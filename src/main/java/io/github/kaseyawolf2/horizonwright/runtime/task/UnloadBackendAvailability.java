package io.github.kaseyawolf2.horizonwright.runtime.task;

/** Explicit availability for the configured storage/container adapter. */
public final class UnloadBackendAvailability {

    private final boolean available;
    private final String diagnostic;

    private UnloadBackendAvailability(boolean available, String diagnostic) {
        this.available = available;
        this.diagnostic = diagnostic == null ? "" : diagnostic;
    }

    public static UnloadBackendAvailability available(String diagnostic) {
        return new UnloadBackendAvailability(true, diagnostic);
    }

    public static UnloadBackendAvailability unavailable(String diagnostic) {
        return new UnloadBackendAvailability(false, diagnostic);
    }

    public boolean isAvailable() {
        return available;
    }

    public String getDiagnostic() {
        return diagnostic;
    }
}
