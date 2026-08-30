package io.github.kaseyawolf2.horizonwright;

import java.util.EnumSet;
import java.util.Optional;

import io.github.kaseyawolf2.horizonwright.core.action.ActionBrokerSnapshot;
import io.github.kaseyawolf2.horizonwright.core.action.ActionCapability;
import io.github.kaseyawolf2.horizonwright.core.action.ActionLease;
import io.github.kaseyawolf2.horizonwright.core.action.ActionRevocationListener;
import io.github.kaseyawolf2.horizonwright.core.action.ActionSessionGuard;
import io.github.kaseyawolf2.horizonwright.core.action.InMemoryActionBroker;
import io.github.kaseyawolf2.horizonwright.core.navigation.NavigationBackend;
import io.github.kaseyawolf2.horizonwright.core.navigation.NavigationHandle;
import io.github.kaseyawolf2.horizonwright.core.navigation.NavigationProgress;
import io.github.kaseyawolf2.horizonwright.core.navigation.NavigationRequest;
import io.github.kaseyawolf2.horizonwright.core.navigation.NavigationState;

public final class HorizonwrightRuntime {

    private static final HorizonwrightRuntime INSTANCE = new HorizonwrightRuntime();

    private final InMemoryActionBroker actionBroker = new InMemoryActionBroker();
    private final ActionSessionGuard actionSessionGuard = new ActionSessionGuard();
    private final long startedAtNanos = System.nanoTime();
    private volatile String navigationDiagnostic = "No navigation backend configured";
    private NavigationBackend navigationBackend;
    private NavigationHandle activeNavigationHandle;
    private ActionLease activeNavigationLease;
    private NavigationProgress lastNavigationProgress;
    private long nextNavigationRequestId = 1L;

    private HorizonwrightRuntime() {
        actionBroker.addRevocationListener(actionSessionGuard);
    }

    public static HorizonwrightRuntime getInstance() {
        return INSTANCE;
    }

    public InMemoryActionBroker getActionBroker() {
        return actionBroker;
    }

    public ActionSessionGuard getActionSessionGuard() {
        return actionSessionGuard;
    }

    public synchronized RuntimeSnapshot snapshot() {
        NavigationProgress progress = activeNavigationHandle == null ? lastNavigationProgress
            : activeNavigationHandle.progress();
        return new RuntimeSnapshot(
            actionBroker.snapshot(),
            navigationDiagnostic,
            progress,
            System.nanoTime() - startedAtNanos);
    }

    public void emergencyStop(String reason) {
        try {
            actionBroker.enterSafetyLockdown();
        } catch (RuntimeException failure) {
            HorizonwrightMod.LOG.error("Emergency revocation listener failed after safety lockdown latched", failure);
        }
        HorizonwrightMod.LOG.warn("Emergency stop latched: {}", reason);
    }

    public synchronized void setNavigationDiagnostic(String diagnostic) {
        if (diagnostic == null || diagnostic.trim()
            .isEmpty()) {
            throw new IllegalArgumentException("diagnostic must not be blank");
        }
        navigationDiagnostic = diagnostic;
    }

    public synchronized void installNavigationBackend(NavigationBackend backend) {
        if (backend == null) {
            throw new IllegalArgumentException("backend must not be null");
        }
        if (activeNavigationHandle != null || actionSessionGuard.isGuarding()) {
            throw new IllegalStateException("cannot replace the navigation backend while an action session is active");
        }
        if (navigationBackend instanceof ActionRevocationListener) {
            actionBroker.removeRevocationListener((ActionRevocationListener) navigationBackend);
        }
        navigationBackend = backend;
        if (backend instanceof ActionRevocationListener) {
            actionBroker.addRevocationListener((ActionRevocationListener) backend);
        }
        navigationDiagnostic = backend.availability()
            .getDiagnostic();
    }

    public synchronized String startNavigation(int dimensionId, int x, int y, int z, int tolerance) {
        if (navigationBackend == null || !navigationBackend.availability()
            .isAvailable()) {
            throw new IllegalStateException(navigationDiagnostic);
        }
        if (activeNavigationHandle != null) {
            throw new IllegalStateException("a navigation request is already active");
        }
        if (!actionSessionGuard.isReadyForSession()) {
            throw new IllegalStateException(actionSessionGuard.readinessDiagnostic());
        }
        Optional<ActionLease> acquired = actionBroker
            .tryAcquire("manual-goto", EnumSet.of(ActionCapability.MOVEMENT, ActionCapability.LOOK));
        if (!acquired.isPresent()) {
            throw new IllegalStateException("MOVEMENT/LOOK action lease is unavailable");
        }
        ActionLease lease = acquired.get();
        try {
            NavigationRequest request = new NavigationRequest(
                "manual-goto-" + nextNavigationRequestId++,
                lease.getEpoch(),
                dimensionId,
                x,
                y,
                z,
                tolerance);
            activeNavigationHandle = navigationBackend.submit(request, lease);
            activeNavigationLease = lease;
            lastNavigationProgress = activeNavigationHandle.progress();
            return request.getRequestId();
        } catch (RuntimeException failure) {
            lease.close();
            throw failure;
        }
    }

    public synchronized boolean cancelNavigation(String reason) {
        if (activeNavigationHandle == null) {
            return false;
        }
        NavigationHandle handle = activeNavigationHandle;
        RuntimeException cancellationFailure = null;
        try {
            handle.cancel();
        } catch (RuntimeException failure) {
            cancellationFailure = failure;
        } finally {
            try {
                lastNavigationProgress = handle.progress();
            } catch (RuntimeException progressFailure) {
                if (cancellationFailure == null) {
                    cancellationFailure = progressFailure;
                } else {
                    cancellationFailure.addSuppressed(progressFailure);
                }
            }
            activeNavigationHandle = null;
            if (activeNavigationLease != null) {
                activeNavigationLease.close();
                activeNavigationLease = null;
            }
        }
        HorizonwrightMod.LOG.info("Navigation cancelled: {}", reason);
        if (cancellationFailure != null) {
            throw cancellationFailure;
        }
        return true;
    }

    public synchronized void clientTick() {
        if (navigationBackend != null) {
            boolean tickFailed = false;
            try {
                navigationBackend.clientTick();
            } catch (RuntimeException failure) {
                tickFailed = true;
                navigationDiagnostic = "Navigation cleanup failed: " + failure.getMessage();
                HorizonwrightMod.LOG.error("Navigation backend client tick failed", failure);
            }
            if (!tickFailed) {
                navigationDiagnostic = navigationBackend.availability()
                    .getDiagnostic();
            }
        }
        if (activeNavigationHandle == null) {
            return;
        }
        NavigationProgress progress = activeNavigationHandle.progress();
        lastNavigationProgress = progress;
        if (isTerminal(progress.getState())) {
            activeNavigationHandle = null;
            if (activeNavigationLease != null) {
                activeNavigationLease.close();
                activeNavigationLease = null;
            }
        }
    }

    private static boolean isTerminal(NavigationState state) {
        return state == NavigationState.COMPLETED || state == NavigationState.CANCELLED
            || state == NavigationState.FAILED;
    }

    public static final class RuntimeSnapshot {

        private final ActionBrokerSnapshot actionBroker;
        private final String navigationDiagnostic;
        private final NavigationProgress navigationProgress;
        private final long uptimeNanos;

        private RuntimeSnapshot(ActionBrokerSnapshot actionBroker, String navigationDiagnostic,
            NavigationProgress navigationProgress, long uptimeNanos) {
            this.actionBroker = actionBroker;
            this.navigationDiagnostic = navigationDiagnostic;
            this.navigationProgress = navigationProgress;
            this.uptimeNanos = uptimeNanos;
        }

        public ActionBrokerSnapshot getActionBroker() {
            return actionBroker;
        }

        public String getNavigationDiagnostic() {
            return navigationDiagnostic;
        }

        public NavigationProgress getNavigationProgress() {
            return navigationProgress;
        }

        public long getUptimeNanos() {
            return uptimeNanos;
        }
    }
}
