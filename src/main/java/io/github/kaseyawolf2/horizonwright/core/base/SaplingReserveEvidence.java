package io.github.kaseyawolf2.horizonwright.core.base;

/** Verified sapling inventory evidence captured with the designated-tree observation. */
public final class SaplingReserveEvidence {

    private final ReserveEvidence evidence;

    public SaplingReserveEvidence(long inventoryRevision, String inventoryFingerprint, String saplingFingerprint,
        int availableSaplings, int minimumReserve) {
        evidence = new ReserveEvidence(
            inventoryRevision,
            inventoryFingerprint,
            saplingFingerprint,
            availableSaplings,
            minimumReserve);
    }

    public long getInventoryRevision() {
        return evidence.getInventoryRevision();
    }

    public String getInventoryFingerprint() {
        return evidence.getInventoryFingerprint();
    }

    public String getSaplingFingerprint() {
        return evidence.getMaterialFingerprint();
    }

    public int getAvailableSaplings() {
        return evidence.getAvailableItems();
    }

    public int getMinimumReserve() {
        return evidence.getMinimumReserve();
    }

    public boolean canReplantAndPreserveReserve() {
        return evidence.canConsumeOne();
    }

    public boolean isForMaterial(String requiredSaplingFingerprint) {
        return requiredSaplingFingerprint != null && evidence.getMaterialFingerprint()
            .equals(requiredSaplingFingerprint.trim());
    }

    public boolean isSameSnapshot(SaplingReserveEvidence other) {
        return other != null && getInventoryRevision() == other.getInventoryRevision()
            && getInventoryFingerprint().equals(other.getInventoryFingerprint())
            && getSaplingFingerprint().equals(other.getSaplingFingerprint())
            && getAvailableSaplings() == other.getAvailableSaplings()
            && getMinimumReserve() == other.getMinimumReserve();
    }
}
