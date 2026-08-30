package io.github.kaseyawolf2.horizonwright.core.excavation;

import java.util.Objects;

/** Approved materials and deterministic managed-quarry cadence. */
public final class ManagedQuarryConfiguration {

    private static final ManagedQuarryConfiguration DEFAULTS = new ManagedQuarryConfiguration(
        "minecraft:cobblestone",
        "minecraft:torch",
        "minecraft:cobblestone",
        4);

    private final String rampMaterial;
    private final String lightMaterial;
    private final String fluidFillerMaterial;
    private final int lightLayerInterval;

    public ManagedQuarryConfiguration(String rampMaterial, String lightMaterial, String fluidFillerMaterial,
        int lightLayerInterval) {
        this.rampMaterial = requireMaterial(rampMaterial, "rampMaterial");
        this.lightMaterial = requireMaterial(lightMaterial, "lightMaterial");
        this.fluidFillerMaterial = requireMaterial(fluidFillerMaterial, "fluidFillerMaterial");
        if (lightLayerInterval < 1 || lightLayerInterval > 64) {
            throw new IllegalArgumentException("lightLayerInterval must be between 1 and 64");
        }
        this.lightLayerInterval = lightLayerInterval;
    }

    public static ManagedQuarryConfiguration defaults() {
        return DEFAULTS;
    }

    public String getRampMaterial() {
        return rampMaterial;
    }

    public String getLightMaterial() {
        return lightMaterial;
    }

    public String getFluidFillerMaterial() {
        return fluidFillerMaterial;
    }

    public int getLightLayerInterval() {
        return lightLayerInterval;
    }

    private static String requireMaterial(String value, String name) {
        if (value == null || value.trim()
            .isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ManagedQuarryConfiguration)) {
            return false;
        }
        ManagedQuarryConfiguration that = (ManagedQuarryConfiguration) other;
        return lightLayerInterval == that.lightLayerInterval && rampMaterial.equals(that.rampMaterial)
            && lightMaterial.equals(that.lightMaterial)
            && fluidFillerMaterial.equals(that.fluidFillerMaterial);
    }

    @Override
    public int hashCode() {
        return Objects.hash(rampMaterial, lightMaterial, fluidFillerMaterial, lightLayerInterval);
    }
}
