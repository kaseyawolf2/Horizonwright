package io.github.kaseyawolf2.horizonwright.core.safety.death;

/** Immutable movement-only request toward the recorded death location. */
public final class RecoveryNavigationRequest {

    private final long deathEpoch;
    private final DimensionBlockPosition target;

    RecoveryNavigationRequest(long deathEpoch, DimensionBlockPosition target) {
        this.deathEpoch = deathEpoch;
        this.target = target;
    }

    public long getDeathEpoch() {
        return deathEpoch;
    }

    public DimensionBlockPosition getTarget() {
        return target;
    }

    public boolean areGenericInteractionsAllowed() {
        return false;
    }

    public boolean allowsRegisteredRouteAction(boolean narrowlyScoped, String targetRegistryName) {
        return narrowlyScoped && !GraveProtectionPolicy.isOpenBlocksGrave(targetRegistryName);
    }
}
