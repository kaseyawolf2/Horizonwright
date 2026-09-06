package io.github.kaseyawolf2.horizonwright.forge.client.farm;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;

import io.github.kaseyawolf2.horizonwright.DevelopmentTrace;
import io.github.kaseyawolf2.horizonwright.core.base.BasePosition;
import io.github.kaseyawolf2.horizonwright.core.base.NamedArea;
import io.github.kaseyawolf2.horizonwright.core.base.SaplingReserveEvidence;
import io.github.kaseyawolf2.horizonwright.core.base.TreeObservation;
import io.github.kaseyawolf2.horizonwright.core.base.TreeObservationState;
import io.github.kaseyawolf2.horizonwright.core.base.TreeWorkCheckpoint;
import io.github.kaseyawolf2.horizonwright.core.base.TreeWorkStage;
import io.github.kaseyawolf2.horizonwright.core.container.ItemFingerprint;
import io.github.kaseyawolf2.horizonwright.forge.client.MinecraftRuntimeAccess;
import io.github.kaseyawolf2.horizonwright.forge.client.container.MinecraftContainerSnapshotter;

/** Conservative vanilla-tree observer bounded to an identity-bound named area. */
public final class MinecraftVanillaTreeObserver {

    private static final long MAX_SCANNED_BLOCKS = 65_536L;
    private static final int[][] NEIGHBORS = { { 1, 0, 0 }, { -1, 0, 0 }, { 0, 1, 0 }, { 0, -1, 0 }, { 0, 0, 1 },
        { 0, 0, -1 } };
    private static final Comparator<BasePosition> BOTTOM_UP = Comparator.comparingInt(BasePosition::getY)
        .thenComparingInt(BasePosition::getX)
        .thenComparingInt(BasePosition::getZ);

    private final Minecraft minecraft;
    private final ProfileFarmConfiguration configuration;
    private final MinecraftContainerSnapshotter items = new MinecraftContainerSnapshotter();

    public MinecraftVanillaTreeObserver(Minecraft minecraft, ProfileFarmConfiguration configuration) {
        if (minecraft == null || configuration == null) {
            throw new IllegalArgumentException("minecraft and tree-farm configuration are required");
        }
        this.minecraft = minecraft;
        this.configuration = configuration;
    }

    public NamedArea resolveArea(String areaId) {
        requireClient();
        NamedArea area = configuration.resolve(areaId);
        requireCurrentDimension(area);
        requireBoundedLoadedArea(area);
        return area;
    }

    public List<TreeObservation> scan(NamedArea area) {
        return scan(area, -1, 5);
    }

    public List<TreeObservation> scan(NamedArea area, int plantingSpecies, int spacing) {
        requireClient();
        requireCurrentDimension(area);
        requireBoundedLoadedArea(area);
        Set<BasePosition> visited = new HashSet<>();
        List<TreeObservation> trees = new ArrayList<>();
        BasePosition min = area.getMinimum();
        BasePosition max = area.getMaximum();
        long revision = positiveWorldRevision();
        boolean occupiedByTree = false;
        for (int y = min.getY(); y <= max.getY(); y++) {
            for (int z = min.getZ(); z <= max.getZ(); z++) {
                for (int x = min.getX(); x <= max.getX(); x++) {
                    BasePosition position = new BasePosition(min.getDimensionId(), x, y, z);
                    int species = logSpecies(position);
                    Block scanned = MinecraftRuntimeAccess.block(minecraft.theWorld, x, y, z);
                    if (species >= 0 || scanned == Blocks.sapling || scanned.isWood(minecraft.theWorld, x, y, z))
                        occupiedByTree = true;
                    if (species < 0 || visited.contains(position)) continue;
                    Component component = component(area, position, species, visited);
                    TreeObservation observation = standingObservation(area, component, revision);
                    if (observation != null && !observation.isProtectedTree()) trees.add(observation);
                    else if (observation != null) DevelopmentTrace.event(
                        "tree-observer",
                        "skip-protected-tree",
                        "root",
                        observation.getReplantPosition(),
                        "reason",
                        "boundary or unsupported root pattern");
                }
            }
        }
        if (!occupiedByTree && plantingSpecies >= 0) {
            int footprint = plantingSpecies >= 5 ? 2 : 1;
            int actualSpecies = plantingSpecies == 6 ? 1 : plantingSpecies == 7 ? 3 : plantingSpecies;
            for (int z = min.getZ(); z <= max.getZ(); z += spacing) {
                for (int x = min.getX(); x <= max.getX(); x += spacing) {
                    for (int y = max.getY(); y >= Math.max(1, min.getY()); y--) {
                        if (!clearPlantingFootprint(area, x, y, z, footprint)) continue;
                        BasePosition root = new BasePosition(min.getDimensionId(), x, y, z);
                        String id = "vanilla-plant@" + min
                            .getDimensionId() + ":" + x + "," + y + "," + z + ":" + plantingSpecies;
                        if (footprint == 2) id += "|2x2";
                        trees.add(
                            new TreeObservation(
                                id,
                                revision,
                                "sha256:" + sha256(id + "|clear"),
                                saplingFingerprint(actualSpecies),
                                Collections.emptyList(),
                                root,
                                TreeObservationState.FELLED_CLEAR,
                                false,
                                false));
                        break;
                    }
                }
            }
            DevelopmentTrace.event(
                "tree-observer",
                "empty-farm-planting",
                "species",
                plantingSpecies,
                "spacing",
                spacing,
                "sites",
                trees.size());
        }
        Collections.sort(trees, Comparator.comparing(tree -> tree.getReplantPosition(), BOTTOM_UP));
        return Collections.unmodifiableList(trees);
    }

    public TreeObservation observe(TreeWorkCheckpoint work) {
        requireClient();
        if (work == null) throw new IllegalArgumentException("tree work checkpoint is required");
        requireCurrentDimension(work.getTreeFarm());
        requireLoaded(work.getReplantPosition());
        for (BasePosition block : work.getCapturedBlocks()) requireLoaded(block);
        if (work.getStage() == TreeWorkStage.READY_TO_FELL) return observeStanding(work);
        return observePostFell(work);
    }

    private boolean clearPlantingFootprint(NamedArea area, int x, int y, int z, int side) {
        for (int dz = 0; dz < side; dz++) for (int dx = 0; dx < side; dx++) {
            if (!area.contains(
                new BasePosition(
                    area.getMinimum()
                        .getDimensionId(),
                    x + dx,
                    y,
                    z + dz))
                || !minecraft.theWorld.isAirBlock(x + dx, y, z + dz)) return false;
            Block support = MinecraftRuntimeAccess.block(minecraft.theWorld, x + dx, y - 1, z + dz);
            if (support != Blocks.grass && support != Blocks.dirt) return false;
        }
        return true;
    }

    public BasePosition nextMissingSapling(TreeWorkCheckpoint work) {
        int species = saplingSpecies(work.getRequiredSaplingFingerprint());
        for (BasePosition position : work.getReplantPositions()) {
            requireLoaded(position);
            if (!isSapling(position, species)) return position;
        }
        return null;
    }

    TreeObservation observeAfterMutation(TreeWorkCheckpoint work) {
        requireClient();
        if (work == null) throw new IllegalArgumentException("tree work checkpoint is required");
        requireCurrentDimension(work.getTreeFarm());
        requireLoaded(work.getReplantPosition());
        for (BasePosition block : work.getCapturedBlocks()) requireLoaded(block);
        return observePostFell(work);
    }

    boolean hasExpectedLog(BasePosition position, String requiredSaplingFingerprint) {
        requireClient();
        requireLoaded(position);
        return logSpecies(position) == saplingSpecies(requiredSaplingFingerprint);
    }

    public SaplingReserveEvidence reserve(long inventoryRevision, String requiredSaplingFingerprint,
        int minimumReserve) {
        requireClient();
        int count = 0;
        StringBuilder snapshot = new StringBuilder();
        for (int slot = 0; slot < minecraft.thePlayer.inventory.mainInventory.length; slot++) {
            ItemStack stack = minecraft.thePlayer.inventory.mainInventory[slot];
            ItemFingerprint fingerprint = items.fingerprint(stack);
            snapshot.append(slot)
                .append('=')
                .append(fingerprint == null ? "empty" : fingerprint.toString())
                .append(';');
            if (fingerprint != null
                && requiredSaplingFingerprint.equals(MinecraftVanillaFarmObserver.materialIdentity(fingerprint))) {
                count = Math.addExact(count, fingerprint.getCount());
            }
        }
        ItemFingerprint cursor = items.fingerprint(minecraft.thePlayer.inventory.getItemStack());
        snapshot.append("cursor=")
            .append(cursor == null ? "empty" : cursor.toString());
        return new SaplingReserveEvidence(
            inventoryRevision,
            "sha256:" + sha256(snapshot.toString()),
            requiredSaplingFingerprint,
            count,
            minimumReserve);
    }

    public int findSaplingSlot(String requiredSaplingFingerprint, int startInclusive, int endExclusive) {
        requireClient();
        if (startInclusive < 0 || endExclusive > 36 || startInclusive > endExclusive) {
            throw new IllegalArgumentException("inventory slot bounds must be within 0..36");
        }
        for (int slot = startInclusive; slot < endExclusive; slot++) {
            ItemFingerprint fingerprint = items.fingerprint(minecraft.thePlayer.inventory.mainInventory[slot]);
            if (fingerprint != null
                && requiredSaplingFingerprint.equals(MinecraftVanillaFarmObserver.materialIdentity(fingerprint)))
                return slot;
        }
        return -1;
    }

    private TreeObservation observeStanding(TreeWorkCheckpoint work) {
        int species = saplingSpecies(work.getRequiredSaplingFingerprint());
        for (BasePosition block : work.getCapturedBlocks()) {
            if (minecraft.theWorld.isAirBlock(block.getX(), block.getY(), block.getZ())) {
                DevelopmentTrace
                    .event("tree-observer", "captured-log-already-air", "tree", work.getTreeId(), "position", block);
                continue;
            }
            if (!minecraft.theWorld.isAirBlock(block.getX(), block.getY(), block.getZ())
                && logSpecies(block) != species)
                throw new IllegalStateException("captured tree replaced by an unexpected block at " + block);
        }
        String fingerprint = standingFingerprint(work.getCapturedBlocks(), species);
        if (!work.getExpectedObservationFingerprint()
            .equals(fingerprint)) {
            throw new IllegalStateException("captured tree fingerprint changed before felling");
        }
        return new TreeObservation(
            work.getTreeId(),
            work.getExpectedObservationRevision(),
            fingerprint,
            work.getRequiredSaplingFingerprint(),
            work.getCapturedBlocks(),
            work.getReplantPosition(),
            TreeObservationState.STANDING,
            true,
            false);
    }

    private TreeObservation observePostFell(TreeWorkCheckpoint work) {
        int species = saplingSpecies(work.getRequiredSaplingFingerprint());
        if (nextMissingSapling(work) == null) {
            return postObservation(work, TreeObservationState.SAPLING_PLANTED, "sapling:" + species);
        }
        for (BasePosition root : work.getReplantPositions()) {
            if (!isSapling(root, species) && !minecraft.theWorld.isAirBlock(root.getX(), root.getY(), root.getZ())) {
                throw new IllegalStateException("planting position is no longer clear at " + root);
            }
        }
        for (BasePosition block : work.getCapturedBlocks()) {
            if (work.getReplantPositions()
                .contains(block) && isSapling(block, species)) continue;
            if (!minecraft.theWorld.isAirBlock(block.getX(), block.getY(), block.getZ())) {
                throw new IllegalStateException("captured tree is not yet clear at " + block);
            }
        }
        return postObservation(work, TreeObservationState.FELLED_CLEAR, "clear");
    }

    private TreeObservation postObservation(TreeWorkCheckpoint work, TreeObservationState state, String stateValue) {
        String fingerprint = "sha256:" + sha256(work.getTreeId() + "|" + stateValue);
        long revision = positiveWorldRevision();
        if (state == expectedState(work) && fingerprint.equals(work.getExpectedObservationFingerprint())) {
            revision = work.getExpectedObservationRevision();
        } else if (revision <= work.getExpectedObservationRevision()) {
            revision = work.getExpectedObservationRevision() == Long.MAX_VALUE ? Long.MAX_VALUE
                : work.getExpectedObservationRevision() + 1L;
        }
        return new TreeObservation(
            work.getTreeId(),
            revision,
            fingerprint,
            work.getRequiredSaplingFingerprint(),
            Collections.<BasePosition>emptyList(),
            work.getReplantPosition(),
            state,
            false,
            false);
    }

    private static TreeObservationState expectedState(TreeWorkCheckpoint work) {
        return work.getStage() == TreeWorkStage.READY_TO_REPLANT ? TreeObservationState.FELLED_CLEAR
            : TreeObservationState.SAPLING_PLANTED;
    }

    private TreeObservation standingObservation(NamedArea area, Component component, long revision) {
        if (component.blocks.size() > TreeObservation.MAX_CAPTURED_BLOCKS) {
            DevelopmentTrace.event("tree-observer", "skip", "reason", "more-than-256-logs", "root", component.seed);
            return null;
        }
        Collections.sort(component.blocks, BOTTOM_UP);
        int minimumY = component.blocks.get(0)
            .getY();
        List<BasePosition> roots = new ArrayList<>();
        for (BasePosition block : component.blocks) if (block.getY() == minimumY) roots.add(block);
        BasePosition root = roots.get(0);
        boolean square = roots.size() == 4
            && (component.species == 1 || component.species == 3 || component.species == 5);
        if (square) {
            for (int z = 0; z < 2; z++) for (int x = 0; x < 2; x++) {
                if (!roots
                    .contains(new BasePosition(root.getDimensionId(), root.getX() + x, root.getY(), root.getZ() + z)))
                    square = false;
            }
        }
        boolean protectedTree = component.crossesBoundary || (!square && (roots.size() != 1 || component.species == 5));
        String sapling = saplingFingerprint(component.species);
        String treeId = "vanilla-tree@" + root
            .getDimensionId() + ":" + root.getX() + "," + root.getY() + "," + root.getZ() + ":" + component.species;
        if (square) treeId += "|2x2";
        return new TreeObservation(
            treeId,
            revision,
            standingFingerprint(component.blocks, component.species),
            sapling,
            component.blocks,
            root,
            TreeObservationState.STANDING,
            component.blocks.size() >= 2,
            protectedTree);
    }

    private Component component(NamedArea area, BasePosition seed, int species, Set<BasePosition> visited) {
        Component result = new Component(seed, species);
        ArrayDeque<BasePosition> queue = new ArrayDeque<>();
        queue.add(seed);
        visited.add(seed);
        while (!queue.isEmpty()) {
            BasePosition current = queue.removeFirst();
            result.blocks.add(current);
            for (int[] offset : NEIGHBORS) {
                BasePosition next = new BasePosition(
                    current.getDimensionId(),
                    current.getX() + offset[0],
                    current.getY() + offset[1],
                    current.getZ() + offset[2]);
                if (!area.contains(next)) {
                    if (!MinecraftRuntimeAccess.blockExists(minecraft.theWorld, next.getX(), next.getY(), next.getZ())
                        || logSpecies(next) == species) result.crossesBoundary = true;
                    continue;
                }
                if (!visited.contains(next) && logSpecies(next) == species) {
                    visited.add(next);
                    queue.addLast(next);
                }
            }
        }
        return result;
    }

    private int logSpecies(BasePosition position) {
        Block block = MinecraftRuntimeAccess
            .block(minecraft.theWorld, position.getX(), position.getY(), position.getZ());
        int metadata = MinecraftRuntimeAccess
            .blockMetadata(minecraft.theWorld, position.getX(), position.getY(), position.getZ());
        if (block == Blocks.log) return metadata & 3;
        if (block == Blocks.log2) return 4 + (metadata & 1);
        return -1;
    }

    private boolean isSapling(BasePosition position, int species) {
        return MinecraftRuntimeAccess.block(minecraft.theWorld, position.getX(), position.getY(), position.getZ())
            == Blocks.sapling
            && (MinecraftRuntimeAccess
                .blockMetadata(minecraft.theWorld, position.getX(), position.getY(), position.getZ()) & 7) == species;
    }

    static String saplingFingerprint(int species) {
        if (species < 0 || species > 5) throw new IllegalArgumentException("vanilla tree species must be from 0 to 5");
        return VanillaCropClassifier.materialIdentity("minecraft:sapling", species, "none");
    }

    static int saplingSpecies(String fingerprint) {
        for (int species = 0; species <= 5; species++)
            if (saplingFingerprint(species).equals(fingerprint)) return species;
        throw new IllegalArgumentException("unsupported vanilla sapling fingerprint " + fingerprint);
    }

    private static String standingFingerprint(List<BasePosition> blocks, int species) {
        StringBuilder value = new StringBuilder("standing|").append(species)
            .append('|');
        for (BasePosition block : blocks) value.append(block)
            .append(';');
        return "sha256:" + sha256(value.toString());
    }

    private long positiveWorldRevision() {
        long time = MinecraftRuntimeAccess.totalWorldTime(minecraft.theWorld);
        return time < 1L ? 1L : time;
    }

    private void requireClient() {
        if (!minecraft.func_152345_ab() || minecraft.thePlayer == null
            || minecraft.theWorld == null
            || minecraft.theWorld.provider == null)
            throw new IllegalStateException("a joined Minecraft client thread is required");
    }

    private void requireCurrentDimension(NamedArea area) {
        if (area == null || area.getMinimum()
            .getDimensionId() != minecraft.theWorld.provider.dimensionId) {
            throw new IllegalStateException("named tree farm is in another dimension");
        }
    }

    private void requireBoundedLoadedArea(NamedArea area) {
        BasePosition min = area.getMinimum();
        BasePosition max = area.getMaximum();
        long x = (long) max.getX() - min.getX() + 1L;
        long y = (long) max.getY() - min.getY() + 1L;
        long z = (long) max.getZ() - min.getZ() + 1L;
        long volume;
        try {
            volume = Math.multiplyExact(Math.multiplyExact(x, y), z);
        } catch (ArithmeticException failure) {
            throw new IllegalStateException("named tree farm volume is too large", failure);
        }
        if (volume > MAX_SCANNED_BLOCKS)
            throw new IllegalStateException("named tree farm exceeds the 65,536-block observation bound");
        for (int chunkX = min.getX() >> 4; chunkX <= max.getX() >> 4; chunkX++) {
            for (int chunkZ = min.getZ() >> 4; chunkZ <= max.getZ() >> 4; chunkZ++) {
                if (!MinecraftRuntimeAccess.chunkProvider(minecraft.theWorld)
                    .chunkExists(chunkX, chunkZ)) {
                    throw new IllegalStateException("every chunk in the named tree farm must be loaded");
                }
            }
        }
    }

    private void requireLoaded(BasePosition position) {
        if (position.getDimensionId() != minecraft.theWorld.provider.dimensionId
            || !MinecraftRuntimeAccess.chunkProvider(minecraft.theWorld)
                .chunkExists(position.getX() >> 4, position.getZ() >> 4)) {
            throw new IllegalStateException("tree target is not loaded in the current dimension");
        }
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder encoded = new StringBuilder(digest.length * 2);
            for (byte current : digest) encoded.append(String.format("%02x", current & 0xff));
            return encoded.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static final class Component {

        private final BasePosition seed;
        private final int species;
        private final List<BasePosition> blocks = new ArrayList<>();
        private boolean crossesBoundary;

        private Component(BasePosition seed, int species) {
            this.seed = seed;
            this.species = species;
        }
    }
}
