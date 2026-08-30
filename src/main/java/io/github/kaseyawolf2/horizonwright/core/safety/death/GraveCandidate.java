package io.github.kaseyawolf2.horizonwright.core.safety.death;

/** Owned-tile evidence read from an OpenBlocks grave. */
public final class GraveCandidate {

    private final GraveIdentity identity;
    private final String ownerIdentity;
    private final InventoryManifest contents;

    public GraveCandidate(GraveIdentity identity, String ownerIdentity, InventoryManifest contents) {
        if (identity == null || contents == null) {
            throw new IllegalArgumentException("identity and contents must not be null");
        }
        this.identity = identity;
        this.ownerIdentity = ConnectionIdentity.requireText(ownerIdentity, "ownerIdentity");
        this.contents = contents;
    }

    public GraveIdentity getIdentity() {
        return identity;
    }

    public String getOwnerIdentity() {
        return ownerIdentity;
    }

    public InventoryManifest getContents() {
        return contents;
    }

    public boolean hasSameStableEvidence(GraveCandidate other) {
        return other != null && identity.equals(other.identity)
            && ownerIdentity.equals(other.ownerIdentity)
            && contents.hasSameContents(other.contents);
    }
}
