package io.github.kaseyawolf2.horizonwright.forge.client.excavation;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import io.github.kaseyawolf2.horizonwright.core.excavation.BlockPosition;
import io.github.kaseyawolf2.horizonwright.core.excavation.CylinderExcavationSpec;

/** Finds a leaf's nearby connected log component without reading or loading blocks outside the supplied cache view. */
final class TreeLogRecoveryPlanner {

    interface BlockLookup {

        Kind kindAt(BlockPosition position);
    }

    enum Kind {
        UNAVAILABLE,
        OTHER,
        LEAF,
        WOOD
    }

    private static final int MAX_LEAF_DISTANCE = 6;
    private static final int MAX_SEARCHED_BLOCKS = 16_384;
    private static final int MAX_CONNECTED_LOGS = 4_096;
    private static final int[][] DIRECTIONS = { { 0, 1, 0 }, { 0, -1, 0 }, { 1, 0, 0 }, { -1, 0, 0 }, { 0, 0, 1 },
        { 0, 0, -1 } };

    private TreeLogRecoveryPlanner() {}

    static Optional<TreeLogRecoveryPlan> plan(CylinderExcavationSpec area, BlockPosition leaf, BlockLookup lookup) {
        if (area == null || leaf == null || lookup == null) {
            throw new IllegalArgumentException("area, leaf, and lookup are required");
        }
        if (!area.contains(leaf) || lookup.kindAt(leaf) != Kind.LEAF) return Optional.empty();

        Set<BlockPosition> visitedLeaves = new HashSet<>();
        Set<BlockPosition> logSeeds = new LinkedHashSet<>();
        ArrayDeque<LeafSearchNode> leaves = new ArrayDeque<>();
        visitedLeaves.add(leaf);
        leaves.addLast(new LeafSearchNode(leaf, 0));
        int searched = 0;
        while (!leaves.isEmpty() && searched < MAX_SEARCHED_BLOCKS) {
            LeafSearchNode current = leaves.removeFirst();
            searched++;
            for (BlockPosition neighbor : neighbors(current.position)) {
                if (!area.contains(neighbor)) continue;
                Kind kind = lookup.kindAt(neighbor);
                if (kind == Kind.WOOD) {
                    logSeeds.add(neighbor);
                } else if (kind == Kind.LEAF && current.distance < MAX_LEAF_DISTANCE && visitedLeaves.add(neighbor)) {
                    leaves.addLast(new LeafSearchNode(neighbor, current.distance + 1));
                }
            }
        }
        if (logSeeds.isEmpty()) return Optional.empty();

        Set<BlockPosition> connectedLogs = new LinkedHashSet<>();
        ArrayDeque<BlockPosition> pendingLogs = new ArrayDeque<>(logSeeds);
        while (!pendingLogs.isEmpty()) {
            BlockPosition current = pendingLogs.removeFirst();
            if (!area.contains(current) || connectedLogs.contains(current) || lookup.kindAt(current) != Kind.WOOD)
                continue;
            connectedLogs.add(current);
            if (connectedLogs.size() > MAX_CONNECTED_LOGS) return Optional.empty();
            for (BlockPosition neighbor : neighbors(current)) {
                if (area.contains(neighbor) && !connectedLogs.contains(neighbor)) pendingLogs.addLast(neighbor);
            }
        }
        if (connectedLogs.isEmpty()) return Optional.empty();

        List<BlockPosition> ordered = new ArrayList<>(connectedLogs);
        ordered.sort(
            Comparator.comparingInt(BlockPosition::getY)
                .thenComparingInt(BlockPosition::getX)
                .thenComparingInt(BlockPosition::getZ));
        BlockPosition root = connectedLogs.stream()
            .min(
                Comparator.comparingInt(BlockPosition::getY)
                    .thenComparingInt(BlockPosition::getX)
                    .thenComparingInt(BlockPosition::getZ))
            .get();
        return Optional.of(new TreeLogRecoveryPlan(leaf, root, ordered));
    }

    private static List<BlockPosition> neighbors(BlockPosition position) {
        List<BlockPosition> neighbors = new ArrayList<>(DIRECTIONS.length);
        for (int[] direction : DIRECTIONS) {
            neighbors.add(
                new BlockPosition(
                    position.getX() + direction[0],
                    position.getY() + direction[1],
                    position.getZ() + direction[2]));
        }
        return neighbors;
    }

    private static final class LeafSearchNode {

        private final BlockPosition position;
        private final int distance;

        private LeafSearchNode(BlockPosition position, int distance) {
            this.position = position;
            this.distance = distance;
        }
    }
}
