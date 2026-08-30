package io.github.kaseyawolf2.horizonwright.forge.client.safety;

import io.github.kaseyawolf2.horizonwright.core.safety.death.GraveActivationPermit;
import io.github.kaseyawolf2.horizonwright.core.safety.death.GraveIdentity;

/** Immutable Minecraft-thread evidence published for the outbound packet thread. */
public final class GraveActivationPacketSnapshot {

    private final GraveActivationPermit permit;
    private final GraveIdentity inspectedTarget;
    private final int currentDimensionId;
    private final boolean emptyHand;
    private final boolean sneaking;

    public GraveActivationPacketSnapshot(GraveActivationPermit permit, GraveIdentity inspectedTarget,
        int currentDimensionId, boolean emptyHand, boolean sneaking) {
        if (permit == null || inspectedTarget == null) {
            throw new IllegalArgumentException("grave permit and inspected target must not be null");
        }
        this.permit = permit;
        this.inspectedTarget = inspectedTarget;
        this.currentDimensionId = currentDimensionId;
        this.emptyHand = emptyHand;
        this.sneaking = sneaking;
    }

    public GraveActivationPermit getPermit() {
        return permit;
    }

    public GraveIdentity getInspectedTarget() {
        return inspectedTarget;
    }

    public int getCurrentDimensionId() {
        return currentDimensionId;
    }

    public boolean isEmptyHand() {
        return emptyHand;
    }

    public boolean isSneaking() {
        return sneaking;
    }
}
