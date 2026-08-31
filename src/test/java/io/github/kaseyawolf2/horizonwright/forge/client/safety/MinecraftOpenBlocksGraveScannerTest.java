package io.github.kaseyawolf2.horizonwright.forge.client.safety;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.tileentity.TileEntity;

import org.junit.Test;

import io.github.kaseyawolf2.horizonwright.core.safety.death.DimensionBlockPosition;
import io.github.kaseyawolf2.horizonwright.core.safety.death.GraveCandidate;
import io.github.kaseyawolf2.horizonwright.core.safety.death.GraveIdentity;
import io.github.kaseyawolf2.horizonwright.core.safety.death.GraveResolution;
import io.github.kaseyawolf2.horizonwright.core.safety.death.GraveSearchStatus;
import io.github.kaseyawolf2.horizonwright.core.safety.death.InventoryManifest;
import io.github.kaseyawolf2.horizonwright.core.safety.death.InventoryStack;

public class MinecraftOpenBlocksGraveScannerTest {

    private static final DimensionBlockPosition DEATH = new DimensionBlockPosition(7, 15, 64, 15);
    private static final InventoryManifest CONTENTS = new InventoryManifest(
        36,
        Arrays.asList(new InventoryStack("minecraft:diamond|meta=0|nbt=none", 3, 64)));

    @Test
    public void completeLoadedRegionReturnsBoundedOwnedAndForeignGraves() {
        FakeWorld world = new FakeWorld(7);
        world.loadChunk(0, 0);
        world.loadChunk(0, 1);
        world.loadChunk(1, 0);
        world.loadChunk(1, 1);
        world.add(new FakeGraveTile("Kasey", false), 16, 64, 15);
        world.add(new FakeGraveTile("Alex", false), 15, 64, 16);
        world.add(new FakeGraveTile("Kasey", false), 30, 64, 30);
        MinecraftOpenBlocksGraveScanner scanner = scanner(world);

        GraveRegionScan result = scanner.scanRegion(request());

        assertEquals(GraveSearchStatus.COMPLETE, result.getStatus());
        assertEquals(
            2,
            result.getCandidates()
                .size());
        GraveCandidate owned = candidateOwnedBy(result, "old-player-incarnation");
        assertSame(CONTENTS, owned.getContents());
        assertEquals(
            new DimensionBlockPosition(7, 16, 64, 15),
            owned.getIdentity()
                .getPosition());
        assertTrue(candidateOwnedBy(result, "openblocks-owner-v1:4:Alex") != null);
    }

    @Test
    public void anyMissingIntersectingChunkMakesTheWholeScanUnloaded() {
        FakeWorld world = new FakeWorld(7);
        world.loadChunk(0, 0);
        world.loadChunk(0, 1);
        world.loadChunk(1, 0);
        world.add(new FakeGraveTile("Kasey", false), 16, 64, 15);

        GraveRegionScan result = scanner(world).scanRegion(request());

        assertEquals(GraveSearchStatus.REGION_UNLOADED, result.getStatus());
        assertTrue(
            result.getCandidates()
                .isEmpty());
    }

    @Test
    public void nonemptyOwnedGraveWithNoReconstructableContentsIsUnavailable() {
        FakeWorld world = new FakeWorld(7);
        world.loadChunk(0, 0);
        world.loadChunk(0, 1);
        world.loadChunk(1, 0);
        world.loadChunk(1, 1);
        world.add(new FakeGraveTile("Kasey", false), 16, 64, 15);
        GraveScanRequest request = new GraveScanRequest(
            DEATH,
            2,
            "old-player-incarnation",
            "Kasey",
            InventoryManifest.empty(36));

        GraveRegionScan result = scanner(world).scanRegion(request);

        assertEquals(GraveSearchStatus.EVIDENCE_UNAVAILABLE, result.getStatus());
        assertTrue(
            result.getCandidates()
                .isEmpty());
    }

    @Test
    public void exactInspectionDistinguishesPresentEmptyRemovedAndUnloaded() {
        FakeWorld world = new FakeWorld(7);
        world.loadChunk(1, 0);
        FakeGraveTile grave = new FakeGraveTile("Kasey", false);
        world.add(grave, 16, 64, 15);
        MinecraftOpenBlocksGraveScanner scanner = scanner(world);
        GraveIdentity identity = new OpenBlocksGraveTileReader(FakeGraveTile.class.getName())
            .read(grave, new DimensionBlockPosition(7, 16, 64, 15))
            .get()
            .getIdentity();
        GraveInspectionRequest request = new GraveInspectionRequest(
            identity,
            "old-player-incarnation",
            "Kasey",
            CONTENTS);

        GraveInspection present = scanner.inspectExact(request);
        assertEquals(GraveResolution.PRESENT, present.getResolution());
        assertSame(
            CONTENTS,
            present.getCandidate()
                .getContents());

        world.replace(new FakeGraveTile("Kasey", true), 16, 64, 15);
        assertEquals(
            GraveResolution.EMPTY,
            scanner.inspectExact(request)
                .getResolution());

        world.replace(new TileEntity(), 16, 64, 15);
        assertEquals(
            GraveResolution.REMOVED,
            scanner.inspectExact(request)
                .getResolution());

        world.unloadChunk(1, 0);
        assertEquals(
            GraveResolution.REGION_UNLOADED,
            scanner.inspectExact(request)
                .getResolution());
    }

    private static GraveCandidate candidateOwnedBy(GraveRegionScan scan, String owner) {
        for (GraveCandidate candidate : scan.getCandidates()) {
            if (owner.equals(candidate.getOwnerIdentity())) {
                return candidate;
            }
        }
        throw new AssertionError("missing candidate owned by " + owner);
    }

    private static GraveScanRequest request() {
        return new GraveScanRequest(DEATH, 2, "old-player-incarnation", "Kasey", CONTENTS);
    }

    private static MinecraftOpenBlocksGraveScanner scanner(FakeWorld world) {
        return new MinecraftOpenBlocksGraveScanner(world, new OpenBlocksGraveTileReader(FakeGraveTile.class.getName()));
    }

    public static final class FakeGraveTile extends TileEntity {

        private final String username;
        private final boolean empty;

        FakeGraveTile(String username, boolean empty) {
            this.username = username;
            this.empty = empty;
        }

        public String getUsername() {
            return username;
        }

        public boolean isInventoryEmpty() {
            return empty;
        }
    }

    private static final class FakeWorld implements LoadedGraveWorldView {

        private final int dimension;
        private final Set<String> loadedChunks = new HashSet<String>();
        private final List<TileEntity> tiles = new ArrayList<TileEntity>();

        private FakeWorld(int dimension) {
            this.dimension = dimension;
        }

        private void loadChunk(int x, int z) {
            loadedChunks.add(x + ":" + z);
        }

        private void unloadChunk(int x, int z) {
            loadedChunks.remove(x + ":" + z);
        }

        private void add(TileEntity tile, int x, int y, int z) {
            tile.xCoord = x;
            tile.yCoord = y;
            tile.zCoord = z;
            tiles.add(tile);
        }

        private void replace(TileEntity tile, int x, int y, int z) {
            TileEntity previous = getTileEntity(x, y, z);
            tiles.remove(previous);
            add(tile, x, y, z);
        }

        @Override
        public void requireClientThread() {}

        @Override
        public int getDimensionId() {
            return dimension;
        }

        @Override
        public boolean isChunkLoaded(int chunkX, int chunkZ) {
            return loadedChunks.contains(chunkX + ":" + chunkZ);
        }

        @Override
        public List<TileEntity> getLoadedTileEntities() {
            return new ArrayList<TileEntity>(tiles);
        }

        @Override
        public TileEntity getTileEntity(int x, int y, int z) {
            for (TileEntity tile : tiles) {
                if (tile.xCoord == x && tile.yCoord == y && tile.zCoord == z) {
                    return tile;
                }
            }
            return null;
        }
    }
}
