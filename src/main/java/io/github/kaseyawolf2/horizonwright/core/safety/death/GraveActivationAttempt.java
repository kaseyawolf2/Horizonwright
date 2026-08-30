package io.github.kaseyawolf2.horizonwright.core.safety.death;

/** Packet-boundary evidence for the narrowly scoped grave activation. */
public final class GraveActivationAttempt {

    private final long permitId;
    private final long deathEpoch;
    private final GraveIdentity target;
    private final boolean emptyHand;
    private final boolean sneaking;

    public GraveActivationAttempt(long permitId, long deathEpoch, GraveIdentity target, boolean emptyHand,
        boolean sneaking) {
        if (permitId <= 0L || deathEpoch <= 0L || target == null) {
            throw new IllegalArgumentException("permitId, deathEpoch, and target must identify an activation");
        }
        this.permitId = permitId;
        this.deathEpoch = deathEpoch;
        this.target = target;
        this.emptyHand = emptyHand;
        this.sneaking = sneaking;
    }

    public long getPermitId() {
        return permitId;
    }

    public long getDeathEpoch() {
        return deathEpoch;
    }

    public GraveIdentity getTarget() {
        return target;
    }

    public boolean isEmptyHand() {
        return emptyHand;
    }

    public boolean isSneaking() {
        return sneaking;
    }
}
