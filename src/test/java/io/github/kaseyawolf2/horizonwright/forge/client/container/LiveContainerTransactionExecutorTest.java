package io.github.kaseyawolf2.horizonwright.forge.client.container;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.client.C0EPacketClickWindow;
import net.minecraft.network.play.server.S2FPacketSetSlot;
import net.minecraft.network.play.server.S30PacketWindowItems;
import net.minecraft.network.play.server.S32PacketConfirmTransaction;

import org.junit.Test;

import io.github.kaseyawolf2.horizonwright.core.container.ContainerSnapshot;
import io.github.kaseyawolf2.horizonwright.core.container.ContainerTransaction;
import io.github.kaseyawolf2.horizonwright.core.container.ContainerTransactionState;
import io.github.kaseyawolf2.horizonwright.core.container.ItemFingerprint;
import io.github.kaseyawolf2.horizonwright.core.container.VerifiedContainerClick;
import io.github.kaseyawolf2.horizonwright.forge.client.network.ContainerTransactionPacketBridge;
import io.github.kaseyawolf2.horizonwright.forge.client.network.ContainerTransactionPacketCoordinator;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;

public class LiveContainerTransactionExecutorTest {

    private static final ItemFingerprint ORE = new ItemFingerprint("gregtech:ore", 4, "ore-data", 16);
    private static final ItemFingerprint DUST = new ItemFingerprint("gregtech:dust", 2, "dust-data", 8);

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
            ContainerTransactionPacketCoordinator packets = new ContainerTransactionPacketCoordinator(clock);
            EmbeddedChannel channel = new EmbeddedChannel(new ChannelInboundHandlerAdapter());
            bridge = packets.open(new NetworkManager(true), channel);
            client = new FakeClient(initial, bridge);
            executor = new LiveContainerTransactionExecutor(client, () -> 41L, packets, clock, 50L);
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
