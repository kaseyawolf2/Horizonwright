package io.github.kaseyawolf2.horizonwright.testfixtures;

public final class GtProspectGrid {

    private GtProspectGrid() {}

    public static CellCenter cellCenter(int cellX, int cellZ) {
        int chunkX = centerChunk(cellX);
        int chunkZ = centerChunk(cellZ);
        return new CellCenter(cellX, cellZ, chunkX, chunkZ, centerBlock(chunkX), centerBlock(chunkZ));
    }

    public static int centerChunk(int cellCoordinate) {
        return checkedInt(3L * cellCoordinate + 1L, "center chunk");
    }

    public static int centerBlock(int chunkCoordinate) {
        return checkedInt(16L * chunkCoordinate + 8L, "center block");
    }

    private static int checkedInt(long value, String description) {
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new ArithmeticException(description + " overflow");
        }
        return (int) value;
    }

    public static final class CellCenter {

        private final int cellX;
        private final int cellZ;
        private final int chunkX;
        private final int chunkZ;
        private final int blockX;
        private final int blockZ;

        private CellCenter(int cellX, int cellZ, int chunkX, int chunkZ, int blockX, int blockZ) {
            this.cellX = cellX;
            this.cellZ = cellZ;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.blockX = blockX;
            this.blockZ = blockZ;
        }

        public int getCellX() {
            return cellX;
        }

        public int getCellZ() {
            return cellZ;
        }

        public int getChunkX() {
            return chunkX;
        }

        public int getChunkZ() {
            return chunkZ;
        }

        public int getBlockX() {
            return blockX;
        }

        public int getBlockZ() {
            return blockZ;
        }
    }
}
