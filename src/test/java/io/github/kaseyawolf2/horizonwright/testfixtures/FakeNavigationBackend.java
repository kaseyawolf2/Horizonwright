package io.github.kaseyawolf2.horizonwright.testfixtures;

import io.github.kaseyawolf2.horizonwright.core.action.ActionCapability;
import io.github.kaseyawolf2.horizonwright.core.action.ActionLease;
import io.github.kaseyawolf2.horizonwright.core.navigation.BackendAvailability;
import io.github.kaseyawolf2.horizonwright.core.navigation.NavigationBackend;
import io.github.kaseyawolf2.horizonwright.core.navigation.NavigationHandle;
import io.github.kaseyawolf2.horizonwright.core.navigation.NavigationProgress;
import io.github.kaseyawolf2.horizonwright.core.navigation.NavigationRequest;
import io.github.kaseyawolf2.horizonwright.core.navigation.NavigationState;

public final class FakeNavigationBackend implements NavigationBackend {

    private boolean available = true;
    private String diagnostic = "Deterministic fake navigation backend";
    private FakeHandle activeHandle;

    @Override
    public BackendAvailability availability() {
        return available ? BackendAvailability.available(diagnostic) : BackendAvailability.unavailable(diagnostic);
    }

    @Override
    public NavigationHandle submit(NavigationRequest request, ActionLease movementLease) {
        if (!available) {
            throw new IllegalStateException(diagnostic);
        }
        if (!movementLease.isValid()) {
            throw new IllegalArgumentException("movement lease is not valid");
        }
        if (!movementLease.getCapabilities()
            .contains(ActionCapability.MOVEMENT)) {
            throw new IllegalArgumentException("movement capability is required");
        }
        if (request.getActionEpoch() != movementLease.getEpoch()) {
            throw new IllegalArgumentException("request and lease epochs differ");
        }
        if (activeHandle != null && !activeHandle.isTerminal()) {
            throw new IllegalStateException("fake backend already has an active request");
        }
        activeHandle = new FakeHandle(request, movementLease);
        return activeHandle;
    }

    public void startMoving() {
        requireActive().state = NavigationState.MOVING;
    }

    public void complete() {
        FakeHandle handle = requireActive();
        if (!handle.movementLease.isValid()) {
            handle.state = NavigationState.CANCELLED;
            handle.detail = "Action lease revoked";
            return;
        }
        handle.state = NavigationState.COMPLETED;
        handle.detail = "Target reached";
    }

    public void setUnavailable(String diagnostic) {
        available = false;
        this.diagnostic = diagnostic;
        if (activeHandle != null && !activeHandle.isTerminal()) {
            activeHandle.state = NavigationState.FAILED;
            activeHandle.detail = diagnostic;
        }
    }

    private FakeHandle requireActive() {
        if (activeHandle == null || activeHandle.isTerminal()) {
            throw new IllegalStateException("no active fake navigation request");
        }
        return activeHandle;
    }

    private static final class FakeHandle implements NavigationHandle {

        private final NavigationRequest request;
        private final ActionLease movementLease;
        private NavigationState state = NavigationState.SUBMITTED;
        private String detail = "Request accepted";

        private FakeHandle(NavigationRequest request, ActionLease movementLease) {
            this.request = request;
            this.movementLease = movementLease;
        }

        @Override
        public String getRequestId() {
            return request.getRequestId();
        }

        @Override
        public NavigationProgress progress() {
            if (!isTerminal() && !movementLease.isValid()) {
                state = NavigationState.CANCELLED;
                detail = "Action lease revoked";
            }
            return new NavigationProgress(request.getRequestId(), request.getActionEpoch(), state, detail);
        }

        @Override
        public void cancel() {
            if (!isTerminal()) {
                state = NavigationState.CANCELLED;
                detail = "Cancelled";
            }
        }

        private boolean isTerminal() {
            return state == NavigationState.COMPLETED || state == NavigationState.CANCELLED
                || state == NavigationState.FAILED;
        }
    }
}
