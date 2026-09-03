package io.github.kaseyawolf2.horizonwright.runtime.task;

public final class RepairBackendAvailability {

    private final boolean available;
    private final boolean waitingForOperator;
    private final String diagnostic;

    private RepairBackendAvailability(boolean available, boolean waitingForOperator, String diagnostic) {
        this.available = available;
        this.waitingForOperator = waitingForOperator;
        this.diagnostic = diagnostic == null ? "" : diagnostic;
    }

    public static RepairBackendAvailability available(String diagnostic) {
        return new RepairBackendAvailability(true, false, diagnostic);
    }

    public static RepairBackendAvailability unavailable(String diagnostic) {
        return new RepairBackendAvailability(false, false, diagnostic);
    }

    public static RepairBackendAvailability waitingForOperator(String diagnostic) {
        return new RepairBackendAvailability(false, true, diagnostic);
    }

    public boolean isAvailable() {
        return available;
    }

    public boolean isWaitingForOperator() {
        return waitingForOperator;
    }

    public String getDiagnostic() {
        return diagnostic;
    }
}
