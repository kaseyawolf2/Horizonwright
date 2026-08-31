package io.github.kaseyawolf2.horizonwright.forge.client.safety;

import java.util.List;

import net.minecraft.tileentity.TileEntity;

/** Minimal loaded-world surface needed by bounded grave discovery. */
interface LoadedGraveWorldView {

    void requireClientThread();

    int getDimensionId();

    boolean isChunkLoaded(int chunkX, int chunkZ);

    List<TileEntity> getLoadedTileEntities();

    TileEntity getTileEntity(int x, int y, int z);
}
