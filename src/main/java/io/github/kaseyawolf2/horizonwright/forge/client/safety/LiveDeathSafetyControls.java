package io.github.kaseyawolf2.horizonwright.forge.client.safety;

import java.util.Optional;

import io.github.kaseyawolf2.horizonwright.HorizonwrightMod;
import io.github.kaseyawolf2.horizonwright.HorizonwrightRuntime;
import io.github.kaseyawolf2.horizonwright.core.action.ActionLease;
import io.github.kaseyawolf2.horizonwright.core.navigation.BackendAvailability;
import io.github.kaseyawolf2.horizonwright.core.navigation.NavigationBackend;
import io.github.kaseyawolf2.horizonwright.core.navigation.NavigationHandle;
import io.github.kaseyawolf2.horizonwright.core.navigation.NavigationProgress;
import io.github.kaseyawolf2.horizonwright.core.navigation.NavigationRequest;
import io.github.kaseyawolf2.horizonwright.core.navigation.NavigationState;
import io.github.kaseyawolf2.horizonwright.core.safety.death.DeathSafetySnapshot;
import io.github.kaseyawolf2.horizonwright.core.safety.death.ManualHoldReason;
import io.github.kaseyawolf2.horizonwright.core.safety.death.RecoveryNavigationRequest;
import io.github.kaseyawolf2.horizonwright.core.safety.death.RecoveryNavigationStatus;

/** Conservative production controls for the live death-safety directive effect. */
final class LiveDeathSafetyControls implements MinecraftDeathSafetyDirectiveEffect.TaskWorkControl,
    MinecraftDeathSafetyDirectiveEffect.ActionAuthorityControl, MinecraftDeathSafetyDirectiveEffect.NavigationControl,
    MinecraftDeathSafetyDirectiveEffect.ContainerEpochControl, MinecraftDeathSafetyDirectiveEffect.ManualHoldControl {

    private final HorizonwrightRuntime runtime;
    private NavigationHandle recoveryHandle;
    private ActionLease recoveryLease;
    private long recoveryDeathEpoch;
    private String recoveryFailure;
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
        cancelRecoveryNavigation("death safety cancelled navigation");
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
        cancelRecoveryNavigation("death safety revoked action authority");
        runtime.getActionBroker()
            .revokeAll();
    }

    @Override
    public void invalidateActionEpoch(DeathSafetySnapshot snapshot) {
        cancelRecoveryNavigation("death safety advanced the action epoch");
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
        cancelRecoveryNavigation("death recovery completed");
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
        cancelRecoveryNavigation("replaced recovery route");
        recoveryDeathEpoch = request.getDeathEpoch();
        NavigationBackend backend = runtime.getNavigationBackend();
        if (backend == null) {
            recoveryFailure = "No navigation backend is configured";
            return;
        }
        ActionLease lease = null;
        try {
            BackendAvailability availability = backend.availability();
            if (availability == null || !availability.isAvailable()) {
                recoveryFailure = availability == null ? "Navigation backend returned no availability"
                    : availability.getDiagnostic();
                return;
            }
            Optional<ActionLease> acquired = runtime.getActionBroker()
                .tryAcquireSafetyRecovery("death-recovery-" + request.getDeathEpoch());
            if (!acquired.isPresent()) {
                recoveryFailure = "Movement/look safety-recovery authority is unavailable";
                return;
            }
            lease = acquired.get();
            recoveryHandle = backend.submit(
                new NavigationRequest(
                    "death-recovery-" + request.getDeathEpoch(),
                    lease.getEpoch(),
                    request.getTarget()
                        .getDimensionId(),
                    request.getTarget()
                        .getX(),
                    request.getTarget()
                        .getY(),
                    request.getTarget()
                        .getZ(),
                    request.getArrivalTolerance()),
                lease);
            recoveryLease = lease;
            recoveryFailure = null;
        } catch (RuntimeException failure) {
            if (lease != null) {
                lease.close();
            }
            recoveryFailure = failure.getClass()
                .getSimpleName() + (failure.getMessage() == null ? "" : ": " + failure.getMessage());
            HorizonwrightMod.LOG.error("Could not start interaction-disabled death recovery navigation", failure);
        }
    }

    @Override
    public void invalidateContainerEpoch(DeathSafetySnapshot snapshot) {
        runtime.getActionBroker()
            .revokeAll();
    }

    @Override
    public synchronized void enterManualHold(ManualHoldReason reason, DeathSafetySnapshot snapshot) {
        cancelRecoveryNavigation("death recovery entered manual hold");
        manualHoldReason = reason;
        HorizonwrightMod.LOG.error("Death recovery entered manual hold: {}", reason);
    }

    synchronized Optional<RecoveryNavigationStatus> pollRecoveryNavigation(long deathEpoch) {
        if (deathEpoch != recoveryDeathEpoch) {
            return Optional.empty();
        }
        if (recoveryFailure != null) {
            recoveryFailure = null;
            return Optional.of(RecoveryNavigationStatus.FAILED);
        }
        if (recoveryHandle == null) {
            return Optional.empty();
        }
        final NavigationProgress progress;
        try {
            progress = recoveryHandle.progress();
        } catch (RuntimeException failure) {
            HorizonwrightMod.LOG.error("Could not read recovery navigation progress", failure);
            cancelRecoveryNavigation("recovery progress failed");
            return Optional.of(RecoveryNavigationStatus.FAILED);
        }
        NavigationState state = progress.getState();
        if (state == NavigationState.COMPLETED || state == NavigationState.CANCELLED
            || state == NavigationState.FAILED) {
            RecoveryNavigationStatus status = state == NavigationState.COMPLETED ? RecoveryNavigationStatus.ARRIVED
                : RecoveryNavigationStatus.FAILED;
            closeRecoveryLease();
            recoveryHandle = null;
            return Optional.of(status);
        }
        return Optional.of(RecoveryNavigationStatus.IN_PROGRESS);
    }

    synchronized ManualHoldReason getManualHoldReason() {
        return manualHoldReason;
    }

    private synchronized void cancelRecoveryNavigation(String reason) {
        NavigationHandle handle = recoveryHandle;
        recoveryHandle = null;
        recoveryFailure = null;
        recoveryDeathEpoch = 0L;
        if (handle != null) {
            try {
                handle.cancel();
            } catch (RuntimeException failure) {
                HorizonwrightMod.LOG.error("Failed to cancel recovery navigation: {}", reason, failure);
            }
        }
        closeRecoveryLease();
    }

    private void closeRecoveryLease() {
        ActionLease lease = recoveryLease;
        recoveryLease = null;
        if (lease != null) {
            lease.close();
        }
    }
}
