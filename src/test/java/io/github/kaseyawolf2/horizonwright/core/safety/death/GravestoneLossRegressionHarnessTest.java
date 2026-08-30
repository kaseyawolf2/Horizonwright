package io.github.kaseyawolf2.horizonwright.core.safety.death;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.junit.Test;

/** Deterministic reproduction of action packets delayed across the lethal-S06 write race. */
public class GravestoneLossRegressionHarnessTest {

    @Test
    public void everyHeldActionIsRevokedAndEveryPreLatchPacketIsRejectedAtTheWriteBoundary() {
        RegressionRuntime runtime = new RegressionRuntime();
        DeathSafetyController controller = new DeathSafetyController(
            DeathSafetyPolicy.planDefaults(6),
            runtime,
            DeathSafetyTestHarness.connection(1L, DeathSafetyTestHarness.OLD_PLAYER));
        runtime.controller = controller;
        runtime.heldOwners.addAll(EnumSet.allOf(ActionOwner.class));
        for (ActionOwner owner : ActionOwner.values()) {
            runtime.outboundQueue.add(new QueuedActionPacket(owner, 1L));
        }

        InventoryManifest preDeath = DeathSafetyTestHarness
            .inventory(36, DeathSafetyTestHarness.stack("minecraft:diamond", 32, 64));
        DeathSafetyUpdate lethalS06 = controller.onHealthObservation(
            new SafetyEventStamp(1L, 1L, 1L),
            0.0D,
            20.0D,
            new DeathContext(
                DeathSafetyTestHarness.DEATH_POSITION,
                DeathSafetyTestHarness.OLD_PLAYER,
                "held-action-task",
                preDeath));

        assertEquals(
            DeathSafetyState.DEATH_LATCHED,
            lethalS06.getSnapshot()
                .getState());
        assertTrue(runtime.heldOwners.isEmpty());
        assertEquals(EnumSet.allOf(EmergencyStopAction.class), runtime.lastLatch.getRequiredActions());
        assertFalse(runtime.tryReacquireTask());
        assertFalse(runtime.tryReacquireBackend());
        assertFalse(runtime.tryReacquireKey());
        assertFalse(runtime.tryReacquireInputOwner());

        runtime.flushOutboundAtActualWriteBoundary();

        assertTrue(runtime.writtenActions.isEmpty());
        assertEquals(Arrays.asList(ActionOwner.values()), runtime.blockedActions);
        assertEquals(32, runtime.graveItemCount);
    }

    private enum ActionOwner {
        HELD_MINING,
        RIGHT_USE,
        EATING,
        COMBAT,
        THAUMCRAFT_SCANNING,
        WAND_DRAINING,
        RESEARCH_PLACEMENT,
        WINDOW_CLICK
    }

    private static final class QueuedActionPacket {

        private final ActionOwner owner;
        private final long actionEpoch;

        private QueuedActionPacket(ActionOwner owner, long actionEpoch) {
            this.owner = owner;
            this.actionEpoch = actionEpoch;
        }
    }

    private static final class RegressionRuntime implements DeathSafetyInterlock {

        private DeathSafetyController controller;
        private final Set<ActionOwner> heldOwners = EnumSet.noneOf(ActionOwner.class);
        private final List<QueuedActionPacket> outboundQueue = new ArrayList<>();
        private final List<ActionOwner> writtenActions = new ArrayList<>();
        private final List<ActionOwner> blockedActions = new ArrayList<>();
        private DeathLatchRecord lastLatch;
        private int graveItemCount = 32;

        @Override
        public void enterCriticalRestrictions() {
            heldOwners.clear();
        }

        @Override
        public void releaseCriticalRestrictions() {}

        @Override
        public void latchDeath(DeathLatchRecord record) {
            lastLatch = record;
            heldOwners.clear();
        }

        @Override
        public void reaffirmDeathLockdown(long deathEpoch) {
            heldOwners.clear();
        }

        @Override
        public void releaseDeathLockdown(long deathEpoch) {}

        private boolean tryReacquireTask() {
            return tryReacquire(ActionOwner.RESEARCH_PLACEMENT);
        }

        private boolean tryReacquireBackend() {
            return tryReacquire(ActionOwner.HELD_MINING);
        }

        private boolean tryReacquireKey() {
            return tryReacquire(ActionOwner.RIGHT_USE);
        }

        private boolean tryReacquireInputOwner() {
            return tryReacquire(ActionOwner.COMBAT);
        }

        private boolean tryReacquire(ActionOwner owner) {
            if (controller.snapshot()
                .areAllAutomationInputOwnersBlocked()) {
                return false;
            }
            heldOwners.add(owner);
            return true;
        }

        private void flushOutboundAtActualWriteBoundary() {
            for (QueuedActionPacket packet : outboundQueue) {
                if (packet.actionEpoch == controller.snapshot()
                    .getDeathEpoch() && controller.snapshot()
                        .areDangerousActionsAllowed()) {
                    writtenActions.add(packet.owner);
                    graveItemCount--;
                } else {
                    blockedActions.add(packet.owner);
                }
            }
            outboundQueue.clear();
        }
    }
}
