package io.github.kaseyawolf2.horizonwright.forge.client.farm;

/** Exact-version availability result for Hunger Overhaul right-click harvesting. */
final class HungerOverhaulCompatibilityStatus {

    private final boolean available;
    private final boolean referenceBytes;
    private final String diagnostic;

    private HungerOverhaulCompatibilityStatus(boolean available, boolean referenceBytes, String diagnostic) {
        this.available = available;
        this.referenceBytes = referenceBytes;
        this.diagnostic = diagnostic;
    }

    static HungerOverhaulCompatibilityStatus available(boolean referenceBytes, String diagnostic) {
        return new HungerOverhaulCompatibilityStatus(true, referenceBytes, diagnostic);
    }

    static HungerOverhaulCompatibilityStatus unavailable(String diagnostic) {
        return new HungerOverhaulCompatibilityStatus(false, false, diagnostic);
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
