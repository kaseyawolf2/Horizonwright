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
        return new ExcavationFrontier(
            spec.getGeometryKey(),
            layerY,
            minimumChunkX(spec),
            minimumChunkZ(spec),
            0,
            0,
            false);
    }

    private static int minimumChunkX(CylinderExcavationSpec spec) {
        return Math.floorDiv(spec.getCenterX() - spec.getRadius(), 16);
    }

    private static int maximumChunkX(CylinderExcavationSpec spec) {
        return Math.floorDiv(spec.getCenterX() + spec.getRadius(), 16);
    }

    private static int minimumChunkZ(CylinderExcavationSpec spec) {
        return Math.floorDiv(spec.getCenterZ() - spec.getRadius(), 16);
    }

    private static int maximumChunkZ(CylinderExcavationSpec spec) {
        return Math.floorDiv(spec.getCenterZ() + spec.getRadius(), 16);
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
