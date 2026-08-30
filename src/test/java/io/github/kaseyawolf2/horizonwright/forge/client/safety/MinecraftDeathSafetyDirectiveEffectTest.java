package io.github.kaseyawolf2.horizonwright.forge.client.safety;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

import io.github.kaseyawolf2.horizonwright.core.safety.death.DeathContext;
import io.github.kaseyawolf2.horizonwright.core.safety.death.DeathSafetyController;
import io.github.kaseyawolf2.horizonwright.core.safety.death.DeathSafetyDirective;
import io.github.kaseyawolf2.horizonwright.core.safety.death.DeathSafetySnapshot;
import io.github.kaseyawolf2.horizonwright.core.safety.death.DeathSafetyUpdate;
import io.github.kaseyawolf2.horizonwright.core.safety.death.DeathSignal;
import io.github.kaseyawolf2.horizonwright.core.safety.death.DimensionBlockPosition;
import io.github.kaseyawolf2.horizonwright.core.safety.death.InventoryManifest;
import io.github.kaseyawolf2.horizonwright.core.safety.death.ManualHoldReason;
import io.github.kaseyawolf2.horizonwright.core.safety.death.RecoveryNavigationRequest;
import io.github.kaseyawolf2.horizonwright.core.safety.death.RespawnObservation;
import io.github.kaseyawolf2.horizonwright.core.safety.death.SafetyEventStamp;

public class MinecraftDeathSafetyDirectiveEffectTest {

    @Test
    public void firstLatchExecutesEveryConcreteOwnerAndKeepsGlobalReleaseDeathOnly() {
        List<String> order = new ArrayList<>();
        RecordingControls controls = new RecordingControls(order);
        MinecraftDeathSafetyDirectiveEffect effect = controls.effect();
        DeathSafetyDirectiveProcessor processor = new DeathSafetyDirectiveProcessor(new DeathSafetyDurableState() {

            @Override
            public void persistUnresolvedDeath(DeathSafetySnapshot snapshot) {
                order.add("persist");
            }

            @Override
            public void clearResolvedDeath() {
                order.add("clear");
            }
        });

        processor.process(firstLatchUpdate(), effect);

        assertEquals(
            Arrays.asList(
                "persist",
                "task-checkpoint",
                "task-cancel-pending",
                "action-revoke",
                "minecraft-global-input",
                "navigation-private-input",
                "minecraft-held-use",
                "action-epoch",
                "container-epoch",
                "action-death-lock"),
            order);
        assertEquals(1, controls.globalInputClears);
        assertEquals(1, controls.heldUseReleases);
    }

    @Test
    public void criticalRestrictionsOnlyReachAutomationAuthority() {
        List<String> order = new ArrayList<>();
        RecordingControls controls = new RecordingControls(order);
        MinecraftDeathSafetyDirectiveEffect effect = controls.effect();
        DeathSafetyController controller = controller(8L);

        DeathSafetyUpdate critical = controller
            .onHealthObservation(new SafetyEventStamp(8L, 1L, 1L), 8.0D, 20.0D, null);
        effect.apply(DeathSafetyDirective.ENTER_CRITICAL_RESTRICTIONS, critical.getSnapshot());
        DeathSafetyUpdate recovered = controller
            .onHealthObservation(new SafetyEventStamp(8L, 2L, 2L), 20.0D, 20.0D, null);
        effect.apply(DeathSafetyDirective.RELEASE_CRITICAL_RESTRICTIONS, recovered.getSnapshot());

        assertEquals(Arrays.asList("action-critical-enter", "action-critical-release"), order);
        assertEquals(0, controls.globalInputClears);
        assertEquals(0, controls.heldUseReleases);
    }

    @Test
    public void actionAndContainerEpochInvalidationBothRunWhenOneOwnerFails() {
        List<String> order = new ArrayList<>();
        RecordingControls controls = new RecordingControls(order);
        controls.failActionEpoch = true;

        try {
            controls.effect()
                .apply(DeathSafetyDirective.INVALIDATE_ACTION_AND_CONTAINER_EPOCHS, firstLatchSnapshot());
            fail("expected action epoch failure");
        } catch (IllegalStateException expected) {
            assertEquals("action epoch failed", expected.getMessage());
        }

        assertEquals(Arrays.asList("action-epoch", "container-epoch"), order);
    }

    @Test
    public void recoveryAndManualHoldRequireAndForwardExactKernelEvidence() {
        List<String> order = new ArrayList<>();
        RecordingControls controls = new RecordingControls(order);
        MinecraftDeathSafetyDirectiveEffect effect = controls.effect();
        DeathSafetyController recoveryController = controller(11L);
        long deathEpoch = recoveryController
            .onDeathSignal(new SafetyEventStamp(11L, 1L, 1L), DeathSignal.LOCAL_DEATH_CALLBACK, deathContext())
            .getSnapshot()
            .getDeathEpoch();
        DeathSafetySnapshot recovery = recoveryController
            .onRespawnObservation(
                new SafetyEventStamp(11L, 2L, 2L),
                deathEpoch,
                new RespawnObservation(
                    "new-player",
                    20.0D,
                    false,
                    true,
                    true,
                    new DimensionBlockPosition(0, 0, 64, 0),
                    InventoryManifest.empty(36)))
            .getSnapshot();

        effect.apply(DeathSafetyDirective.START_INTERACTION_DISABLED_RECOVERY_NAVIGATION, recovery);
        assertSame(
            recovery.getRecoveryNavigationRequest()
                .get(),
            controls.recoveryRequest);
        assertFalse(controls.recoveryRequest.areGenericInteractionsAllowed());

        DeathSafetySnapshot manual = controller(12L).onLethalHealthWithoutContext(new SafetyEventStamp(12L, 1L, 1L))
            .getSnapshot();
        effect.apply(DeathSafetyDirective.ENTER_MANUAL_HOLD, manual);

        assertEquals(ManualHoldReason.PRE_DEATH_CONTEXT_UNAVAILABLE, controls.manualHoldReason);
        assertEquals(Arrays.asList("recovery-navigation", "manual-hold"), order);
    }

    @Test
    public void packetAndDurabilityDirectivesCannotEscapeTheirExactOwners() {
        MinecraftDeathSafetyDirectiveEffect effect = new RecordingControls(new ArrayList<String>()).effect();
        DeathSafetySnapshot snapshot = firstLatchSnapshot();

        assertRefusedAtBoundary(effect, DeathSafetyDirective.SEND_EXACTLY_ONE_RESPAWN, "packet-write gate");
        assertRefusedAtBoundary(effect, DeathSafetyDirective.AUTHORIZE_EXACT_GRAVE_ACTIVATION, "packet-write gate");
        assertRefusedAtBoundary(effect, DeathSafetyDirective.PERSIST_UNRESOLVED_DEATH, "DirectiveProcessor");
        assertRefusedAtBoundary(effect, DeathSafetyDirective.CLEAR_UNRESOLVED_DEATH, "DirectiveProcessor");
        assertTrue(snapshot.areAllAutomationInputOwnersBlocked());
    }

    private static void assertRefusedAtBoundary(MinecraftDeathSafetyDirectiveEffect effect,
        DeathSafetyDirective directive, String diagnostic) {
        try {
            effect.apply(directive, firstLatchSnapshot());
            fail("expected boundary refusal for " + directive);
        } catch (RuntimeException expected) {
            assertTrue(
                expected.getMessage(),
                expected.getMessage()
                    .contains(diagnostic));
        }
    }

    private static DeathSafetyUpdate firstLatchUpdate() {
        return controller(7L)
            .onDeathSignal(new SafetyEventStamp(7L, 1L, 1L), DeathSignal.LOCAL_DEATH_CALLBACK, deathContext());
    }

    private static DeathSafetySnapshot firstLatchSnapshot() {
        return firstLatchUpdate().getSnapshot();
    }

    private static DeathSafetyController controller(long connectionEpoch) {
        return new DeathSafetyController(
            BrokerBackedDeathSafetyInterlockTest.testPolicy(),
            new PacketWriteGateTest.NoOpInterlock(),
            BrokerBackedDeathSafetyInterlockTest.connection(connectionEpoch, "old-player"));
    }

    private static DeathContext deathContext() {
        return new DeathContext(
            new DimensionBlockPosition(0, 10, 64, 10),
            "old-player",
            "task",
            new InventoryManifest(36, Collections.emptyList()));
    }

    private static final class RecordingControls implements MinecraftDeathSafetyDirectiveEffect.TaskWorkControl,
        MinecraftDeathSafetyDirectiveEffect.ActionAuthorityControl,
        MinecraftDeathSafetyDirectiveEffect.NavigationControl,
        MinecraftDeathSafetyDirectiveEffect.ContainerEpochControl,
        MinecraftDeathSafetyDirectiveEffect.ManualHoldControl,
        MinecraftDeathSafetyDirectiveEffect.MinecraftInputControl {

        private final List<String> order;
        private int globalInputClears;
        private int heldUseReleases;
        private boolean failActionEpoch;
        private RecoveryNavigationRequest recoveryRequest;
        private ManualHoldReason manualHoldReason;

        private RecordingControls(List<String> order) {
            this.order = order;
        }

        private MinecraftDeathSafetyDirectiveEffect effect() {
            return new MinecraftDeathSafetyDirectiveEffect(this, this, this, this, this, this);
        }

        @Override
        public void forceCheckpointActiveTask(DeathSafetySnapshot snapshot) {
            order.add("task-checkpoint");
        }

        @Override
        public void cancelAllNavigationAndPendingWork(DeathSafetySnapshot snapshot) {
            order.add("task-cancel-pending");
        }

        @Override
        public void enterCriticalRestrictions(DeathSafetySnapshot snapshot) {
            order.add("action-critical-enter");
        }

        @Override
        public void releaseCriticalRestrictions(DeathSafetySnapshot snapshot) {
            order.add("action-critical-release");
        }

        @Override
        public void revokeAllActionLeases(DeathSafetySnapshot snapshot) {
            order.add("action-revoke");
        }

        @Override
        public void invalidateActionEpoch(DeathSafetySnapshot snapshot) {
            order.add("action-epoch");
            if (failActionEpoch) {
                throw new IllegalStateException("action epoch failed");
            }
        }

        @Override
        public void engageDeathLockdown(DeathSafetySnapshot snapshot) {
            order.add("action-death-lock");
        }

        @Override
        public void releaseDeathLockdown(DeathSafetySnapshot snapshot) {
            order.add("action-death-release");
        }

        @Override
        public void clearPrivateInput(DeathSafetySnapshot snapshot) {
            order.add("navigation-private-input");
        }

        @Override
        public void startInteractionDisabledRecoveryNavigation(RecoveryNavigationRequest request,
            DeathSafetySnapshot snapshot) {
            recoveryRequest = request;
            order.add("recovery-navigation");
        }

        @Override
        public void invalidateContainerEpoch(DeathSafetySnapshot snapshot) {
            order.add("container-epoch");
        }

        @Override
        public void enterManualHold(ManualHoldReason reason, DeathSafetySnapshot snapshot) {
            manualHoldReason = reason;
            order.add("manual-hold");
        }

        @Override
        public void clearAllInputAndKeybindings() {
            globalInputClears++;
            order.add("minecraft-global-input");
        }

        @Override
        public void releaseAllHeldUse() {
            heldUseReleases++;
            order.add("minecraft-held-use");
        }
    }
}
