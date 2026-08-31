package io.github.kaseyawolf2.horizonwright.runtime.task;

public final class RepairBackendAvailability {

    private final boolean available;
    private final String diagnostic;

    private RepairBackendAvailability(boolean available, String diagnostic) {
        this.available = available;
        this.diagnostic = diagnostic == null ? "" : diagnostic;
    }

    public static RepairBackendAvailability available(String diagnostic) {
        return new RepairBackendAvailability(true, diagnostic);
    }

    public static RepairBackendAvailability unavailable(String diagnostic) {
        return new RepairBackendAvailability(false, diagnostic);
    }

    public boolean isAvailable() {
        return available;
    }

    public String getDiagnostic() {
        return diagnostic;
    }
}
