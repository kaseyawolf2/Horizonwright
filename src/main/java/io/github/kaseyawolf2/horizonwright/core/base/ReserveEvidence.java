package io.github.kaseyawolf2.horizonwright.core.base;

/** Shared validation for typed, immutable replant-material evidence. */
final class ReserveEvidence {

    private final long inventoryRevision;
    private final String inventoryFingerprint;
    private final String materialFingerprint;
    private final int availableItems;
    private final int minimumReserve;

    ReserveEvidence(long inventoryRevision, String inventoryFingerprint, String materialFingerprint, int availableItems,
        int minimumReserve) {
        if (inventoryRevision < 0L || availableItems < 0 || minimumReserve < 0) {
            throw new IllegalArgumentException("reserve evidence values must not be negative");
        }
        this.inventoryFingerprint = requireFingerprint(inventoryFingerprint, "inventoryFingerprint");
        this.materialFingerprint = requireFingerprint(materialFingerprint, "materialFingerprint");
        this.inventoryRevision = inventoryRevision;
        this.availableItems = availableItems;
        this.minimumReserve = minimumReserve;
    }

    long getInventoryRevision() {
        return inventoryRevision;
    }

    String getInventoryFingerprint() {
        return inventoryFingerprint;
    }

    String getMaterialFingerprint() {
        return materialFingerprint;
    }

    int getAvailableItems() {
        return availableItems;
    }

    int getMinimumReserve() {
        return minimumReserve;
    }

    boolean canConsumeOne() {
        return availableItems > minimumReserve;
    }

    private static String requireFingerprint(String value, String name) {
        if (value == null || value.trim()
            .isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
