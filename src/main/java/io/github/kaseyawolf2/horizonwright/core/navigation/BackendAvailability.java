package io.github.kaseyawolf2.horizonwright.core.navigation;

public final class BackendAvailability {

    private final boolean available;
    private final String diagnostic;

    private BackendAvailability(boolean available, String diagnostic) {
        this.available = available;
        this.diagnostic = diagnostic;
    }

    public static BackendAvailability available(String diagnostic) {
        return new BackendAvailability(true, requireDiagnostic(diagnostic));
    }

    public static BackendAvailability unavailable(String diagnostic) {
        return new BackendAvailability(false, requireDiagnostic(diagnostic));
    }

    public boolean isAvailable() {
        return available;
    }

    public String getDiagnostic() {
        return diagnostic;
    }

    private static String requireDiagnostic(String diagnostic) {
        if (diagnostic == null || diagnostic.trim()
            .isEmpty()) {
            throw new IllegalArgumentException("diagnostic must not be blank");
        }
        return diagnostic.trim();
    }
}
