package io.github.kaseyawolf2.horizonwright.forge.client.safety;

import io.github.kaseyawolf2.horizonwright.core.safety.death.GraveIdentity;

/** Minecraft/OpenBlocks adapter implemented by loaded-world tile-entity inspection. */
public interface GraveScanner {

    GraveRegionScan scanRegion(GraveScanRequest request);

    GraveInspection inspectExact(GraveIdentity identity);
}
