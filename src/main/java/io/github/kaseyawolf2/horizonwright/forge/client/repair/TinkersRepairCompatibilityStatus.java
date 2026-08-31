package io.github.kaseyawolf2.horizonwright.forge.client.repair;

/** Exact-version availability result for the optional live repair capability. */
public final class TinkersRepairCompatibilityStatus {

    private final boolean available;
    private final boolean referenceBytes;
    private final String diagnostic;

    private TinkersRepairCompatibilityStatus(boolean available, boolean referenceBytes, String diagnostic) {
        this.available = available;
        this.referenceBytes = referenceBytes;
        this.diagnostic = diagnostic;
    }

    static TinkersRepairCompatibilityStatus available(boolean referenceBytes, String diagnostic) {
        return new TinkersRepairCompatibilityStatus(true, referenceBytes, diagnostic);
    }

    static TinkersRepairCompatibilityStatus unavailable(String diagnostic) {
        return new TinkersRepairCompatibilityStatus(false, false, diagnostic);
    }

    public boolean isAvailable() {
        return available;
    }

    public boolean isReferenceBytes() {
        return referenceBytes;
    }

    public String getDiagnostic() {
        return diagnostic;
    }
}
