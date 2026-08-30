package io.github.kaseyawolf2.horizonwright.forge.client.safety;

import io.github.kaseyawolf2.horizonwright.core.safety.death.GraveProtectionPolicy;

/** OpenBlocks grave blacklist shared by mining, use, combat targeting, and scavenging boundaries. */
public final class OpenBlocksGraveActionProtection implements GraveActionProtection {

    @Override
    public boolean allowsGenericAction(String blockRegistryName, GraveActionKind actionKind) {
        if (actionKind == null) {
            throw new IllegalArgumentException("actionKind must not be null");
        }
        switch (actionKind) {
            case MINING:
                return GraveProtectionPolicy.allowsGenericMining(blockRegistryName);
            case USE:
                return GraveProtectionPolicy.allowsGenericUse(blockRegistryName);
            case COMBAT_TARGET:
                return GraveProtectionPolicy.allowsGenericCombatTarget(blockRegistryName);
            case SCAVENGE:
                return GraveProtectionPolicy.allowsGenericScavenging(blockRegistryName);
            default:
                throw new IllegalStateException("unhandled grave action kind " + actionKind);
        }
    }
}
