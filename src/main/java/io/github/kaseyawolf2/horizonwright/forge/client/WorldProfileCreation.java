package io.github.kaseyawolf2.horizonwright.forge.client;

import io.github.kaseyawolf2.horizonwright.runtime.persistence.session.ClientProfileBindingCoordinator;
import io.github.kaseyawolf2.horizonwright.runtime.persistence.session.ClientProfileBindingSnapshot;
import io.github.kaseyawolf2.horizonwright.runtime.persistence.session.ClientProfileBindingState;

/** World-bound state for the dashboard's profile creation action, available before a runtime exists. */
final class WorldProfileCreation {

    private final ClientProfileBindingCoordinator bindings;
    private ClientProfileBindingSnapshot displayed;

    WorldProfileCreation(ClientProfileBindingCoordinator bindings) {
        this.bindings = bindings;
        refresh();
    }

    void refresh() {
        displayed = bindings == null ? null : bindings.getSnapshot();
    }

    boolean canCreate() {
        return displayed != null && displayed.getState() == ClientProfileBindingState.NEEDS_EXPLICIT_ENROLLMENT;
    }

    String diagnostic(String fallback) {
        return displayed == null ? fallback : displayed.getDiagnostic();
    }

    String create() {
        if (!canCreate()) return "Profile creation is unavailable for this world.";
        if (bindings.getSnapshot() != displayed) {
            refresh();
            return "The world profile changed. Review it before creating a profile.";
        }
        try {
            bindings.confirmEnrollment(true);
            return "World profile created. You can now configure your base and tasks.";
        } catch (RuntimeException failure) {
            String detail = failure.getMessage() == null ? failure.getClass()
                .getSimpleName() : failure.getMessage();
            return "Could not create world profile: " + detail;
        } finally {
            refresh();
        }
    }
}
