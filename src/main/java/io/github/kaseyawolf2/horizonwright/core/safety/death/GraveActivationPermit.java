package io.github.kaseyawolf2.horizonwright.core.safety.death;

/** One-use authorization bound to one connection, death epoch, and exact grave. */
public final class GraveActivationPermit {

    private final long permitId;
    private final long connectionEpoch;
    private final long deathEpoch;
    private final GraveIdentity graveIdentity;

    GraveActivationPermit(long permitId, long connectionEpoch, long deathEpoch, GraveIdentity graveIdentity) {
        this.permitId = permitId;
        this.connectionEpoch = connectionEpoch;
        this.deathEpoch = deathEpoch;
        this.graveIdentity = graveIdentity;
    }

    public long getPermitId() {
        return permitId;
    }

    public long getConnectionEpoch() {
        return connectionEpoch;
    }

    public long getDeathEpoch() {
        return deathEpoch;
    }

    public GraveIdentity getGraveIdentity() {
        return graveIdentity;
    }
}
