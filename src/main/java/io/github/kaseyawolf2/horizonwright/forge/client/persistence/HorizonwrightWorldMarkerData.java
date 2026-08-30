package io.github.kaseyawolf2.horizonwright.forge.client.persistence;

import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.WorldSavedData;

/**
 * Horizonwright-owned identity marker stored in an integrated-server save.
 *
 * <p>
 * The loader is deliberately non-throwing. Invalid or newer data is retained as an invalid in-memory marker and
 * is never marked dirty, so merely discovering a save cannot brick world loading or overwrite evidence that needs
 * operator inspection.
 * </p>
 */
public final class HorizonwrightWorldMarkerData extends WorldSavedData {

    public static final String DATA_NAME = "horizonwright_world_identity";
    static final int SCHEMA_VERSION = 1;

    private static final String SCHEMA_KEY = "schemaVersion";
    private static final String MARKER_KEY = "markerId";

    private String markerId;
    private String diagnostic = "marker has not been loaded";
    private boolean valid;

    public HorizonwrightWorldMarkerData(String name) {
        super(name);
    }

    static HorizonwrightWorldMarkerData create(String markerId) {
        HorizonwrightWorldMarkerData marker = new HorizonwrightWorldMarkerData(DATA_NAME);
        marker.markerId = requireUuid(markerId);
        marker.valid = true;
        marker.diagnostic = "singleplayer world marker created";
        marker.markDirty();
        return marker;
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        valid = false;
        markerId = null;
        if (compound == null) {
            diagnostic = "singleplayer world marker contains no data";
            return;
        }
        int schemaVersion = compound.getInteger(SCHEMA_KEY);
        if (schemaVersion != SCHEMA_VERSION) {
            diagnostic = schemaVersion > SCHEMA_VERSION ? "singleplayer world marker uses newer schema " + schemaVersion
                : "singleplayer world marker uses unsupported schema " + schemaVersion;
            return;
        }
        try {
            markerId = requireUuid(compound.getString(MARKER_KEY));
            valid = true;
            diagnostic = "singleplayer world marker loaded";
        } catch (IllegalArgumentException invalidMarker) {
            diagnostic = "singleplayer world marker id is invalid";
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound compound) {
        if (!valid) {
            return;
        }
        compound.setInteger(SCHEMA_KEY, SCHEMA_VERSION);
        compound.setString(MARKER_KEY, markerId);
    }

    public boolean isValidMarker() {
        return valid;
    }

    public String getMarkerId() {
        if (!valid) {
            throw new IllegalStateException(diagnostic);
        }
        return markerId;
    }

    public String getDiagnostic() {
        return diagnostic;
    }

    private static String requireUuid(String value) {
        if (value == null) {
            throw new IllegalArgumentException("marker id must not be null");
        }
        String normalized = UUID.fromString(value.trim())
            .toString();
        if (!normalized.equals(
            value.trim()
                .toLowerCase(java.util.Locale.ROOT))) {
            throw new IllegalArgumentException("marker id must use canonical UUID form");
        }
        return normalized;
    }
}
