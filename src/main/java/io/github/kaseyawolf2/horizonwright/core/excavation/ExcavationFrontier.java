package io.github.kaseyawolf2.horizonwright.core.excavation;

import java.util.Objects;

/** Exact next-block cursor, ordered by layer, chunk, local-X band, then local Z. */
public final class ExcavationFrontier {

    private final String geometryKey;
    private final int layerY;
    private final int chunkX;
    private final int chunkZ;
    private final int band;
    private final int offset;
    private final boolean complete;

    ExcavationFrontier(String geometryKey, int layerY, int chunkX, int chunkZ, int band, int offset, boolean complete) {
        if (geometryKey == null || geometryKey.isEmpty()) {
            throw new IllegalArgumentException("geometryKey must not be blank");
        }
        if (!complete && (band < 0 || band > 15 || offset < 0 || offset > 15)) {
            throw new IllegalArgumentException("band and offset must be local chunk coordinates");
        }
        if (complete && (layerY != 0 || chunkX != 0 || chunkZ != 0 || band != 0 || offset != 0)) {
            throw new IllegalArgumentException("a complete frontier must use the canonical zero sentinel fields");
        }
        this.geometryKey = geometryKey;
        this.layerY = layerY;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.band = band;
        this.offset = offset;
        this.complete = complete;
    }

    static ExcavationFrontier complete(String geometryKey) {
        return new ExcavationFrontier(geometryKey, 0, 0, 0, 0, 0, true);
    }

    /** Reconstructs persisted fields; {@link CylinderExcavationGeometry#validate} must authenticate them. */
    public static ExcavationFrontier restore(String geometryKey, int layerY, int chunkX, int chunkZ, int band,
        int offset, boolean complete) {
        return new ExcavationFrontier(geometryKey, layerY, chunkX, chunkZ, band, offset, complete);
    }

    public String getGeometryKey() {
        return geometryKey;
    }

    public int getLayerY() {
        return layerY;
    }

    public int getChunkX() {
        return chunkX;
    }

    public int getChunkZ() {
        return chunkZ;
    }

    /** The local-X strip within the current chunk. */
    public int getBand() {
        return band;
    }

    /** The local-Z offset within the current band. */
    public int getOffset() {
        return offset;
    }

    public boolean isComplete() {
        return complete;
    }

    public BlockPosition getPosition() {
        if (complete) {
            throw new IllegalStateException("a complete frontier has no next position");
        }
        long x = (long) chunkX * 16L + band;
        long z = (long) chunkZ * 16L + offset;
        return new BlockPosition(Math.toIntExact(x), layerY, Math.toIntExact(z));
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ExcavationFrontier)) {
            return false;
        }
        ExcavationFrontier that = (ExcavationFrontier) other;
        return layerY == that.layerY && chunkX == that.chunkX
            && chunkZ == that.chunkZ
            && band == that.band
            && offset == that.offset
            && complete == that.complete
            && geometryKey.equals(that.geometryKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(geometryKey, layerY, chunkX, chunkZ, band, offset, complete);
    }

    @Override
    public String toString() {
        return complete ? "ExcavationFrontier{complete," + geometryKey + '}'
            : "ExcavationFrontier{" + layerY
                + ",chunk="
                + chunkX
                + ','
                + chunkZ
                + ",band="
                + band
                + ",offset="
                + offset
                + '}';
    }
}
