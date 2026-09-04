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
    private final List<BlockPosition> logsBottomUp;

    TreeLogRecoveryPlan(BlockPosition leaf, BlockPosition rootLog, List<BlockPosition> logsBottomUp) {
        this.leaf = Objects.requireNonNull(leaf, "leaf");
        this.rootLog = Objects.requireNonNull(rootLog, "rootLog");
        if (logsBottomUp == null || logsBottomUp.isEmpty()) {
            throw new IllegalArgumentException("a tree recovery plan requires at least one log");
        }
        this.logsBottomUp = Collections.unmodifiableList(new ArrayList<>(logsBottomUp));
    }

    BlockPosition getLeaf() {
        return leaf;
    }

    BlockPosition getRootLog() {
        return rootLog;
    }

    List<BlockPosition> getLogsBottomUp() {
        return logsBottomUp;
    }
}
