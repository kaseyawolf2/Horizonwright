package io.github.kaseyawolf2.horizonwright.core.safety.death;

/** Immutable movement-only request toward the recorded death location. */
public final class RecoveryNavigationRequest {

    private final long deathEpoch;
    private final DimensionBlockPosition target;
    private final int arrivalTolerance;

    RecoveryNavigationRequest(long deathEpoch, DimensionBlockPosition target, int arrivalTolerance) {
        if (arrivalTolerance < 0 || arrivalTolerance > 8) {
            throw new IllegalArgumentException("recovery arrival tolerance must be between zero and eight");
        }
        this.deathEpoch = deathEpoch;
        this.target = target;
        this.arrivalTolerance = arrivalTolerance;
    }

    public long getDeathEpoch() {
        return deathEpoch;
    }

    public DimensionBlockPosition getTarget() {
        return target;
    }

    public int getArrivalTolerance() {
        return arrivalTolerance;
    }

    public boolean areGenericInteractionsAllowed() {
        return false;
    }

    public boolean allowsRegisteredRouteAction(boolean narrowlyScoped, String targetRegistryName) {
        return narrowlyScoped && !GraveProtectionPolicy.isOpenBlocksGrave(targetRegistryName);
    }
}
