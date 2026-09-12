package io.github.kaseyawolf2.horizonwright.forge.client.container;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;

import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.client.C0EPacketClickWindow;
import net.minecraft.network.play.server.S2FPacketSetSlot;
import net.minecraft.network.play.server.S30PacketWindowItems;
import net.minecraft.network.play.server.S32PacketConfirmTransaction;

import org.junit.Test;

import io.github.kaseyawolf2.horizonwright.core.action.ActionCapability;
import io.github.kaseyawolf2.horizonwright.core.action.ActionLease;
import io.github.kaseyawolf2.horizonwright.core.action.ActionSessionGuard;
import io.github.kaseyawolf2.horizonwright.core.action.InMemoryActionBroker;
import io.github.kaseyawolf2.horizonwright.core.container.ContainerActionPacing;
import io.github.kaseyawolf2.horizonwright.core.container.ContainerSnapshot;
import io.github.kaseyawolf2.horizonwright.core.container.ContainerTransaction;
import io.github.kaseyawolf2.horizonwright.core.container.ContainerTransactionState;
import io.github.kaseyawolf2.horizonwright.core.container.ItemFingerprint;
import io.github.kaseyawolf2.horizonwright.core.container.VerifiedContainerClick;
import io.github.kaseyawolf2.horizonwright.forge.client.network.ContainerTransactionPacketBridge;
import io.github.kaseyawolf2.horizonwright.forge.client.network.ContainerTransactionPacketCoordinator;
import io.github.kaseyawolf2.horizonwright.runtime.task.UnloadActionState;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;

public class LiveContainerTransactionExecutorTest {

    @Test
    public void unloadStartsGuardedSessionAndConfirmsThroughLiveExecutor() {
        ActionSessionGuard guard = new ActionSessionGuard();
        guard.markFirewallInstalled();
        ActionLease lease = new InMemoryActionBroker()
            .tryAcquire("unload", Collections.singleton(ActionCapability.CONTAINER))
            .get();
        ContainerSnapshot before = snapshot(10L, ORE, null);
        ContainerSnapshot after = snapshot(11L, null, ORE);
        Harness harness = new Harness(before, new ContainerActionPacing(0), () -> {}, guard::activeEpochOrZero);
        ContainerTransaction transaction = new ContainerTransaction(
            "guarded",
            lease.getEpoch(),
            Arrays.asList(click("ore", 0, before, after)));
        LiveVanillaChestUnloadBackend.Handle handle = new LiveVanillaChestUnloadBackend.Handle(
            "request",
            transaction,
            harness.executor,
            guard,
            lease);
        assertEquals(0L, guard.activeEpochOrZero());
        assertEquals(
            UnloadActionState.EXECUTING,
            handle.progress()
                .getState());
        assertTrue(guard.isActiveLease(lease));
        assertEquals(1, harness.client.clickCount);
        // Accepted packets alone are insufficient: the synchronized inventory must match too.
        harness.bridge.beforeConfirmationRead(new S32PacketConfirmTransaction(7, (short) 1, true));
        assertEquals(
            UnloadActionState.EXECUTING,
            handle.progress()
                .getState());
        harness.client.observed = after;
        harness.executor.tick();
        assertEquals(
            UnloadActionState.CONFIRMED,
            handle.progress()
                .getState());
        assertFalse(guard.isActiveLease(lease));
        assertTrue(guard.completeDrain(guard.drainGenerationOrZero()));
        assertTrue(guard.isReadyForSession());
        lease.close();
    }

    @Test
    public void unloadWaitsForOpeningSessionDrainAndCancellationNeverClicks() {
        ActionSessionGuard guard = new ActionSessionGuard();
        guard.markFirewallInstalled();
        InMemoryActionBroker broker = new InMemoryActionBroker();
        ActionLease opening = broker.tryAcquire("open", Collections.singleton(ActionCapability.USE))
            .get();
        guard.begin(opening);
        guard.end(opening);
        opening.close();
        ActionLease lease = broker.tryAcquire("unload", Collections.singleton(ActionCapability.CONTAINER))
            .get();
        ContainerSnapshot before = snapshot(10L, ORE, null);
        ContainerSnapshot after = snapshot(11L, null, ORE);
        Harness harness = new Harness(before, new ContainerActionPacing(0), () -> {}, guard::activeEpochOrZero);
        ContainerTransaction transaction = new ContainerTransaction(
            "waiting",
            lease.getEpoch(),
            Arrays.asList(click("ore", 0, before, after)));
        LiveVanillaChestUnloadBackend.Handle handle = new LiveVanillaChestUnloadBackend.Handle(
            "request",
            transaction,
            harness.executor,
            guard,
            lease);
        assertEquals(
            UnloadActionState.EXECUTING,
            handle.progress()
                .getState());
        assertEquals(0, harness.client.clickCount);
        handle.cancel();
        assertTrue(guard.completeDrain(guard.drainGenerationOrZero()));
        assertEquals(
            UnloadActionState.FAILED,
            handle.progress()
                .getState());
        assertEquals(0, harness.client.clickCount);
        lease.close();
    }

    @Test
    public void unloadStartFailureReleasesItsGuardSession() {
        ActionSessionGuard guard = new ActionSessionGuard();
        guard.markFirewallInstalled();
        ActionLease lease = new InMemoryActionBroker()
            .tryAcquire("unload", Collections.singleton(ActionCapability.CONTAINER))
            .get();
        ContainerSnapshot before = snapshot(10L, ORE, null);
        ContainerSnapshot after = snapshot(11L, null, ORE);
        Harness harness = new Harness(
            before,
            new ContainerActionPacing(0),
            () -> { throw new IllegalStateException("closed"); },
            guard::activeEpochOrZero);
        ContainerTransaction transaction = new ContainerTransaction(
            "failure",
            lease.getEpoch(),
            Arrays.asList(click("ore", 0, before, after)));
        LiveVanillaChestUnloadBackend.Handle handle = new LiveVanillaChestUnloadBackend.Handle(
            "request",
            transaction,
            harness.executor,
            guard,
            lease);
        assertEquals(
            UnloadActionState.FAILED,
            handle.progress()
                .getState());
        assertEquals(0, harness.client.clickCount);
        assertFalse(harness.executor.isActive());
        assertTrue(guard.completeDrain(guard.drainGenerationOrZero()));
        lease.close();
    }

    private static final ItemFingerprint ORE = new ItemFingerprint("gregtech:ore", 4, "ore-data", 16);
    private static final ItemFingerprint DUST = new ItemFingerprint("gregtech:dust", 2, "dust-data", 8);

    @Test
    public void closedInventoryScreenPreventsTheFirstClick() {
        ContainerSnapshot before = snapshot(10L, ORE, null);
        ContainerSnapshot after = snapshot(11L, null, ORE);
        Harness harness = new Harness(
            before,
            new ContainerActionPacing(5),
            () -> { throw new IllegalStateException("player inventory screen is closed"); });
        ContainerTransaction transaction = transaction(before, after);
        try {
            harness.executor.begin(transaction);
            org.junit.Assert.fail("closed inventory must reject the click");
        } catch (IllegalStateException expected) {
            assertTrue(
                expected.getMessage()
                    .contains("screen is closed"));
        }
        assertEquals(0, harness.client.clickCount);
        assertEquals(ContainerTransactionState.ABORTED, transaction.getState());
        assertFalse(harness.executor.isActive());
    }

    @Test
    public void closingInventoryDuringPacingCancelsTheNextClickEvenAfterConfirmation() {
        ContainerSnapshot before = snapshot(10L, ORE, DUST, null, null);
        ContainerSnapshot middle = snapshot(11L, null, DUST, ORE, null);
        ContainerSnapshot after = snapshot(12L, null, null, ORE, DUST);
        boolean[] screenOpen = { true };
        Harness harness = new Harness(
            before,
            new ContainerActionPacing(5),
            () -> { if (!screenOpen[0]) throw new IllegalStateException("inventory screen changed"); });
        ContainerTransaction transaction = new ContainerTransaction(
            "screen-bound",
            41L,
            Arrays.asList(click("pick", 0, before, middle), click("place", 1, middle, after)));
        harness.executor.begin(transaction);
        harness.bridge.beforeConfirmationRead(new S32PacketConfirmTransaction(7, (short) 1, true));
        harness.client.observed = middle;
        screenOpen[0] = false;
        for (int tick = 1; tick < 5; tick++) harness.executor.tick();
        try {
            harness.executor.tick();
            org.junit.Assert.fail("closed inventory must prevent the second click");
        } catch (IllegalStateException expected) {
            assertTrue(
                expected.getMessage()
                    .contains("screen changed"));
        }
        assertEquals(1, harness.client.clickCount);
        assertEquals(ContainerTransactionState.ABORTED, transaction.getState());
        assertFalse(harness.executor.isActive());
    }

    @Test
    public void fastConfirmationStillWaitsFiveTicksBetweenClicks() {
        ContainerSnapshot before = snapshot(10L, ORE, DUST, null, null);
        ContainerSnapshot middle = snapshot(11L, null, DUST, ORE, null);
        ContainerSnapshot after = snapshot(12L, null, null, ORE, DUST);
        ContainerTransaction transaction = new ContainerTransaction(
            "paced",
            41L,
            Arrays.asList(click("ore", 0, before, middle), click("dust", 1, middle, after)));
        Harness harness = new Harness(before, new ContainerActionPacing(5));
        harness.executor.begin(transaction);
        harness.bridge.beforeConfirmationRead(new S32PacketConfirmTransaction(7, (short) 1, true));
        harness.client.observed = middle;
        for (int tick = 1; tick < 5; tick++) {
            harness.executor.tick();
            assertEquals(1, harness.client.clickCount);
        }
        harness.executor.tick();
        assertEquals(2, harness.client.clickCount);
    }

    @Test
    public void elapsedDelayDoesNotReplaceServerConfirmation() {
        ContainerSnapshot before = snapshot(10L, ORE, DUST, null, null);
        ContainerSnapshot middle = snapshot(11L, null, DUST, ORE, null);
        ContainerSnapshot after = snapshot(12L, null, null, ORE, DUST);
        ContainerTransaction transaction = new ContainerTransaction(
            "paced",
            41L,
            Arrays.asList(click("ore", 0, before, middle), click("dust", 1, middle, after)));
        Harness harness = new Harness(before, new ContainerActionPacing(5));
        harness.executor.begin(transaction);
        harness.client.observed = middle;
        for (int tick = 0; tick < 8; tick++) harness.executor.tick();
        assertEquals(1, harness.client.clickCount);
        harness.bridge.beforeConfirmationRead(new S32PacketConfirmTransaction(7, (short) 1, true));
        harness.executor.tick();
        assertEquals(2, harness.client.clickCount);
    }

    @Test
    public void precedingBagActionDelaysFirstClickAndCancellationStopsIt() {
        ContainerSnapshot before = snapshot(10L, ORE, null);
        ContainerSnapshot after = snapshot(11L, null, ORE);
        ContainerActionPacing pacing = new ContainerActionPacing(5);
        Harness harness = new Harness(before, pacing);
        pacing.actionPerformed(); // A bag was just selected, opened or inspected.
        harness.executor.begin(transaction(before, after));
        assertEquals(0, harness.client.clickCount);
        for (int tick = 1; tick < 5; tick++) {
            harness.executor.tick();
            assertEquals(0, harness.client.clickCount);
        }
        harness.executor.tick();
        assertEquals(1, harness.client.clickCount);

        harness.executor.cancel("stop testing");
        ContainerTransaction cancelled = transaction(before, after);
        harness.executor.begin(cancelled);
        harness.executor.cancel("user paused during pacing");
        for (int tick = 0; tick < 5; tick++) harness.executor.tick();
        assertEquals(1, harness.client.clickCount);
        assertEquals(ContainerTransactionState.ABORTED, cancelled.getState());
    }

    @Test
    public void dispatchesNextClickOnlyAfterAcceptedResponseAndExactSnapshot() {
        ContainerSnapshot before = snapshot(10L, ORE, DUST, null, null);
        ContainerSnapshot middle = snapshot(11L, null, DUST, ORE, null);
        ContainerSnapshot after = snapshot(12L, null, null, ORE, DUST);
        ContainerTransaction transaction = new ContainerTransaction(
            "unload",
            41L,
            Arrays.asList(click("ore", 0, before, middle), click("dust", 1, middle, after)));
        Harness harness = new Harness(before);

        harness.executor.begin(transaction);
        assertEquals(1, harness.client.clickCount);

        harness.executor.tick();
        assertEquals(1, harness.client.clickCount);
        harness.bridge.beforeConfirmationRead(new S32PacketConfirmTransaction(7, (short) 1, true));
        harness.executor.tick();
        assertEquals(1, harness.client.clickCount);

        harness.client.observed = middle;
        harness.executor.tick();
        assertEquals(2, harness.client.clickCount);
        harness.bridge.beforeConfirmationRead(new S32PacketConfirmTransaction(7, (short) 2, true));
        harness.client.observed = after;
        harness.executor.tick();

        assertEquals(ContainerTransactionState.COMPLETED, transaction.getState());
        assertFalse(harness.executor.isActive());
    }

    @Test
    public void rejectedAcknowledgementAdvancesOnlyAfterExactAuthoritativeResync() {
        ContainerSnapshot before = snapshot(10L, ORE, null);
        ContainerSnapshot after = snapshot(11L, null, ORE);

        ContainerTransaction rejected = transaction(before, after);
        Harness rejectedHarness = new Harness(before);
        rejectedHarness.executor.begin(rejected);
        rejectedHarness.bridge.beforeConfirmationRead(new S32PacketConfirmTransaction(7, (short) 1, false));
        rejectedHarness.client.observed = after;
        rejectedHarness.executor.tick();
        assertEquals(ContainerTransactionState.AWAITING_CONFIRMATION, rejected.getState());
        rejectedHarness.bridge.beforeWindowItemsRead(
            new S30PacketWindowItems(7, java.util.Collections.<net.minecraft.item.ItemStack>emptyList()));
        rejectedHarness.bridge.beforeSetSlotRead(new S2FPacketSetSlot(-1, -1, null));
        rejectedHarness.executor.tick();
        assertEquals(1, rejectedHarness.client.clickCount);
        assertEquals(ContainerTransactionState.COMPLETED, rejected.getState());
    }

    @Test
    public void rejectionAndTimeoutNeverRedispatchTheUncertainClick() {
        ContainerSnapshot before = snapshot(10L, ORE, null);
        ContainerSnapshot after = snapshot(11L, null, ORE);

        ContainerTransaction rejected = transaction(before, after);
        Harness rejectedHarness = new Harness(before);
        rejectedHarness.executor.begin(rejected);
        rejectedHarness.bridge.beforeConfirmationRead(new S32PacketConfirmTransaction(7, (short) 1, false));
        rejectedHarness.bridge.beforeWindowItemsRead(
            new S30PacketWindowItems(7, java.util.Collections.<net.minecraft.item.ItemStack>emptyList()));
        rejectedHarness.bridge.beforeSetSlotRead(new S2FPacketSetSlot(-1, -1, null));
        rejectedHarness.clock.now = 151L;
        rejectedHarness.executor.tick();
        rejectedHarness.executor.tick();
        assertEquals(1, rejectedHarness.client.clickCount);
        assertEquals(ContainerTransactionState.ABORTED, rejected.getState());

        ContainerTransaction timedOut = transaction(before, after);
        Harness timeoutHarness = new Harness(before);
        timeoutHarness.executor.begin(timedOut);
        timeoutHarness.clock.now = 151L;
        timeoutHarness.executor.tick();
        timeoutHarness.executor.tick();
        assertEquals(1, timeoutHarness.client.clickCount);
        assertEquals(ContainerTransactionState.ABORTED, timedOut.getState());
        assertTrue(
            timedOut.getAbortReason()
                .contains("not be resent"));
    }

    @Test
    public void staleOwnerCannotCancelAnotherTransaction() {
        ContainerSnapshot before = snapshot(10L, ORE, null);
        ContainerSnapshot after = snapshot(11L, null, ORE);
        ContainerTransaction active = transaction(before, after);
        ContainerTransaction stale = transaction(before, after);
        Harness harness = new Harness(before);

        harness.executor.begin(active);
        assertFalse(harness.executor.cancel(stale, "stale task retirement"));
        assertEquals(ContainerTransactionState.AWAITING_CONFIRMATION, active.getState());
        assertTrue(harness.executor.cancel(active, "exact owner retirement"));
        assertEquals(ContainerTransactionState.ABORTED, active.getState());
        assertFalse(harness.executor.isActive());
    }

    @Test
    public void drainingChestRecoversFromServerResyncThenContinuesWithFreshTransfer() {
        ContainerSnapshot before = snapshot(10L, null, null, ORE, DUST);
        ContainerSnapshot predicted = snapshot(11L, ORE, null, null, DUST);
        VerifiedContainerClick first = click("ore", 2, before, predicted).allowingStorageExtraction(2);
        ContainerTransaction transaction = new ContainerTransaction("first", 41L, Arrays.asList(first));
        Harness harness = new Harness(before);
        harness.executor.begin(transaction);
        harness.bridge.beforeConfirmationRead(new S32PacketConfirmTransaction(7, (short) 1, false));
        harness.client.observed = snapshot(11L, null, null, null, DUST);
        harness.executor.tick();
        assertEquals(ContainerTransactionState.AWAITING_CONFIRMATION, transaction.getState());
        harness.bridge.beforeWindowItemsRead(
            new S30PacketWindowItems(7, java.util.Collections.<net.minecraft.item.ItemStack>emptyList()));
        harness.executor.tick();
        assertEquals(ContainerTransactionState.AWAITING_CONFIRMATION, transaction.getState());
        harness.bridge.beforeSetSlotRead(new S2FPacketSetSlot(-1, -1, null));
        harness.executor.tick();
        assertEquals(ContainerTransactionState.COMPLETED, transaction.getState());
        assertEquals(1, harness.client.clickCount);
        assertFalse(harness.executor.isActive());

        ContainerSnapshot fresh = harness.client.observed;
        VerifiedContainerClick second = click("dust", 3, fresh, snapshot(12L, DUST, null, null, null))
            .allowingStorageExtraction(2);
        ContainerTransaction remaining = new ContainerTransaction("second", 41L, Arrays.asList(second));
        harness.executor.begin(remaining);
        harness.bridge.beforeConfirmationRead(new S32PacketConfirmTransaction(7, (short) 2, true));
        harness.client.observed = snapshot(12L, null, null, null, null);
        harness.executor.tick();
        assertEquals(ContainerTransactionState.COMPLETED, remaining.getState());
        assertEquals(2, harness.client.clickCount);
    }

    @Test
    public void extractionDoesNotConfirmAnUnmovedSourceOrAnUnacknowledgedClick() {
        ContainerSnapshot before = snapshot(10L, DUST, null, ORE, null);
        VerifiedContainerClick click = click("ore", 2, before, snapshot(11L, DUST, ORE, null, null))
            .allowingStorageExtraction(2);
        ContainerTransaction transaction = new ContainerTransaction("unmoved", 41L, Arrays.asList(click));
        Harness harness = new Harness(before);
        harness.executor.begin(transaction);
        harness.client.observed = snapshot(11L, null, null, null, null);
        harness.executor.tick();
        assertEquals(ContainerTransactionState.AWAITING_CONFIRMATION, transaction.getState());
        harness.bridge.beforeConfirmationRead(new S32PacketConfirmTransaction(7, (short) 1, false));
        harness.bridge.beforeWindowItemsRead(
            new S30PacketWindowItems(7, java.util.Collections.<net.minecraft.item.ItemStack>emptyList()));
        harness.bridge.beforeSetSlotRead(new S2FPacketSetSlot(-1, -1, null));
        harness.client.observed = snapshot(11L, null, null, ORE, null);
        harness.executor.tick();
        assertEquals(ContainerTransactionState.AWAITING_CONFIRMATION, transaction.getState());
        harness.clock.now = 151L;
        harness.executor.tick();
        assertEquals(ContainerTransactionState.ABORTED, transaction.getState());
        assertEquals(1, harness.client.clickCount);
    }

    @Test
    public void acceptedUnloadWithInkPickupCompletesAndNewCargoCanBeUnloaded() {
        ItemFingerprint ink = new ItemFingerprint("minecraft:dye", 0, "none", 1);
        // Same 108-slot chest and window slots as the 10:31:13 stall:
        // slot 138 was emptied by the previous transfer; slot 141 is now unloading.
        java.util.List<ItemFingerprint> slots = new java.util.ArrayList<>(java.util.Collections.nCopies(144, null));
        slots.set(0, ORE);
        slots.set(141, DUST);
        ContainerSnapshot before = new ContainerSnapshot(7, "ironchest:diamond", "108+36", 0, slots, null);
        slots.set(1, DUST);
        slots.set(141, null);
        ContainerSnapshot predicted = new ContainerSnapshot(7, "ironchest:diamond", "108+36", 1, slots, null);
        ContainerTransaction transaction = new ContainerTransaction(
            "dirt-transfer",
            41L,
            Arrays.asList(click("dirt", 141, before, predicted).allowingStorageExtraction(108)));
        Harness harness = new Harness(before);
        harness.executor.begin(transaction);
        harness.bridge.beforeConfirmationRead(new S32PacketConfirmTransaction(7, (short) 1, true));
        slots.set(0, null);
        slots.set(1, null);
        slots.set(138, ink);
        ContainerSnapshot withPickup = new ContainerSnapshot(7, "ironchest:diamond", "108+36", 1, slots, null);
        harness.client.observed = withPickup;
        harness.executor.tick();
        assertEquals(ContainerTransactionState.COMPLETED, transaction.getState());
        assertEquals(1, harness.client.clickCount);

        slots.set(138, null);
        slots.set(0, ink);
        ContainerSnapshot inkAfter = new ContainerSnapshot(7, "ironchest:diamond", "108+36", 2, slots, null);
        ContainerTransaction inkTransfer = new ContainerTransaction(
            "ink-transfer",
            41L,
            Arrays.asList(click("ink", 138, withPickup, inkAfter).allowingStorageExtraction(108)));
        harness.executor.begin(inkTransfer);
        harness.bridge.beforeConfirmationRead(new S32PacketConfirmTransaction(7, (short) 2, false));
        harness.bridge.beforeWindowItemsRead(
            new S30PacketWindowItems(7, java.util.Collections.<net.minecraft.item.ItemStack>emptyList()));
        harness.bridge.beforeSetSlotRead(new S2FPacketSetSlot(-1, -1, null));
        slots.set(0, null);
        harness.client.observed = new ContainerSnapshot(7, "ironchest:diamond", "108+36", 2, slots, null);
        harness.executor.tick();
        assertEquals(ContainerTransactionState.COMPLETED, inkTransfer.getState());
        assertEquals(2, harness.client.clickCount);
    }

    private static ContainerTransaction transaction(ContainerSnapshot before, ContainerSnapshot after) {
        return new ContainerTransaction("unload", 41L, Arrays.asList(click("ore", 0, before, after)));
    }

    private static VerifiedContainerClick click(String id, int slot, ContainerSnapshot before,
        ContainerSnapshot after) {
        return new VerifiedContainerClick(id, slot, 0, 1, before, after);
    }

    private static ContainerSnapshot snapshot(long revision, ItemFingerprint... slots) {
        return new ContainerSnapshot(7, "minecraft:chest", "chest+player", revision, Arrays.asList(slots), null);
    }

    private static final class Harness {

        private final MutableClock clock = new MutableClock();
        private final FakeClient client;
        private final ContainerTransactionPacketBridge bridge;
        private final LiveContainerTransactionExecutor executor;

        private Harness(ContainerSnapshot initial) {
            this(initial, new ContainerActionPacing(0));
        }

        private Harness(ContainerSnapshot initial, ContainerActionPacing pacing) {
            this(initial, pacing, () -> {});
        }

        private Harness(ContainerSnapshot initial, ContainerActionPacing pacing, Runnable requireInteractionScreen) {
            this(initial, pacing, requireInteractionScreen, () -> 41L);
        }

        private Harness(ContainerSnapshot initial, ContainerActionPacing pacing, Runnable requireInteractionScreen,
            LiveContainerTransactionExecutor.EpochSource epochs) {
            ContainerTransactionPacketCoordinator packets = new ContainerTransactionPacketCoordinator(clock);
            EmbeddedChannel channel = new EmbeddedChannel(new ChannelInboundHandlerAdapter());
            bridge = packets.open(new NetworkManager(true), channel);
            client = new FakeClient(initial, bridge);
            executor = new LiveContainerTransactionExecutor(
                client,
                epochs,
                packets,
                clock,
                50L,
                pacing,
                requireInteractionScreen);
        }
    }

    private static final class MutableClock
        implements LiveContainerTransactionExecutor.NanoClock, ContainerTransactionPacketCoordinator.NanoClock {

        private long now = 100L;

        @Override
        public long nanoTime() {
            return now;
        }
    }

    private static final class FakeClient implements LiveContainerTransactionExecutor.ClientAccess {

        private final ContainerTransactionPacketBridge bridge;
        private ContainerSnapshot observed;
        private int clickCount;

        private FakeClient(ContainerSnapshot observed, ContainerTransactionPacketBridge bridge) {
            this.observed = observed;
            this.bridge = bridge;
        }

        @Override
        public void requireClientThread() {}

        @Override
        public ContainerSnapshot capture(long revision) {
            return new ContainerSnapshot(
                observed.getWindowId(),
                observed.getContainerType(),
                observed.getSlotLayout(),
                revision,
                observed.getSlots(),
                observed.getCursor());
        }

        @Override
        public void click(VerifiedContainerClick click) {
            clickCount++;
            bridge.beforeClickWrite(
                new C0EPacketClickWindow(
                    click.getExpectedBefore()
                        .getWindowId(),
                    click.getSlot(),
                    click.getMouseButton(),
                    click.getClickMode(),
                    null,
                    (short) clickCount));
        }
    }
}
