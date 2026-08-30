package io.github.kaseyawolf2.horizonwright.core.safety.death;

import java.util.Locale;

/** Permanent generic-action blacklist for the OpenBlocks grave block. */
public final class GraveProtectionPolicy {

    public static final String OPENBLOCKS_GRAVE = "OpenBlocks:grave";

    private GraveProtectionPolicy() {}

    public static boolean isOpenBlocksGrave(String registryName) {
        return registryName != null && OPENBLOCKS_GRAVE.toLowerCase(Locale.ROOT)
            .equals(
                registryName.trim()
                    .toLowerCase(Locale.ROOT));
    }

    public static boolean allowsGenericMining(String registryName) {
        return !isOpenBlocksGrave(registryName);
    }

    public static boolean allowsGenericUse(String registryName) {
        return !isOpenBlocksGrave(registryName);
    }

    public static boolean allowsGenericCombatTarget(String registryName) {
        return !isOpenBlocksGrave(registryName);
    }

    public static boolean allowsGenericScavenging(String registryName) {
        return !isOpenBlocksGrave(registryName);
    }
}
