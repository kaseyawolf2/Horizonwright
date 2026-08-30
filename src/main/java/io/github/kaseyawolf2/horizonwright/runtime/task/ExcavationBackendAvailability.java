package io.github.kaseyawolf2.horizonwright.runtime.task;

/** Exact availability of the optional live excavation adapter. */
public final class ExcavationBackendAvailability {

    private final boolean available;
    private final String diagnostic;

    private ExcavationBackendAvailability(boolean available, String diagnostic) {
        this.available = available;
        this.diagnostic = requireDiagnostic(diagnostic);
    }

    public static ExcavationBackendAvailability available(String diagnostic) {
        return new ExcavationBackendAvailability(true, diagnostic);
    }

    public static ExcavationBackendAvailability unavailable(String diagnostic) {
        return new ExcavationBackendAvailability(false, diagnostic);
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
