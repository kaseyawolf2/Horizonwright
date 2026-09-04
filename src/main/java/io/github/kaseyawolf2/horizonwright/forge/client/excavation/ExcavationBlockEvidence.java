package io.github.kaseyawolf2.horizonwright.forge.client.excavation;

import io.github.kaseyawolf2.horizonwright.core.excavation.BlockPosition;

/** Immutable, Minecraft-independent block evidence captured on the client thread. */
final class ExcavationBlockEvidence {

    private final BlockPosition position;
    private final String fingerprint;
    private final boolean loaded;
    private final boolean air;
    private final boolean fluid;
    private final boolean fluidSource;
    private final boolean protectedGrave;
    private final boolean infrastructure;
    private final boolean foliage;
    private final boolean breakable;

    ExcavationBlockEvidence(BlockPosition position, String fingerprint, boolean loaded, boolean air, boolean fluid,
        boolean fluidSource, boolean protectedGrave, boolean infrastructure, boolean foliage, boolean breakable) {
        if (position == null || fingerprint == null
            || fingerprint.trim()
                .isEmpty()) {
            throw new IllegalArgumentException("position and fingerprint are required");
        }
        if (!loaded && (air || fluid || fluidSource || protectedGrave || infrastructure || foliage || breakable)) {
            throw new IllegalArgumentException("unloaded evidence cannot claim block semantics");
        }
        if (fluidSource && !fluid) throw new IllegalArgumentException("a fluid source must be fluid");
        if (air && (fluid || protectedGrave || infrastructure || foliage || breakable)) {
            throw new IllegalArgumentException("air cannot also carry actionable block semantics");
        }
        if (protectedGrave && infrastructure) {
            throw new IllegalArgumentException("grave and general infrastructure classifications must be distinct");
        }
        this.position = position;
        this.fingerprint = fingerprint.trim();
        this.loaded = loaded;
        this.air = air;
        this.fluid = fluid;
        this.fluidSource = fluidSource;
        this.protectedGrave = protectedGrave;
        this.infrastructure = infrastructure;
        this.foliage = foliage;
        this.breakable = breakable;
    }

    BlockPosition getPosition() {
        return position;
    }

    String getFingerprint() {
        return fingerprint;
    }

    boolean isLoaded() {
        return loaded;
    }

    boolean isAir() {
        return air;
    }

    boolean isFluid() {
        return fluid;
    }

    boolean isFluidSource() {
        return fluidSource;
    }

    boolean isProtectedGrave() {
        return protectedGrave;
    }

    boolean isInfrastructure() {
        return infrastructure;
    }

    boolean isFoliage() {
        return foliage;
    }

    boolean isBreakable() {
        return breakable;
    }
}
