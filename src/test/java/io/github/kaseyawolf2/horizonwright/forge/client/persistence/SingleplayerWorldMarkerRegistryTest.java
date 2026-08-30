package io.github.kaseyawolf2.horizonwright.forge.client.persistence;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

public class SingleplayerWorldMarkerRegistryTest {

    private static final String MARKER_ID = "dba3ca5f-e15d-4667-84f6-42d39ce8406f";

    @Test
    public void resolvesOnLoadCallerAndPublishesImmutableSnapshotToAnotherThread() throws Exception {
        SingleplayerWorldMarkerRegistry registry = registry();
        Object world = new Object();
        AtomicReference<Thread> resolutionThread = new AtomicReference<>();
        AtomicReference<SingleplayerWorldMarkerSnapshot> clientObservation = new AtomicReference<>();
        CountDownLatch published = new CountDownLatch(1);

        Thread integratedServer = new Thread(() -> {
            registry.recordWorldLoad(world, () -> {
                resolutionThread.set(Thread.currentThread());
                return loaded("locator-a");
            });
            published.countDown();
        }, "integrated-server-test");
        Thread client = new Thread(() -> {
            try {
                assertTrue(published.await(5L, TimeUnit.SECONDS));
                clientObservation.set(registry.snapshot());
            } catch (InterruptedException interrupted) {
                Thread.currentThread()
                    .interrupt();
            }
        }, "client-test");

        integratedServer.start();
        client.start();
        integratedServer.join(5_000L);
        client.join(5_000L);

        assertSame(integratedServer, resolutionThread.get());
        SingleplayerWorldMarkerSnapshot observed = clientObservation.get();
        assertEquals(SingleplayerWorldMarkerSnapshot.Status.LOADED, observed.getStatus());
        assertEquals(1L, observed.getRevision());
        assertEquals(
            "uuid:" + MARKER_ID,
            observed.getEvidence()
                .get()
                .getWorldFingerprint());
    }

    @Test
    public void duplicateLoadIsInertAndDoesNotResolveAgain() {
        SingleplayerWorldMarkerRegistry registry = registry();
        Object world = new Object();
        AtomicInteger resolutions = new AtomicInteger();

        assertTrue(registry.recordWorldLoad(world, () -> {
            resolutions.incrementAndGet();
            return loaded("locator-a");
        }));
        SingleplayerWorldMarkerSnapshot first = registry.snapshot();
        assertFalse(registry.recordWorldLoad(world, () -> {
            resolutions.incrementAndGet();
            return loaded("locator-a");
        }));

        assertEquals(1, resolutions.get());
        assertSame(first, registry.snapshot());
        assertEquals(
            1L,
            registry.snapshot()
                .getRevision());
    }

    @Test
    public void staleUnloadCannotClearNewerWorldPublication() {
        SingleplayerWorldMarkerRegistry registry = registry();
        Object firstWorld = new Object();
        Object secondWorld = new Object();
        registry.recordWorldLoad(firstWorld, () -> loaded("locator-a"));
        registry.recordWorldLoad(secondWorld, () -> loaded("locator-b"));

        assertFalse(registry.recordWorldUnload(firstWorld));
        assertEquals(
            SingleplayerWorldMarkerSnapshot.Status.LOADED,
            registry.snapshot()
                .getStatus());
        assertEquals(
            "locator-b",
            registry.snapshot()
                .getEvidence()
                .get()
                .getLocatorKey());
        assertEquals(
            2L,
            registry.snapshot()
                .getRevision());

        assertTrue(registry.recordWorldUnload(secondWorld));
        assertEquals(
            SingleplayerWorldMarkerSnapshot.Status.NO_WORLD,
            registry.snapshot()
                .getStatus());
        assertFalse(
            registry.snapshot()
                .getEvidence()
                .isPresent());
        assertEquals(
            3L,
            registry.snapshot()
                .getRevision());
    }

    @Test
    public void resolutionFailureIsContainedAndPublishedWithoutRetryingDuplicateEvent() {
        SingleplayerWorldMarkerRegistry registry = registry();
        Object world = new Object();
        AtomicInteger resolutions = new AtomicInteger();

        assertTrue(registry.recordWorldLoad(world, () -> {
            resolutions.incrementAndGet();
            throw new AssertionError("marker read exploded");
        }));

        assertEquals(
            SingleplayerWorldMarkerSnapshot.Status.UNAVAILABLE,
            registry.snapshot()
                .getStatus());
        assertFalse(
            registry.snapshot()
                .isAvailable());
        assertTrue(
            registry.snapshot()
                .getDiagnostic()
                .contains("marker read exploded"));
        assertFalse(registry.recordWorldLoad(world, () -> loaded("locator-a")));
        assertEquals(1, resolutions.get());
    }

    @Test
    public void corruptResultPublishesNoEvidenceAndDoesNotMutateIt() {
        SingleplayerWorldMarkerRegistry registry = registry();
        Object world = new Object();
        SingleplayerWorldMarkerResult corrupt = SingleplayerWorldMarkerResult
            .unavailable(SingleplayerWorldMarkerResult.Status.CORRUPT, "newer marker was preserved");

        assertTrue(registry.recordWorldLoad(world, () -> corrupt));

        assertEquals(
            SingleplayerWorldMarkerSnapshot.Status.CORRUPT,
            registry.snapshot()
                .getStatus());
        assertFalse(
            registry.snapshot()
                .isAvailable());
        assertEquals(
            "newer marker was preserved",
            registry.snapshot()
                .getDiagnostic());
    }

    private static SingleplayerWorldMarkerRegistry registry() {
        return new SingleplayerWorldMarkerRegistry(new SingleplayerWorldMarkerResolver());
    }

    private static SingleplayerWorldMarkerResult loaded(String locator) {
        return SingleplayerWorldMarkerResult.available(
            SingleplayerWorldMarkerResult.Status.LOADED,
            new SingleplayerWorldBindingEvidence(locator, MARKER_ID),
            "singleplayer world marker loaded");
    }
}
