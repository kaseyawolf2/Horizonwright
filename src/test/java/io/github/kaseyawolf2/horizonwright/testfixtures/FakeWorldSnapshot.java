package io.github.kaseyawolf2.horizonwright.testfixtures;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class FakeWorldSnapshot {

    private final int dimensionId;
    private final long revision;
    private final Map<BlockPos, BlockState> blocks;

    public FakeWorldSnapshot(int dimensionId, long revision, Map<BlockPos, BlockState> blocks) {
        if (revision < 1L) {
            throw new IllegalArgumentException("revision must be positive");
        }
        if (blocks == null) {
            throw new IllegalArgumentException("blocks must not be null");
        }
        LinkedHashMap<BlockPos, BlockState> copy = new LinkedHashMap<>();
        for (Map.Entry<BlockPos, BlockState> entry : blocks.entrySet()) {
            copy.put(
                Objects.requireNonNull(entry.getKey(), "block position"),
                Objects.requireNonNull(entry.getValue(), "block state"));
        }
        this.dimensionId = dimensionId;
        this.revision = revision;
        this.blocks = Collections.unmodifiableMap(copy);
    }

    public int getDimensionId() {
        return dimensionId;
    }

    public long getRevision() {
        return revision;
    }

    public Optional<BlockState> blockAt(BlockPos position) {
        return Optional.ofNullable(blocks.get(position));
    }

    public Map<BlockPos, BlockState> getBlocks() {
        return blocks;
    }

    public static final class BlockPos implements Comparable<BlockPos> {

        private final int x;
        private final int y;
        private final int z;

        public BlockPos(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public int getX() {
            return x;
        }

        public int getY() {
            return y;
        }

        public int getZ() {
            return z;
        }

        @Override
        public int compareTo(BlockPos other) {
            int byX = Integer.compare(x, other.x);
            if (byX != 0) {
                return byX;
            }
            int byZ = Integer.compare(z, other.z);
            return byZ != 0 ? byZ : Integer.compare(y, other.y);
        }

        @Override
        public boolean equals(Object candidate) {
            if (this == candidate) {
                return true;
            }
            if (!(candidate instanceof BlockPos)) {
                return false;
            }
            BlockPos other = (BlockPos) candidate;
            return x == other.x && y == other.y && z == other.z;
        }

        @Override
        public int hashCode() {
            return Objects.hash(x, y, z);
        }

        @Override
        public String toString() {
            return "BlockPos{" + x + ',' + y + ',' + z + '}';
        }
    }

    public static final class BlockState {

        private final String registryName;
        private final int metadata;

        public BlockState(String registryName, int metadata) {
            if (registryName == null || registryName.trim()
                .isEmpty()) {
                throw new IllegalArgumentException("registryName must not be blank");
            }
            if (metadata < 0) {
                throw new IllegalArgumentException("metadata must not be negative");
            }
            this.registryName = registryName.trim();
            this.metadata = metadata;
        }

        public String getRegistryName() {
            return registryName;
        }

        public int getMetadata() {
            return metadata;
        }

        @Override
        public boolean equals(Object candidate) {
            if (this == candidate) {
                return true;
            }
            if (!(candidate instanceof BlockState)) {
                return false;
            }
            BlockState other = (BlockState) candidate;
            return metadata == other.metadata && registryName.equals(other.registryName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(registryName, metadata);
        }
    }
}
