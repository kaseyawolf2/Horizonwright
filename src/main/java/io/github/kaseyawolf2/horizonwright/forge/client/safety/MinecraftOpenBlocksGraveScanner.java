package io.github.kaseyawolf2.horizonwright.forge.client.safety;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import net.minecraft.client.Minecraft;
import net.minecraft.tileentity.TileEntity;

import io.github.kaseyawolf2.horizonwright.core.safety.death.DimensionBlockPosition;
import io.github.kaseyawolf2.horizonwright.core.safety.death.GraveCandidate;
import io.github.kaseyawolf2.horizonwright.core.safety.death.GraveIdentity;
import io.github.kaseyawolf2.horizonwright.core.safety.death.GraveResolution;
import io.github.kaseyawolf2.horizonwright.core.safety.death.GraveSearchStatus;
import io.github.kaseyawolf2.horizonwright.core.safety.death.InventoryManifest;

/** Bounded OpenBlocks grave discovery over loaded client chunks only. */
public final class MinecraftOpenBlocksGraveScanner implements GraveScanner {

    private static final String FOREIGN_OWNER_PREFIX = "openblocks-owner-v1";

    private final LoadedGraveWorldView world;
    private final OpenBlocksGraveTileReader reader;

    public MinecraftOpenBlocksGraveScanner(Minecraft minecraft) {
        this(new MinecraftLoadedGraveWorldView(minecraft), new OpenBlocksGraveTileReader());
    }

    MinecraftOpenBlocksGraveScanner(LoadedGraveWorldView world, OpenBlocksGraveTileReader reader) {
        if (world == null || reader == null) {
            throw new IllegalArgumentException("grave scanner dependencies must not be null");
        }
        this.world = world;
        this.reader = reader;
    }

    @Override
    public GraveRegionScan scanRegion(GraveScanRequest request) {
        if (request == null || !request.hasRecoveryEvidence()) {
            throw new IllegalArgumentException("grave scan requires complete recovery evidence");
        }
        world.requireClientThread();
        if (world.getDimensionId() != request.getDeathPosition()
            .getDimensionId() || !isRegionLoaded(request.getDeathPosition(), request.getRadius())) {
            return new GraveRegionScan(GraveSearchStatus.REGION_UNLOADED, Collections.<GraveCandidate>emptyList());
        }
        List<GraveCandidate> candidates = new ArrayList<GraveCandidate>();
        for (TileEntity tile : world.getLoadedTileEntities()) {
            DimensionBlockPosition position = position(tile);
            if (!position.isWithinRadius(request.getDeathPosition(), request.getRadius())) {
                continue;
            }
            Optional<OpenBlocksGraveTileEvidence> evidence = reader.read(tile, position);
            if (evidence.isPresent()) {
                if (!evidence.get()
                    .isInventoryEmpty() && request.getExpectedOwnerUsername()
                        .equals(
                            evidence.get()
                                .getOwnerUsername())
                    && request.getConservativeExpectedContents()
                        .isEmpty()) {
                    return new GraveRegionScan(
                        GraveSearchStatus.EVIDENCE_UNAVAILABLE,
                        Collections.<GraveCandidate>emptyList());
                }
                candidates.add(candidate(evidence.get(), request));
            }
        }
        return new GraveRegionScan(GraveSearchStatus.COMPLETE, candidates);
    }

    @Override
    public GraveInspection inspectExact(GraveInspectionRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("grave inspection request must not be null");
        }
        world.requireClientThread();
        GraveIdentity expected = request.getIdentity();
        DimensionBlockPosition position = expected.getPosition();
        if (world.getDimensionId() != position.getDimensionId()
            || !world.isChunkLoaded(chunk(position.getX()), chunk(position.getZ()))) {
            return new GraveInspection(GraveResolution.REGION_UNLOADED, null);
        }
        Optional<OpenBlocksGraveTileEvidence> evidence = reader
            .read(world.getTileEntity(position.getX(), position.getY(), position.getZ()), position);
        if (!evidence.isPresent() || !expected.equals(
            evidence.get()
                .getIdentity())) {
            return new GraveInspection(GraveResolution.REMOVED, null);
        }
        if (evidence.get()
            .isInventoryEmpty()) {
            return new GraveInspection(GraveResolution.EMPTY, null);
        }
        return new GraveInspection(GraveResolution.PRESENT, candidate(evidence.get(), request));
    }

    private boolean isRegionLoaded(DimensionBlockPosition center, int radius) {
        int minimumChunkX = chunk(center.getX() - radius);
        int maximumChunkX = chunk(center.getX() + radius);
        int minimumChunkZ = chunk(center.getZ() - radius);
        int maximumChunkZ = chunk(center.getZ() + radius);
        for (int chunkX = minimumChunkX; chunkX <= maximumChunkX; chunkX++) {
            for (int chunkZ = minimumChunkZ; chunkZ <= maximumChunkZ; chunkZ++) {
                if (!world.isChunkLoaded(chunkX, chunkZ)) {
                    return false;
                }
            }
        }
        return true;
    }

    private DimensionBlockPosition position(TileEntity tile) {
        return new DimensionBlockPosition(world.getDimensionId(), tile.xCoord, tile.yCoord, tile.zCoord);
    }

    private static GraveCandidate candidate(OpenBlocksGraveTileEvidence evidence, GraveScanRequest request) {
        boolean expectedOwner = request.getExpectedOwnerUsername()
            .equals(evidence.getOwnerUsername());
        String ownerIdentity = expectedOwner ? request.getExpectedOwnerIdentity()
            : foreignOwnerIdentity(evidence.getOwnerUsername());
        InventoryManifest contents = evidence.isInventoryEmpty() ? InventoryManifest.empty(
            request.getConservativeExpectedContents()
                .getSlotCount())
            : request.getConservativeExpectedContents();
        return new GraveCandidate(evidence.getIdentity(), ownerIdentity, contents);
    }

    private static GraveCandidate candidate(OpenBlocksGraveTileEvidence evidence, GraveInspectionRequest request) {
        boolean expectedOwner = request.getExpectedOwnerUsername()
            .equals(evidence.getOwnerUsername());
        String ownerIdentity = expectedOwner ? request.getExpectedOwnerIdentity()
            : foreignOwnerIdentity(evidence.getOwnerUsername());
        return new GraveCandidate(evidence.getIdentity(), ownerIdentity, request.getConservativeExpectedContents());
    }

    private static String foreignOwnerIdentity(String username) {
        return FOREIGN_OWNER_PREFIX + ':' + username.length() + ':' + username;
    }

    private static int chunk(int blockCoordinate) {
        return blockCoordinate >> 4;
    }
}
