package io.github.kaseyawolf2.horizonwright.forge.client;

import java.util.Optional;

import io.github.kaseyawolf2.horizonwright.HorizonwrightRuntime;
import io.github.kaseyawolf2.horizonwright.runtime.persistence.session.ClientRuntimeSessionDiagnostic;
import io.github.kaseyawolf2.horizonwright.runtime.persistence.session.CurrentRuntimeProvider;

/** Converts the late-bound runtime session into a non-throwing UI lookup. */
final class CurrentRuntimeUiResolver {

    private CurrentRuntimeUiResolver() {}

    static Resolution resolve(CurrentRuntimeProvider provider) {
        if (provider == null) {
            return Resolution.unavailable("UNAVAILABLE: Runtime provider is not installed");
        }
        try {
            Optional<HorizonwrightRuntime> runtime = provider.getCurrentRuntime();
            if (runtime != null && runtime.isPresent()) {
                return Resolution.available(runtime.get());
            }
            return Resolution.unavailable(diagnostic(provider, null));
        } catch (RuntimeException failure) {
            return Resolution.unavailable(diagnostic(provider, failure));
        }
    }

    private static String diagnostic(CurrentRuntimeProvider provider, RuntimeException lookupFailure) {
        try {
            ClientRuntimeSessionDiagnostic diagnostic = provider.getDiagnostic();
            if (diagnostic != null) {
                String message = diagnostic.getState()
                    .name() + ": "
                    + diagnostic.getMessage();
                return lookupFailure == null ? message : message + " (runtime lookup failed safely)";
            }
        } catch (RuntimeException diagnosticFailure) {
            // The UI boundary must remain usable even when a provider has failed internally.
        }
        if (lookupFailure != null && lookupFailure.getMessage() != null
            && !lookupFailure.getMessage()
                .trim()
                .isEmpty()) {
            return "UNAVAILABLE: " + lookupFailure.getMessage();
        }
        return "UNAVAILABLE: Runtime session diagnostic is unavailable";
    }

    static final class Resolution {

        private final HorizonwrightRuntime runtime;
        private final String diagnostic;

        private Resolution(HorizonwrightRuntime runtime, String diagnostic) {
            this.runtime = runtime;
            this.diagnostic = diagnostic;
        }

        static Resolution available(HorizonwrightRuntime runtime) {
            return new Resolution(runtime, "ACTIVE: Runtime session is active");
        }

        static Resolution unavailable(String diagnostic) {
            return new Resolution(null, diagnostic);
        }

        boolean isAvailable() {
            return runtime != null;
        }

        HorizonwrightRuntime getRuntime() {
            if (runtime == null) {
                throw new IllegalStateException("runtime is unavailable: " + diagnostic);
            }
            return runtime;
        }

        String getDiagnostic() {
            return diagnostic;
        }
    }
}
