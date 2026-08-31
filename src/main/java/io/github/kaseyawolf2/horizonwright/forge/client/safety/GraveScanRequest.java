package io.github.kaseyawolf2.horizonwright.forge.client.safety;

import io.github.kaseyawolf2.horizonwright.core.safety.death.DimensionBlockPosition;
import io.github.kaseyawolf2.horizonwright.core.safety.death.InventoryManifest;

/** Bounded grave scan request around the recorded death location. */
public final class GraveScanRequest {

    private final DimensionBlockPosition deathPosition;
    private final int radius;
    private final String expectedOwnerIdentity;
    private final String expectedOwnerUsername;
    private final InventoryManifest conservativeExpectedContents;

    public GraveScanRequest(DimensionBlockPosition deathPosition, int radius) {
        this(deathPosition, radius, null, null, null);
    }

    public GraveScanRequest(DimensionBlockPosition deathPosition, int radius, String expectedOwnerIdentity,
        String expectedOwnerUsername, InventoryManifest conservativeExpectedContents) {
        if (deathPosition == null || radius < 0) {
            throw new IllegalArgumentException("deathPosition must not be null and radius must be non-negative");
        }
        boolean completeOwnerEvidence = hasText(expectedOwnerIdentity) && hasText(expectedOwnerUsername)
            && conservativeExpectedContents != null;
        boolean noOwnerEvidence = expectedOwnerIdentity == null && expectedOwnerUsername == null
            && conservativeExpectedContents == null;
        if (!completeOwnerEvidence && !noOwnerEvidence) {
            throw new IllegalArgumentException("grave owner and conservative contents evidence must be all-or-none");
        }
        this.deathPosition = deathPosition;
        this.radius = radius;
        this.expectedOwnerIdentity = completeOwnerEvidence ? expectedOwnerIdentity.trim() : null;
        this.expectedOwnerUsername = completeOwnerEvidence ? expectedOwnerUsername.trim() : null;
        this.conservativeExpectedContents = conservativeExpectedContents;
    }

    public DimensionBlockPosition getDeathPosition() {
        return deathPosition;
    }

    public int getRadius() {
        return radius;
    }

    public boolean hasRecoveryEvidence() {
        return expectedOwnerIdentity != null;
    }

    public String getExpectedOwnerIdentity() {
        requireRecoveryEvidence();
        return expectedOwnerIdentity;
    }

    public String getExpectedOwnerUsername() {
        requireRecoveryEvidence();
        return expectedOwnerUsername;
    }

    public InventoryManifest getConservativeExpectedContents() {
        requireRecoveryEvidence();
        return conservativeExpectedContents;
    }

    private void requireRecoveryEvidence() {
        if (!hasRecoveryEvidence()) {
            throw new IllegalStateException("grave scan request has no owner/content recovery evidence");
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim()
            .isEmpty();
    }
}
