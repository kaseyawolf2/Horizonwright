package io.github.kaseyawolf2.horizonwright.core.base;

/** Verified seed inventory evidence captured with the crop observation. */
public final class SeedReserveEvidence {

    private final ReserveEvidence evidence;

    public SeedReserveEvidence(long inventoryRevision, String inventoryFingerprint, String seedFingerprint,
        int availableSeeds, int minimumReserve) {
        evidence = new ReserveEvidence(
            inventoryRevision,
            inventoryFingerprint,
            seedFingerprint,
            availableSeeds,
            minimumReserve);
    }

    public long getInventoryRevision() {
        return evidence.getInventoryRevision();
    }

    public String getInventoryFingerprint() {
        return evidence.getInventoryFingerprint();
    }

    public String getSeedFingerprint() {
        return evidence.getMaterialFingerprint();
    }

    public int getAvailableSeeds() {
        return evidence.getAvailableItems();
    }

    public int getMinimumReserve() {
        return evidence.getMinimumReserve();
    }

    public boolean canReplantAndPreserveReserve() {
        return evidence.canConsumeOne();
    }

    public boolean isForMaterial(String requiredSeedFingerprint) {
        return requiredSeedFingerprint != null && evidence.getMaterialFingerprint()
            .equals(requiredSeedFingerprint.trim());
    }

    public boolean isSameSnapshot(SeedReserveEvidence other) {
        return other != null && getInventoryRevision() == other.getInventoryRevision()
            && getInventoryFingerprint().equals(other.getInventoryFingerprint())
            && getSeedFingerprint().equals(other.getSeedFingerprint())
            && getAvailableSeeds() == other.getAvailableSeeds()
            && getMinimumReserve() == other.getMinimumReserve();
    }
}
