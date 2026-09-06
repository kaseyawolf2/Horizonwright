package io.github.kaseyawolf2.horizonwright.runtime.task;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github.kaseyawolf2.horizonwright.core.base.BasePosition;
import io.github.kaseyawolf2.horizonwright.core.base.NamedArea;
import io.github.kaseyawolf2.horizonwright.core.base.TreeObservation;
import io.github.kaseyawolf2.horizonwright.core.base.TreeObservationState;
import io.github.kaseyawolf2.horizonwright.core.base.TreeWorkCheckpoint;
import io.github.kaseyawolf2.horizonwright.core.base.TreeWorkStage;
import io.github.kaseyawolf2.horizonwright.core.task.TaskCheckpoint;
import io.github.kaseyawolf2.horizonwright.core.task.TaskSpec;

/** Strict persistence bridge for a frozen, bounded tree-farm pass. */
final class TreeTaskCheckpointCodec {

    private TreeTaskCheckpointCodec() {}

    static TaskCheckpoint encode(TaskSpec spec, State state, long revision) {
        if (state == null || revision < state.passRevision) {
            throw new IllegalArgumentException("tree state and a current checkpoint revision are required");
        }
        requireSpec(spec, state.area);
        Map<String, String> values = new LinkedHashMap<>();
        writeArea(values, state.area);
        values.put("passRevision", Long.toString(state.passRevision));
        values.put("checkpointRevision", Long.toString(revision));
        values.put("nextIndex", Integer.toString(state.nextIndex));
        values.put("verifiedTrees", Integer.toString(state.verifiedTrees));
        values.put("treeCount", Integer.toString(state.trees.size()));
        for (int index = 0; index < state.trees.size(); index++)
            writeTree(values, "tree." + index + ".", state.trees.get(index));
        values.put("hasWork", Boolean.toString(state.work != null));
        if (state.work != null) writeWork(values, state.work);
        values.put("plantingPhase", Boolean.toString(state.planting));
        values.put("pendingCount", Integer.toString(state.pending.size()));
        for (int i = 0; i < state.pending.size(); i++) {
            Map<String, String> workValues = new LinkedHashMap<>();
            writeWork(workValues, state.pending.get(i));
            for (Map.Entry<String, String> entry : workValues.entrySet())
                values.put("pending." + i + "." + entry.getKey(), entry.getValue());
        }
        return new TaskCheckpoint(revision, values);
    }

    static State decode(TaskSpec spec, TaskCheckpoint checkpoint) {
        if (checkpoint == null) throw new IllegalArgumentException("task checkpoint is required");
        if (checkpoint.getRevision() == 0L && checkpoint.getValues()
            .isEmpty()) return null;
        Map<String, String> values = checkpoint.getValues();
        NamedArea area = readArea(values);
        requireSpec(spec, area);
        long passRevision = longValue(values, "passRevision");
        if (passRevision < 1L || longValue(values, "checkpointRevision") != checkpoint.getRevision()
            || checkpoint.getRevision() < passRevision) {
            throw new IllegalArgumentException("tree checkpoint revision is inconsistent");
        }
        int count = integer(values, "treeCount");
        if (count < 0 || count > 4096) throw new IllegalArgumentException("invalid bounded tree count");
        List<TreeObservation> trees = new ArrayList<>(count);
        for (int index = 0; index < count; index++) trees.add(readTree(values, "tree." + index + "."));
        int nextIndex = integer(values, "nextIndex");
        int verifiedTrees = integer(values, "verifiedTrees");
        TreeWorkCheckpoint work = bool(values, "hasWork") ? readWork(values, area) : null;
        List<TreeWorkCheckpoint> pending = new ArrayList<>();
        int pendingCount = values.containsKey("pendingCount") ? integer(values, "pendingCount") : 0;
        if (pendingCount < 0 || pendingCount > 4096) throw new IllegalArgumentException("invalid pending tree count");
        for (int i = 0; i < pendingCount; i++) {
            Map<String, String> workValues = new LinkedHashMap<>();
            String prefix = "pending." + i + ".";
            for (Map.Entry<String, String> entry : values.entrySet()) if (entry.getKey()
                .startsWith(prefix))
                workValues.put(
                    entry.getKey()
                        .substring(prefix.length()),
                    entry.getValue());
            pending.add(readWork(workValues, area));
        }
        return new State(
            area,
            passRevision,
            trees,
            nextIndex,
            verifiedTrees,
            work,
            values.containsKey("plantingPhase") && bool(values, "plantingPhase"),
            pending);
    }

    private static void writeArea(Map<String, String> values, NamedArea area) {
        BasePosition min = area.getMinimum();
        BasePosition max = area.getMaximum();
        values.put("area.id", area.getId());
        values.put("area.name", area.getDisplayName());
        values.put(
            "area.kind",
            area.getKind()
                .name());
        if (area.getStorageId() != null) values.put("area.storage", area.getStorageId());
        values.put("area.dimension", Integer.toString(min.getDimensionId()));
        values.put("area.minX", Integer.toString(min.getX()));
        values.put("area.minY", Integer.toString(min.getY()));
        values.put("area.minZ", Integer.toString(min.getZ()));
        values.put("area.maxX", Integer.toString(max.getX()));
        values.put("area.maxY", Integer.toString(max.getY()));
        values.put("area.maxZ", Integer.toString(max.getZ()));
    }

    private static NamedArea readArea(Map<String, String> values) {
        int dimension = integer(values, "area.dimension");
        return new NamedArea(
            text(values, "area.id"),
            text(values, "area.name"),
            new BasePosition(
                dimension,
                integer(values, "area.minX"),
                integer(values, "area.minY"),
                integer(values, "area.minZ")),
            new BasePosition(
                dimension,
                integer(values, "area.maxX"),
                integer(values, "area.maxY"),
                integer(values, "area.maxZ")),
            values.containsKey("area.kind")
                ? enumValue(io.github.kaseyawolf2.horizonwright.core.base.AreaKind.class, values, "area.kind")
                : io.github.kaseyawolf2.horizonwright.core.base.AreaKind.UNASSIGNED,
            values.get("area.storage"));
    }

    private static void writeTree(Map<String, String> values, String prefix, TreeObservation tree) {
        values.put(prefix + "id", tree.getTreeId());
        values.put(prefix + "revision", Long.toString(tree.getRevision()));
        values.put(prefix + "fingerprint", tree.getObservationFingerprint());
        values.put(prefix + "sapling", tree.getRequiredSaplingFingerprint());
        writePosition(values, prefix + "replant.", tree.getReplantPosition());
        values.put(
            prefix + "state",
            tree.getState()
                .name());
        values.put(prefix + "mature", Boolean.toString(tree.isMature()));
        values.put(prefix + "protected", Boolean.toString(tree.isProtectedTree()));
        values.put(
            prefix + "blockCount",
            Integer.toString(
                tree.getTreeBlocks()
                    .size()));
        for (int index = 0; index < tree.getTreeBlocks()
            .size(); index++) {
            writePosition(
                values,
                prefix + "block." + index + ".",
                tree.getTreeBlocks()
                    .get(index));
        }
    }

    private static TreeObservation readTree(Map<String, String> values, String prefix) {
        int count = integer(values, prefix + "blockCount");
        if (count < 0 || count > TreeObservation.MAX_CAPTURED_BLOCKS)
            throw new IllegalArgumentException("invalid captured tree size");
        List<BasePosition> blocks = new ArrayList<>(count);
        for (int index = 0; index < count; index++) blocks.add(readPosition(values, prefix + "block." + index + "."));
        return new TreeObservation(
            text(values, prefix + "id"),
            longValue(values, prefix + "revision"),
            text(values, prefix + "fingerprint"),
            text(values, prefix + "sapling"),
            blocks,
            readPosition(values, prefix + "replant."),
            enumValue(TreeObservationState.class, values, prefix + "state"),
            bool(values, prefix + "mature"),
            bool(values, prefix + "protected"));
    }

    private static void writeWork(Map<String, String> values, TreeWorkCheckpoint work) {
        values.put("work.revision", Long.toString(work.getWorkRevision()));
        values.put("work.treeId", work.getTreeId());
        values.put("work.sapling", work.getRequiredSaplingFingerprint());
        writePosition(values, "work.replant.", work.getReplantPosition());
        values.put("work.expectedRevision", Long.toString(work.getExpectedObservationRevision()));
        values.put("work.expectedFingerprint", work.getExpectedObservationFingerprint());
        values.put(
            "work.stage",
            work.getStage()
                .name());
        values.put(
            "work.blockCount",
            Integer.toString(
                work.getCapturedBlocks()
                    .size()));
        for (int index = 0; index < work.getCapturedBlocks()
            .size(); index++) {
            writePosition(
                values,
                "work.block." + index + ".",
                work.getCapturedBlocks()
                    .get(index));
        }
    }

    private static TreeWorkCheckpoint readWork(Map<String, String> values, NamedArea area) {
        int count = integer(values, "work.blockCount");
        if (count < 0 || count > TreeObservation.MAX_CAPTURED_BLOCKS)
            throw new IllegalArgumentException("invalid tree work payload");
        List<BasePosition> blocks = new ArrayList<>(count);
        for (int index = 0; index < count; index++) blocks.add(readPosition(values, "work.block." + index + "."));
        return TreeWorkCheckpoint.restore(
            area,
            longValue(values, "work.revision"),
            text(values, "work.treeId"),
            text(values, "work.sapling"),
            readPosition(values, "work.replant."),
            blocks,
            longValue(values, "work.expectedRevision"),
            text(values, "work.expectedFingerprint"),
            enumValue(TreeWorkStage.class, values, "work.stage"));
    }

    private static void writePosition(Map<String, String> values, String prefix, BasePosition position) {
        values.put(prefix + "dimension", Integer.toString(position.getDimensionId()));
        values.put(prefix + "x", Integer.toString(position.getX()));
        values.put(prefix + "y", Integer.toString(position.getY()));
        values.put(prefix + "z", Integer.toString(position.getZ()));
    }

    private static BasePosition readPosition(Map<String, String> values, String prefix) {
        return new BasePosition(
            integer(values, prefix + "dimension"),
            integer(values, prefix + "x"),
            integer(values, prefix + "y"),
            integer(values, prefix + "z"));
    }

    private static void requireSpec(TaskSpec spec, NamedArea area) {
        if (!TreeTask.areaId(spec)
            .equals(area.getId())) throw new IllegalArgumentException("tree checkpoint belongs to another area");
        TreeTask.minimumSaplingReserve(spec);
    }

    private static String text(Map<String, String> values, String key) {
        String value = values.get(key);
        if (value == null || value.trim()
            .isEmpty()) throw new IllegalArgumentException("missing tree field " + key);
        return value.trim();
    }

    private static int integer(Map<String, String> values, String key) {
        try {
            return Integer.parseInt(text(values, key));
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException("invalid tree integer " + key, failure);
        }
    }

    private static long longValue(Map<String, String> values, String key) {
        try {
            return Long.parseLong(text(values, key));
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException("invalid tree long " + key, failure);
        }
    }

    private static boolean bool(Map<String, String> values, String key) {
        String value = text(values, key);
        if (!"true".equals(value) && !"false".equals(value))
            throw new IllegalArgumentException("invalid tree boolean " + key);
        return Boolean.parseBoolean(value);
    }

    private static <T extends Enum<T>> T enumValue(Class<T> type, Map<String, String> values, String key) {
        try {
            return Enum.valueOf(type, text(values, key));
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException("invalid tree enum " + key, failure);
        }
    }

    static final class State {

        final NamedArea area;
        final long passRevision;
        final List<TreeObservation> trees;
        final int nextIndex;
        final int verifiedTrees;
        final TreeWorkCheckpoint work;
        final boolean planting;
        final List<TreeWorkCheckpoint> pending;

        State(NamedArea area, long passRevision, List<TreeObservation> trees, int nextIndex, int verifiedTrees,
            TreeWorkCheckpoint work) {
            this(area, passRevision, trees, nextIndex, verifiedTrees, work, false, Collections.emptyList());
        }

        State(NamedArea area, long passRevision, List<TreeObservation> trees, int nextIndex, int verifiedTrees,
            TreeWorkCheckpoint work, boolean planting, List<TreeWorkCheckpoint> pending) {
            if (area == null || passRevision < 1L
                || trees == null
                || trees.contains(null)
                || trees.size() > 4096
                || nextIndex < 0
                || nextIndex > trees.size()
                || verifiedTrees < 0
                || verifiedTrees > 4096
                || pending == null
                || pending.contains(null)
                || pending.size() > 4096
                || (work != null && (nextIndex >= trees.size() || !work.getTreeId()
                    .equals(
                        trees.get(nextIndex)
                            .getTreeId())))) {
                throw new IllegalArgumentException("invalid tree pass state");
            }
            java.util.Set<String> pendingIds = new java.util.HashSet<>();
            for (TreeWorkCheckpoint item : pending) {
                if (item.getStage() != TreeWorkStage.READY_TO_REPLANT || !area.equals(item.getTreeFarm())
                    || !pendingIds.add(item.getTreeId()))
                    throw new IllegalArgumentException("invalid deferred planting frontier");
            }
            if (planting && pending.size() != trees.size())
                throw new IllegalArgumentException("planting queue does not match the frozen sites");
            this.area = area;
            this.passRevision = passRevision;
            this.trees = Collections.unmodifiableList(new ArrayList<>(trees));
            this.nextIndex = nextIndex;
            this.verifiedTrees = verifiedTrees;
            this.work = work;
            this.planting = planting;
            this.pending = Collections.unmodifiableList(new ArrayList<>(pending));
        }

        boolean isComplete() {
            return nextIndex == trees.size() && (planting || trees.isEmpty());
        }

        State withWork(TreeWorkCheckpoint nextWork) {
            return new State(area, passRevision, trees, nextIndex, verifiedTrees, nextWork, planting, pending);
        }

        State advance(boolean verified) {
            return new State(
                area,
                passRevision,
                trees,
                nextIndex + 1,
                verifiedTrees + (verified ? 1 : 0),
                null,
                planting,
                pending);
        }

        State defer(TreeWorkCheckpoint cleared) {
            List<TreeWorkCheckpoint> next = new ArrayList<>(pending);
            next.add(cleared);
            return new State(area, passRevision, trees, nextIndex + 1, verifiedTrees, null, false, next);
        }

        boolean collecting() {
            return !planting && nextIndex == trees.size() && !trees.isEmpty();
        }

        State beginPlanting() {
            List<TreeObservation> sites = new ArrayList<>();
            for (TreeWorkCheckpoint item : pending) sites.add(
                new TreeObservation(
                    item.getTreeId(),
                    item.getExpectedObservationRevision(),
                    item.getExpectedObservationFingerprint(),
                    item.getRequiredSaplingFingerprint(),
                    Collections.emptyList(),
                    item.getReplantPosition(),
                    TreeObservationState.FELLED_CLEAR,
                    false,
                    false));
            return new State(area, passRevision, sites, 0, verifiedTrees, null, true, pending);
        }

        State beginGridPlanting(List<TreeObservation> sites) {
            List<TreeWorkCheckpoint> grid = new ArrayList<>();
            for (TreeObservation site : sites) {
                if (site.getState() != TreeObservationState.FELLED_CLEAR || site.isProtectedTree())
                    throw new IllegalArgumentException("Grid must contain only clear planting sites");
                grid.add(TreeWorkCheckpoint.start(area, passRevision, site));
            }
            return new State(area, passRevision, sites, 0, verifiedTrees, null, true, grid);
        }
    }
}
