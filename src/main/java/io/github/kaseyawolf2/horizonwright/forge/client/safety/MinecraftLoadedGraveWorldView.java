package io.github.kaseyawolf2.horizonwright.forge.client.safety;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.tileentity.TileEntity;

import io.github.kaseyawolf2.horizonwright.forge.client.MinecraftRuntimeAccess;

/** Minecraft-thread adapter over only currently loaded chunks and tile entities. */
final class MinecraftLoadedGraveWorldView implements LoadedGraveWorldView {

    private final Minecraft minecraft;

    MinecraftLoadedGraveWorldView(Minecraft minecraft) {
        if (minecraft == null) {
            throw new IllegalArgumentException("minecraft must not be null");
        }
        this.minecraft = minecraft;
    }

    @Override
    public void requireClientThread() {
        if (!minecraft.func_152345_ab() || minecraft.theWorld == null) {
            throw new IllegalStateException("grave discovery requires a loaded world on the Minecraft client thread");
        }
    }

    @Override
    public int getDimensionId() {
        requireClientThread();
        return minecraft.theWorld.provider.dimensionId;
    }

    @Override
    public boolean isChunkLoaded(int chunkX, int chunkZ) {
        requireClientThread();
        return MinecraftRuntimeAccess.chunkProvider(minecraft.theWorld)
            .chunkExists(chunkX, chunkZ);
    }

    @Override
    public List<TileEntity> getLoadedTileEntities() {
        requireClientThread();
        List<TileEntity> snapshot = new ArrayList<TileEntity>();
        for (Object value : minecraft.theWorld.loadedTileEntityList) {
            if (value instanceof TileEntity) {
                snapshot.add((TileEntity) value);
            }
        }
        return snapshot;
    }

    @Override
    public TileEntity getTileEntity(int x, int y, int z) {
        requireClientThread();
        return MinecraftRuntimeAccess.tileEntity(minecraft.theWorld, x, y, z);
    }
}
