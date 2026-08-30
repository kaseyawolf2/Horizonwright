package io.github.kaseyawolf2.horizonwright.forge.client.safety;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Optional;

import net.minecraft.tileentity.TileEntity;

import io.github.kaseyawolf2.horizonwright.core.safety.death.DimensionBlockPosition;
import io.github.kaseyawolf2.horizonwright.core.safety.death.GraveIdentity;

/**
 * Version-isolated decoder for the client-visible fields in OpenBlocks 1.12.18-GTNH graves.
 *
 * <p>
 * The installed build synchronizes {@code getUsername()} and {@code isInventoryEmpty()}, but does not synchronize
 * the grave's inventory contents. This adapter deliberately exposes only evidence the client actually knows.
 */
public final class OpenBlocksGraveTileReader {

    static final String SUPPORTED_TILE_CLASS = "openblocks.common.tileentity.TileEntityGrave";
    private static final String IDENTITY_VERSION = "openblocks-grave-v1";

    private final String supportedTileClass;

    public OpenBlocksGraveTileReader() {
        this(SUPPORTED_TILE_CLASS);
    }

    OpenBlocksGraveTileReader(String supportedTileClass) {
        if (supportedTileClass == null || supportedTileClass.trim()
            .isEmpty()) {
            throw new IllegalArgumentException("supported tile class must not be empty");
        }
        this.supportedTileClass = supportedTileClass;
    }

    public Optional<OpenBlocksGraveTileEvidence> read(TileEntity tile, DimensionBlockPosition position) {
        if (position == null) {
            throw new IllegalArgumentException("grave position must not be null");
        }
        if (tile == null || !supportedTileClass.equals(
            tile.getClass()
                .getName())) {
            return Optional.empty();
        }
        try {
            Method getUsername = tile.getClass()
                .getMethod("getUsername");
            Method isInventoryEmpty = tile.getClass()
                .getMethod("isInventoryEmpty");
            Object rawUsername = getUsername.invoke(tile);
            Object rawEmpty = isInventoryEmpty.invoke(tile);
            if (!(rawUsername instanceof String) || !(rawEmpty instanceof Boolean)) {
                throw new IllegalStateException("OpenBlocks grave accessors returned unsupported values");
            }
            String username = ((String) rawUsername).trim();
            if (username.isEmpty()) {
                throw new IllegalStateException("OpenBlocks grave owner is unavailable");
            }
            GraveIdentity identity = new GraveIdentity(stableIdentity(username), position);
            return Optional.of(new OpenBlocksGraveTileEvidence(identity, username, (Boolean) rawEmpty));
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException failure) {
            throw new IllegalStateException(
                "OpenBlocks 1.12.18-GTNH grave API does not match the tested adapter",
                failure);
        }
    }

    private static String stableIdentity(String username) {
        return IDENTITY_VERSION + ':' + username.length() + ':' + username;
    }
}
