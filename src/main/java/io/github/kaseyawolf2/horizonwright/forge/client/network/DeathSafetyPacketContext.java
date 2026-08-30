package io.github.kaseyawolf2.horizonwright.forge.client.network;

import java.util.Optional;

import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;

import io.github.kaseyawolf2.horizonwright.core.safety.death.GraveActivationAttempt;

/** Live values and exact-grave matching supplied by the client composition root. */
public interface DeathSafetyPacketContext {

    long getClientTick();

    double getMaximumHealth();

    long getActiveDeathEpoch();

    Optional<GraveActivationAttempt> matchGraveActivation(C08PacketPlayerBlockPlacement packet);

    void onBoundaryUnavailable(boolean transportClosed);
}
