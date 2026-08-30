package io.github.kaseyawolf2.horizonwright.core.safety.death;

/** Authorization decision plus the resulting immutable safety snapshot. */
public final class GraveActivationResult {

    private final GraveActivationDecision decision;
    private final DeathSafetyUpdate update;

    GraveActivationResult(GraveActivationDecision decision, DeathSafetyUpdate update) {
        this.decision = decision;
        this.update = update;
    }

    public GraveActivationDecision getDecision() {
        return decision;
    }

    public DeathSafetyUpdate getUpdate() {
        return update;
    }
}
