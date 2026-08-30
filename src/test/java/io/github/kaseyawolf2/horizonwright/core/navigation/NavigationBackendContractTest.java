package io.github.kaseyawolf2.horizonwright.core.navigation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.EnumSet;

import org.junit.Test;

import io.github.kaseyawolf2.horizonwright.core.action.ActionCapability;
import io.github.kaseyawolf2.horizonwright.core.action.ActionLease;
import io.github.kaseyawolf2.horizonwright.core.action.InMemoryActionBroker;
import io.github.kaseyawolf2.horizonwright.testfixtures.FakeNavigationBackend;

public class NavigationBackendContractTest {

    @Test
    public void requestCanProgressAndCompleteThroughTheTypedBackend() {
        InMemoryActionBroker broker = new InMemoryActionBroker();
        ActionLease lease = broker
            .tryAcquire("navigation", EnumSet.of(ActionCapability.MOVEMENT, ActionCapability.LOOK))
            .get();
        FakeNavigationBackend backend = new FakeNavigationBackend();
        NavigationRequest request = new NavigationRequest("go-home", lease.getEpoch(), 0, 12, 70, -24, 1);

        NavigationHandle handle = backend.submit(request, lease);
        assertEquals(
            NavigationState.SUBMITTED,
            handle.progress()
                .getState());

        backend.startMoving();
        assertEquals(
            NavigationState.MOVING,
            handle.progress()
                .getState());

        backend.complete();
        assertEquals(
            NavigationState.COMPLETED,
            handle.progress()
                .getState());
    }

    @Test
    public void epochRevocationCancelsBackendWork() {
        InMemoryActionBroker broker = new InMemoryActionBroker();
        ActionLease lease = broker.tryAcquire("navigation", EnumSet.of(ActionCapability.MOVEMENT))
            .get();
        FakeNavigationBackend backend = new FakeNavigationBackend();
        broker.addRevocationListener(backend);
        NavigationHandle handle = backend
            .submit(new NavigationRequest("stale-request", lease.getEpoch(), 0, 1, 64, 1, 0), lease);

        broker.revokeAll();

        assertFalse(lease.isValid());
        assertFalse(backend.isInputHeld());
        assertEquals(
            NavigationState.CANCELLED,
            handle.progress()
                .getState());
    }

    @Test
    public void explicitCancellationIsIdempotentAndReleasesInputs() {
        InMemoryActionBroker broker = new InMemoryActionBroker();
        ActionLease lease = broker.tryAcquire("navigation", EnumSet.of(ActionCapability.MOVEMENT))
            .get();
        FakeNavigationBackend backend = new FakeNavigationBackend();
        NavigationHandle handle = backend
            .submit(new NavigationRequest("explicit-cancel", lease.getEpoch(), 0, 2, 64, 2, 0), lease);

        assertTrue(backend.isInputHeld());
        handle.cancel();
        handle.cancel();

        assertFalse(backend.isInputHeld());
        assertEquals(
            NavigationState.CANCELLED,
            handle.progress()
                .getState());
    }

    @Test
    public void backendLossFailsTheActiveRequest() {
        InMemoryActionBroker broker = new InMemoryActionBroker();
        ActionLease lease = broker.tryAcquire("navigation", EnumSet.of(ActionCapability.MOVEMENT))
            .get();
        FakeNavigationBackend backend = new FakeNavigationBackend();
        NavigationHandle handle = backend
            .submit(new NavigationRequest("backend-loss", lease.getEpoch(), 0, 1, 64, 1, 0), lease);

        backend.setUnavailable("Backend disconnected");

        assertEquals(
            NavigationState.FAILED,
            handle.progress()
                .getState());
        assertFalse(
            backend.availability()
                .isAvailable());
    }
}
