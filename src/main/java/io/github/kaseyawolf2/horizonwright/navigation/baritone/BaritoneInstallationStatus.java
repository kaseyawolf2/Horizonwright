package io.github.kaseyawolf2.horizonwright.navigation.baritone;

/**
 * Immutable result of validating the Baritone installation that Horizonwright would use.
 */
public final class BaritoneInstallationStatus {

    private final boolean available;
    private final boolean referenceBytes;
    private final String diagnostic;
    private final String sourceDescription;
    private final String sourceSha256;

    private BaritoneInstallationStatus(boolean available, boolean referenceBytes, String diagnostic,
        String sourceDescription, String sourceSha256) {
        this.available = available;
        this.referenceBytes = referenceBytes;
        this.diagnostic = requireText(diagnostic, "diagnostic");
        this.sourceDescription = requireText(sourceDescription, "sourceDescription");
        this.sourceSha256 = normalizeOptionalText(sourceSha256);
    }

    static BaritoneInstallationStatus available(boolean referenceBytes, String diagnostic, String sourceDescription,
        String sourceSha256) {
        return new BaritoneInstallationStatus(true, referenceBytes, diagnostic, sourceDescription, sourceSha256);
    }

    static BaritoneInstallationStatus unavailable(String diagnostic, String sourceDescription, String sourceSha256) {
        return new BaritoneInstallationStatus(false, false, diagnostic, sourceDescription, sourceSha256);
    }

    public boolean isAvailable() {
        return available;
    }

    /**
     * Returns whether the source is the exact, hash-pinned reference Baritone JAR.
     *
     * <p>
     * A deobfuscated development installation can be available while returning {@code false} here.
     * </p>
     */
    public boolean isReferenceBytes() {
        return referenceBytes;
    }

    public String getDiagnostic() {
        return diagnostic;
    }

    public String getSourceDescription() {
        return sourceDescription;
    }

    public boolean hasSourceSha256() {
        return sourceSha256 != null;
    }

    /**
     * Returns an uppercase SHA-256 digest, or {@code null} when the source cannot be represented by one file.
     */
    public String getSourceSha256() {
        return sourceSha256;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.trim()
            .isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    private static String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
