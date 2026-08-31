package io.github.kaseyawolf2.horizonwright.forge.client.safety;

/** Minecraft/OpenBlocks adapter implemented by loaded-world tile-entity inspection. */
public interface GraveScanner {

    GraveRegionScan scanRegion(GraveScanRequest request);

    GraveInspection inspectExact(GraveInspectionRequest request);
}
