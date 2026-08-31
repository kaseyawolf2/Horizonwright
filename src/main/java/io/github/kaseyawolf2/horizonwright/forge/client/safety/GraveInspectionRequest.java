package io.github.kaseyawolf2.horizonwright.forge.client.safety;

import io.github.kaseyawolf2.horizonwright.core.safety.death.GraveIdentity;
import io.github.kaseyawolf2.horizonwright.core.safety.death.InventoryManifest;

/** Exact-grave inspection request carrying the same conservative evidence used during discovery. */
public final class GraveInspectionRequest {

    private final GraveIdentity identity;
    private final String expectedOwnerIdentity;
    private final String expectedOwnerUsername;
    private final InventoryManifest conservativeExpectedContents;

    public GraveInspectionRequest(GraveIdentity identity, String expectedOwnerIdentity, String expectedOwnerUsername,
        InventoryManifest conservativeExpectedContents) {
        if (identity == null || !hasText(expectedOwnerIdentity)
            || !hasText(expectedOwnerUsername)
            || conservativeExpectedContents == null) {
            throw new IllegalArgumentException(
                "exact grave inspection requires identity, owner, and contents evidence");
        }
        this.identity = identity;
        this.expectedOwnerIdentity = expectedOwnerIdentity.trim();
        this.expectedOwnerUsername = expectedOwnerUsername.trim();
        this.conservativeExpectedContents = conservativeExpectedContents;
    }

    public GraveIdentity getIdentity() {
        return identity;
    }

    public String getExpectedOwnerIdentity() {
        return expectedOwnerIdentity;
    }

    public String getExpectedOwnerUsername() {
        return expectedOwnerUsername;
    }

    public InventoryManifest getConservativeExpectedContents() {
        return conservativeExpectedContents;
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim()
            .isEmpty();
    }
}
