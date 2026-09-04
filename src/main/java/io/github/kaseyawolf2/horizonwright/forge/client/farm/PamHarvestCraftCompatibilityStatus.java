package io.github.kaseyawolf2.horizonwright.forge.client.farm;

/** Exact-version availability result for optional Pam HarvestCraft farming. */
final class PamHarvestCraftCompatibilityStatus {

    private final boolean available;
    private final boolean referenceBytes;
    private final String diagnostic;

    private PamHarvestCraftCompatibilityStatus(boolean available, boolean referenceBytes, String diagnostic) {
        this.available = available;
        this.referenceBytes = referenceBytes;
        this.diagnostic = diagnostic;
    }

    static PamHarvestCraftCompatibilityStatus available(boolean referenceBytes, String diagnostic) {
        return new PamHarvestCraftCompatibilityStatus(true, referenceBytes, diagnostic);
    }

    static PamHarvestCraftCompatibilityStatus unavailable(String diagnostic) {
        return new PamHarvestCraftCompatibilityStatus(false, false, diagnostic);
    }

    boolean isAvailable() {
        return available;
    }

    boolean isReferenceBytes() {
        return referenceBytes;
    }

    String getDiagnostic() {
        return diagnostic;
    }
}
