package io.github.kaseyawolf2.horizonwright.forge.client.excavation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import io.github.kaseyawolf2.horizonwright.core.excavation.BlockPosition;

/** Bounded, in-volume log component associated with one observed leaf block. */
final class TreeLogRecoveryPlan {

    private final BlockPosition leaf;
    private final BlockPosition rootLog;
    private final List<BlockPosition> logsTopDown;

    TreeLogRecoveryPlan(BlockPosition leaf, BlockPosition rootLog, List<BlockPosition> logsTopDown) {
        this.leaf = Objects.requireNonNull(leaf, "leaf");
        this.rootLog = Objects.requireNonNull(rootLog, "rootLog");
        if (logsTopDown == null || logsTopDown.isEmpty()) {
            throw new IllegalArgumentException("a tree recovery plan requires at least one log");
        }
        this.logsTopDown = Collections.unmodifiableList(new ArrayList<>(logsTopDown));
    }

    BlockPosition getLeaf() {
        return leaf;
    }

    BlockPosition getRootLog() {
        return rootLog;
    }

    List<BlockPosition> getLogsTopDown() {
        return logsTopDown;
    }
}
