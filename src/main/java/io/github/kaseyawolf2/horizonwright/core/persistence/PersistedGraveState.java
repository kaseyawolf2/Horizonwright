package io.github.kaseyawolf2.horizonwright.core.persistence;

import java.util.Objects;

/**
 * Restart checkpoint for grave evidence and the one-use activation boundary.
 *
 * <p>
 * A serialized permit is audit evidence only. Permits are connection-bound and must never be made live after a
 * restart; recovery must obtain fresh grave evidence. The activation-consumed bit is durable and prevents replay.
 * </p>
 */
public final class PersistedGraveState {

    private final String graveTileIdentity;
    private final DimensionPosition gravePosition;
    private final int stableTicks;
    private final String ownerIdentity;
    private final PersistedInventoryManifest contents;
    private final long activationPermitId;
    private final long activationPermitConnectionEpoch;
    private final long activationPermitDeathEpoch;
    private final boolean activationConsumed;

    public PersistedGraveState(String graveTileIdentity, DimensionPosition gravePosition, int stableTicks,
        long activationPermitId, long activationPermitConnectionEpoch, long activationPermitDeathEpoch,
        boolean activationConsumed) {
        this(
            graveTileIdentity,
            gravePosition,
            stableTicks,
            null,
            null,
            activationPermitId,
            activationPermitConnectionEpoch,
            activationPermitDeathEpoch,
            activationConsumed);
    }

    public PersistedGraveState(String graveTileIdentity, DimensionPosition gravePosition, int stableTicks,
        String ownerIdentity, PersistedInventoryManifest contents, long activationPermitId,
        long activationPermitConnectionEpoch, long activationPermitDeathEpoch, boolean activationConsumed) {
        this.graveTileIdentity = PersistenceValidation.normalizeOptionalText(graveTileIdentity);
        this.gravePosition = gravePosition;
        this.stableTicks = stableTicks;
        this.ownerIdentity = PersistenceValidation.normalizeOptionalText(ownerIdentity);
        this.contents = contents;
        this.activationPermitId = activationPermitId;
        this.activationPermitConnectionEpoch = activationPermitConnectionEpoch;
        this.activationPermitDeathEpoch = activationPermitDeathEpoch;
        this.activationConsumed = activationConsumed;
        validate();
    }

    public static PersistedGraveState none() {
        return new PersistedGraveState(null, null, 0, 0L, 0L, 0L, false);
    }

    public String getGraveTileIdentity() {
        return graveTileIdentity;
    }

    public DimensionPosition getGravePosition() {
        return gravePosition;
    }

    public int getStableTicks() {
        return stableTicks;
    }

    public long getActivationPermitId() {
        return activationPermitId;
    }

    public String getOwnerIdentity() {
        return ownerIdentity;
    }

    public PersistedInventoryManifest getContents() {
        return contents;
    }

    public boolean hasStableEvidence() {
        return graveTileIdentity != null && ownerIdentity != null && contents != null;
    }

    public long getActivationPermitConnectionEpoch() {
        return activationPermitConnectionEpoch;
    }

    public long getActivationPermitDeathEpoch() {
        return activationPermitDeathEpoch;
    }

    public boolean isActivationConsumed() {
        return activationConsumed;
    }

    public boolean hasGraveIdentity() {
        return graveTileIdentity != null;
    }

    /** Returns whether audit data contains a permit which must be invalidated rather than restored. */
    public boolean hasTransientActivationPermit() {
        return activationPermitId > 0L;
    }

    public boolean requiresActivationReplayBlock() {
        return activationConsumed;
    }

    public PersistedGraveState invalidatePermitForRestart() {
        return new PersistedGraveState(
            graveTileIdentity,
            gravePosition,
            stableTicks,
            ownerIdentity,
            contents,
            0L,
            0L,
            0L,
            activationConsumed);
    }

    void validate() {
        if ((graveTileIdentity == null) != (gravePosition == null)) {
            throw new IllegalArgumentException(
                "grave tile identity and position must either both exist or both be absent");
        }
        if (stableTicks < 0) {
            throw new IllegalArgumentException("grave stableTicks must not be negative");
        }
        if ((ownerIdentity == null) != (contents == null)) {
            throw new IllegalArgumentException("grave owner and contents must either both exist or both be absent");
        }
        if (ownerIdentity != null && graveTileIdentity == null) {
            throw new IllegalArgumentException("grave evidence requires an exact grave identity");
        }
        if (contents != null) {
            contents.validate();
        }
        boolean hasPermitId = activationPermitId > 0L;
        boolean hasPermitConnection = activationPermitConnectionEpoch > 0L;
        boolean hasPermitDeath = activationPermitDeathEpoch > 0L;
        if (hasPermitId != hasPermitConnection || hasPermitId != hasPermitDeath) {
            throw new IllegalArgumentException(
                "grave activation permit id, connection epoch, and death epoch must agree");
        }
        if (!hasPermitId && (activationPermitId != 0L || activationPermitConnectionEpoch != 0L
            || activationPermitDeathEpoch != 0L)) {
            throw new IllegalArgumentException("absent grave activation permit fields must be zero");
        }
        if (hasPermitId && graveTileIdentity == null) {
            throw new IllegalArgumentException("grave activation permit requires an exact grave identity");
        }
        if (hasPermitId && activationConsumed) {
            throw new IllegalArgumentException("a consumed grave activation cannot retain a live permit");
        }
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PersistedGraveState)) {
            return false;
        }
        PersistedGraveState that = (PersistedGraveState) other;
        return stableTicks == that.stableTicks && activationPermitId == that.activationPermitId
            && activationPermitConnectionEpoch == that.activationPermitConnectionEpoch
            && activationPermitDeathEpoch == that.activationPermitDeathEpoch
            && activationConsumed == that.activationConsumed
            && Objects.equals(graveTileIdentity, that.graveTileIdentity)
            && Objects.equals(gravePosition, that.gravePosition)
            && Objects.equals(ownerIdentity, that.ownerIdentity)
            && Objects.equals(contents, that.contents);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
            graveTileIdentity,
            gravePosition,
            stableTicks,
            ownerIdentity,
            contents,
            activationPermitId,
            activationPermitConnectionEpoch,
            activationPermitDeathEpoch,
            activationConsumed);
    }
}
