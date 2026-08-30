package io.github.kaseyawolf2.horizonwright.forge.client.safety;

import io.github.kaseyawolf2.horizonwright.core.safety.death.DimensionBlockPosition;

/** Bounded grave scan request around the recorded death location. */
public final class GraveScanRequest {

    private final DimensionBlockPosition deathPosition;
    private final int radius;

    public GraveScanRequest(DimensionBlockPosition deathPosition, int radius) {
        if (deathPosition == null || radius < 0) {
            throw new IllegalArgumentException("deathPosition must not be null and radius must be non-negative");
        }
        this.deathPosition = deathPosition;
        this.radius = radius;
    }

    public DimensionBlockPosition getDeathPosition() {
        return deathPosition;
    }

    public int getRadius() {
        return radius;
    }
}
