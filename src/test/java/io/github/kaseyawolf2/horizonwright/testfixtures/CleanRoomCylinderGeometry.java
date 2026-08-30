package io.github.kaseyawolf2.horizonwright.testfixtures;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class CleanRoomCylinderGeometry {

    private CleanRoomCylinderGeometry() {}

    public static List<Column> columns(int centerX, int centerZ, int radius) {
        if (radius < 0) {
            throw new IllegalArgumentException("radius must not be negative");
        }
        long radiusSquared = (long) radius * radius;
        List<Column> result = new ArrayList<>();
        for (int deltaX = -radius; deltaX <= radius; deltaX++) {
            for (int deltaZ = -radius; deltaZ <= radius; deltaZ++) {
                long distanceSquared = (long) deltaX * deltaX + (long) deltaZ * deltaZ;
                if (distanceSquared <= radiusSquared) {
                    result.add(new Column(Math.addExact(centerX, deltaX), Math.addExact(centerZ, deltaZ)));
                }
            }
        }
        return Collections.unmodifiableList(result);
    }

    public static List<FakeWorldSnapshot.BlockPos> blocks(int centerX, int centerZ, int radius, int bottomY, int topY) {
        if (topY < bottomY) {
            throw new IllegalArgumentException("topY must be at or above bottomY");
        }
        List<FakeWorldSnapshot.BlockPos> result = new ArrayList<>();
        for (Column column : columns(centerX, centerZ, radius)) {
            for (int y = topY;; y--) {
                result.add(new FakeWorldSnapshot.BlockPos(column.getX(), y, column.getZ()));
                if (y == bottomY) {
                    break;
                }
            }
        }
        return Collections.unmodifiableList(result);
    }

    public static final class Column {

        private final int x;
        private final int z;

        public Column(int x, int z) {
            this.x = x;
            this.z = z;
        }

        public int getX() {
            return x;
        }

        public int getZ() {
            return z;
        }

        @Override
        public boolean equals(Object candidate) {
            return this == candidate
                || candidate instanceof Column && x == ((Column) candidate).x && z == ((Column) candidate).z;
        }

        @Override
        public int hashCode() {
            return Objects.hash(x, z);
        }

        @Override
        public String toString() {
            return "Column{" + x + ',' + z + '}';
        }
    }
}
