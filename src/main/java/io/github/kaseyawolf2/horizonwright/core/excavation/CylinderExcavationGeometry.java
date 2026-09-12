package io.github.kaseyawolf2.horizonwright.core.excavation;

import java.util.ArrayList;
import java.util.List;

/** Stateless, bounded traversal of cylinder blocks without materializing the volume. */
public final class CylinderExcavationGeometry {

    public static final int MAX_BATCH_SIZE = 4096;

    private CylinderExcavationGeometry() {}

    public static ExcavationFrontier initialFrontier(CylinderExcavationSpec spec) {
        requireSpec(spec);
        return normalize(spec, rawLayerStart(spec, spec.getTopY()));
    }

    /** First canonical position in one layer, used by bounded cleared-layer verification. */
    public static ExcavationFrontier layerStart(CylinderExcavationSpec spec, int layerY) {
        requireSpec(spec);
        if (layerY < spec.getBottomY() || layerY > spec.getTopY()) {
            throw new IllegalArgumentException("layerY is outside the excavation cylinder");
        }
        return normalize(spec, rawLayerStart(spec, layerY));
    }

    /** Canonical frontier whose current position is the supplied in-cylinder block. */
    public static ExcavationFrontier atPosition(CylinderExcavationSpec spec, BlockPosition position) {
        requireSpec(spec);
        if (!spec.contains(position)) {
            throw new IllegalArgumentException("position is outside the excavation cylinder");
        }
        ExcavationFrontier frontier = new ExcavationFrontier(
            spec.getGeometryKey(),
            position.getY(),
            Math.floorDiv(position.getX(), 16),
            Math.floorDiv(position.getZ(), 16),
            Math.floorMod(position.getX(), 16),
            Math.floorMod(position.getZ(), 16),
            false);
        validate(spec, frontier);
        return frontier;
    }

    public static ExcavationTargetBatch nextBatch(CylinderExcavationSpec spec, ExcavationFrontier frontier,
        int maximumTargets) {
        requireSpec(spec);
        requireMaximumTargets(maximumTargets);
        validate(spec, frontier);
        ExcavationFrontier current = frontier;
        List<ExcavationTarget> targets = new ArrayList<>(Math.min(maximumTargets, 256));
        while (!current.isComplete() && targets.size() < maximumTargets) {
            BlockPosition position = current.getPosition();
            ExcavationFrontier next = normalize(spec, advanceRaw(spec, current));
            targets.add(new ExcavationTarget(position, next));
            current = next;
        }
        return new ExcavationTargetBatch(frontier, current, targets);
    }

    public static boolean isFirstTargetOfLayer(CylinderExcavationSpec spec, ExcavationFrontier frontier) {
        requireSpec(spec);
        validate(spec, frontier);
        return !frontier.isComplete() && frontier.equals(normalize(spec, rawLayerStart(spec, frontier.getLayerY())));
    }

    /** Number of volume positions strictly before this frontier in deterministic traversal order. */
    public static long processedBefore(CylinderExcavationSpec spec, ExcavationFrontier frontier) {
        requireSpec(spec);
        validate(spec, frontier);
        if (frontier.isComplete()) {
            return spec.getVolume();
        }
        long completedLayers = (long) spec.getTopY() - frontier.getLayerY();
        long count = Math.multiplyExact(completedLayers, spec.getColumnCount());
        ExcavationFrontier candidate = normalize(spec, rawLayerStart(spec, frontier.getLayerY()));
        while (!candidate.equals(frontier)) {
            count = Math.addExact(count, 1L);
            candidate = normalize(spec, advanceRaw(spec, candidate));
            if (candidate.isComplete() || candidate.getLayerY() != frontier.getLayerY()) {
                throw new IllegalArgumentException("frontier is not reachable in deterministic traversal order");
            }
        }
        return count;
    }

    public static void validate(CylinderExcavationSpec spec, ExcavationFrontier frontier) {
        requireSpec(spec);
        if (frontier == null) {
            throw new IllegalArgumentException("frontier must not be null");
        }
        if (!spec.getGeometryKey()
            .equals(frontier.getGeometryKey())) {
            throw new IllegalArgumentException("frontier belongs to a different excavation geometry");
        }
        if (frontier.isComplete()) {
            return;
        }
        if (frontier.getLayerY() < spec.getBottomY() || frontier.getLayerY() > spec.getTopY()
            || frontier.getChunkX() < minimumChunkX(spec)
            || frontier.getChunkX() > maximumChunkX(spec)
            || frontier.getChunkZ() < minimumChunkZ(spec)
            || frontier.getChunkZ() > maximumChunkZ(spec)
            || !spec.contains(frontier.getPosition())) {
            throw new IllegalArgumentException("frontier is not a canonical target in this cylinder");
        }
    }

    private static ExcavationFrontier normalize(CylinderExcavationSpec spec, ExcavationFrontier raw) {
        ExcavationFrontier candidate = raw;
        while (!candidate.isComplete() && !spec.contains(candidate.getPosition())) {
            candidate = advanceRaw(spec, candidate);
        }
        return candidate;
    }

    private static ExcavationFrontier advanceRaw(CylinderExcavationSpec spec, ExcavationFrontier frontier) {
        if (frontier.isComplete()) {
            return frontier;
        }
        int layerY = frontier.getLayerY();
        if (spec.usesSpiralLookup()) {
            BlockPosition next = spec.circularSpiral()
                .next(spec, frontier.getPosition());
            return next == null ? nextLayer(spec, layerY) : rawPosition(spec, next);
        }
        if (spec.getTraversal() == ExcavationTraversal.ROWS_X || spec.getTraversal() == ExcavationTraversal.ROWS_Z) {
            boolean alongX = spec.getTraversal() == ExcavationTraversal.ROWS_X;
            BlockPosition p = frontier.getPosition();
            int row = alongX ? p.getZ() : p.getX();
            int minRow = alongX ? spec.getMinimumZ() : spec.getMinimumX();
            int maxRow = alongX ? spec.getMaximumZ() : spec.getMaximumX();
            int min = alongX ? spec.getMinimumX() : spec.getMinimumZ();
            int max = alongX ? spec.getMaximumX() : spec.getMaximumZ();
            int column = alongX ? p.getX() : p.getZ();
            int direction = (row - minRow) % 2 == 0 ? 1 : -1;
            if (column + direction < min || column + direction > max) {
                if (++row > maxRow) return nextLayer(spec, layerY);
            } else column += direction;
            return rawPosition(spec, new BlockPosition(alongX ? column : row, layerY, alongX ? row : column));
        }
        if (spec.isSpiral()) {
            BlockPosition next = InwardSpiral.next(spec, frontier.getPosition());
            if (next == null) return layerY == spec.getBottomY() ? ExcavationFrontier.complete(spec.getGeometryKey())
                : rawLayerStart(spec, layerY - 1);
            return rawPosition(spec, next);
        }
        int chunkX = frontier.getChunkX();
        int chunkZ = frontier.getChunkZ();
        int band = frontier.getBand();
        int offset = frontier.getOffset() + 1;
        if (offset > 15) {
            offset = 0;
            band++;
        }
        if (band > 15) {
            band = 0;
            chunkZ++;
        }
        if (chunkZ > maximumChunkZ(spec)) {
            chunkZ = minimumChunkZ(spec);
            chunkX++;
        }
        if (chunkX > maximumChunkX(spec)) {
            if (layerY == spec.getBottomY()) {
                return ExcavationFrontier.complete(spec.getGeometryKey());
            }
            return rawLayerStart(spec, layerY - 1);
        }
        return new ExcavationFrontier(spec.getGeometryKey(), layerY, chunkX, chunkZ, band, offset, false);
    }

    private static ExcavationFrontier rawLayerStart(CylinderExcavationSpec spec, int layerY) {
        if (spec.usesSpiralLookup()) return rawPosition(
            spec,
            spec.circularSpiral()
                .first(spec, layerY));
        if (spec.getTraversal() != ExcavationTraversal.CHUNKS)
            return rawPosition(spec, new BlockPosition(spec.getMinimumX(), layerY, spec.getMinimumZ()));
        return new ExcavationFrontier(
            spec.getGeometryKey(),
            layerY,
            minimumChunkX(spec),
            minimumChunkZ(spec),
            0,
            0,
            false);
    }

    private static ExcavationFrontier nextLayer(CylinderExcavationSpec spec, int y) {
        return y == spec.getBottomY() ? ExcavationFrontier.complete(spec.getGeometryKey()) : rawLayerStart(spec, y - 1);
    }

    private static ExcavationFrontier rawPosition(CylinderExcavationSpec spec, BlockPosition p) {
        return new ExcavationFrontier(
            spec.getGeometryKey(),
            p.getY(),
            Math.floorDiv(p.getX(), 16),
            Math.floorDiv(p.getZ(), 16),
            Math.floorMod(p.getX(), 16),
            Math.floorMod(p.getZ(), 16),
            false);
    }

    private static int minimumChunkX(CylinderExcavationSpec spec) {
        return Math.floorDiv(spec.getMinimumX(), 16);
    }

    private static int maximumChunkX(CylinderExcavationSpec spec) {
        return Math.floorDiv(spec.getMaximumX(), 16);
    }

    private static int minimumChunkZ(CylinderExcavationSpec spec) {
        return Math.floorDiv(spec.getMinimumZ(), 16);
    }

    private static int maximumChunkZ(CylinderExcavationSpec spec) {
        return Math.floorDiv(spec.getMaximumZ(), 16);
    }

    private static void requireMaximumTargets(int maximumTargets) {
        if (maximumTargets < 1 || maximumTargets > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException("maximumTargets must be between 1 and " + MAX_BATCH_SIZE);
        }
    }

    private static void requireSpec(CylinderExcavationSpec spec) {
        if (spec == null) {
            throw new IllegalArgumentException("spec must not be null");
        }
    }
}
