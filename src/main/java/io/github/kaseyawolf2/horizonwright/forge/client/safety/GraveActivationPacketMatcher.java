package io.github.kaseyawolf2.horizonwright.forge.client.safety;

import java.util.Optional;

import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;

import io.github.kaseyawolf2.horizonwright.core.safety.death.DimensionBlockPosition;
import io.github.kaseyawolf2.horizonwright.core.safety.death.GraveActivationAttempt;
import io.github.kaseyawolf2.horizonwright.core.safety.death.GraveActivationPermit;

/** Pure exact-coordinate matcher over immutable evidence captured on the Minecraft thread. */
public final class GraveActivationPacketMatcher {

    public Optional<GraveActivationAttempt> match(C08PacketPlayerBlockPlacement packet,
        GraveActivationPacketSnapshot snapshot) {
        if (packet == null) {
            throw new IllegalArgumentException("grave activation packet must not be null");
        }
        if (snapshot == null || !snapshot.isEmptyHand() || !snapshot.isSneaking() || packet.func_149574_g() != null) {
            return Optional.empty();
        }
        GraveActivationPermit permit = snapshot.getPermit();
        if (!permit.getGraveIdentity()
            .equals(snapshot.getInspectedTarget())) {
            return Optional.empty();
        }
        DimensionBlockPosition position = permit.getGraveIdentity()
            .getPosition();
        if (snapshot.getCurrentDimensionId() != position.getDimensionId() || packet.func_149576_c() != position.getX()
            || packet.func_149571_d() != position.getY()
            || packet.func_149570_e() != position.getZ()) {
            return Optional.empty();
        }
        return Optional.of(
            new GraveActivationAttempt(
                permit.getPermitId(),
                permit.getDeathEpoch(),
                permit.getGraveIdentity(),
                true,
                true));
    }
}
