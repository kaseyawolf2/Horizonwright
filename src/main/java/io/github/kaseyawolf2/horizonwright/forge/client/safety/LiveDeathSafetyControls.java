package io.github.kaseyawolf2.horizonwright.forge.client.safety;

import io.github.kaseyawolf2.horizonwright.HorizonwrightMod;
import io.github.kaseyawolf2.horizonwright.HorizonwrightRuntime;
import io.github.kaseyawolf2.horizonwright.core.safety.death.DeathSafetySnapshot;
import io.github.kaseyawolf2.horizonwright.core.safety.death.ManualHoldReason;
import io.github.kaseyawolf2.horizonwright.core.safety.death.RecoveryNavigationRequest;

/** Conservative production controls for the live death-safety directive effect. */
final class LiveDeathSafetyControls implements MinecraftDeathSafetyDirectiveEffect.TaskWorkControl,
    MinecraftDeathSafetyDirectiveEffect.ActionAuthorityControl, MinecraftDeathSafetyDirectiveEffect.NavigationControl,
    MinecraftDeathSafetyDirectiveEffect.ContainerEpochControl, MinecraftDeathSafetyDirectiveEffect.ManualHoldControl {

    private final HorizonwrightRuntime runtime;
    private boolean unavailableRecoveryRequested;
    private ManualHoldReason manualHoldReason;

    LiveDeathSafetyControls(HorizonwrightRuntime runtime) {
        if (runtime == null) {
            throw new IllegalArgumentException("runtime must not be null");
        }
        this.runtime = runtime;
    }

    MinecraftDeathSafetyDirectiveEffect createEffect() {
        return new MinecraftDeathSafetyDirectiveEffect(this, this, this, this, this);
    }

    @Override
    public void forceCheckpointActiveTask(DeathSafetySnapshot snapshot) {
        // The durable directive is processed first and atomically exports this controller.
        runtime.exportControllerState();
    }

    @Override
    public void cancelAllNavigationAndPendingWork(DeathSafetySnapshot snapshot) {
        runtime.getActionBroker()
            .revokeAll();
    }

    @Override
    public void enterCriticalRestrictions(DeathSafetySnapshot snapshot) {
        runtime.getActionBroker()
            .revokeAll();
    }

    @Override
    public void releaseCriticalRestrictions(DeathSafetySnapshot snapshot) {
        // Work does not reacquire authority implicitly after critical health.
    }

    @Override
    public void revokeAllActionLeases(DeathSafetySnapshot snapshot) {
        runtime.getActionBroker()
            .revokeAll();
    }

    @Override
    public void invalidateActionEpoch(DeathSafetySnapshot snapshot) {
        runtime.getActionBroker()
            .revokeAll();
    }

    @Override
    public void engageDeathLockdown(DeathSafetySnapshot snapshot) {
        runtime.getActionBroker()
            .enterSafetyLockdown();
    }

    @Override
    public void releaseDeathLockdown(DeathSafetySnapshot snapshot) {
        runtime.getActionBroker()
            .leaveSafetyLockdown();
    }

    @Override
    public void clearPrivateInput(DeathSafetySnapshot snapshot) {
        // Backend revocation is synchronous at the broker and its client-thread cleanup keeps ticking.
        runtime.getActionBroker()
            .revokeAll();
    }

    @Override
    public synchronized void startInteractionDisabledRecoveryNavigation(RecoveryNavigationRequest request,
        DeathSafetySnapshot snapshot) {
        unavailableRecoveryRequested = true;
    }

    @Override
    public void invalidateContainerEpoch(DeathSafetySnapshot snapshot) {
        runtime.getActionBroker()
            .revokeAll();
    }

    @Override
    public synchronized void enterManualHold(ManualHoldReason reason, DeathSafetySnapshot snapshot) {
        manualHoldReason = reason;
        HorizonwrightMod.LOG.error("Death recovery entered manual hold: {}", reason);
    }

    synchronized boolean consumeUnavailableRecoveryRequest() {
        boolean requested = unavailableRecoveryRequested;
        unavailableRecoveryRequested = false;
        return requested;
    }

    synchronized ManualHoldReason getManualHoldReason() {
        return manualHoldReason;
    }
}
