package io.github.kaseyawolf2.horizonwright.core.excavation;

import java.util.Arrays;

/** One bounded horizontal lookup, reused for every Y; concentric Euclidean shells run clockwise inward. */
final class CircularSpiral {

    private final int width;
    private final int[] order;
    private final int[] successor;

    CircularSpiral(CylinderExcavationSpec spec) {
        width = spec.getMaximumX() - spec.getMinimumX() + 1;
        int depth = spec.getMaximumZ() - spec.getMinimumZ() + 1;
        Integer[] columns = new Integer[(int) spec.getColumnCount()];
        int count = 0;
        for (int z = 0; z < depth; z++) for (int x = 0; x < width; x++) {
            if (spec.contains(new BlockPosition(spec.getMinimumX() + x, spec.getTopY(), spec.getMinimumZ() + z)))
                columns[count++] = z * width + x;
        }
        final double centerX = (width - 1) / 2D, centerZ = (depth - 1) / 2D;
        Arrays.sort(columns, (a, b) -> {
            double ax = a % width - centerX, az = a / width - centerZ;
            double bx = b % width - centerX, bz = b / width - centerZ;
            int ring;
            if (spec.getTraversal() == ExcavationTraversal.SQUARE_SPIRAL) {
                int ad = Math
                    .min(Math.min(a % width, width - 1 - a % width), Math.min(a / width, depth - 1 - a / width));
                int bd = Math
                    .min(Math.min(b % width, width - 1 - b % width), Math.min(b / width, depth - 1 - b / width));
                ring = Integer.compare(ad / spec.getSpiralWidth(), bd / spec.getSpiralWidth());
            } else {
                ring = Integer.compare(
                    (int) Math.ceil(Math.hypot(bx, bz)) / spec.getSpiralWidth(),
                    (int) Math.ceil(Math.hypot(ax, az)) / spec.getSpiralWidth());
            }
            if (ring != 0) return ring;
            int angle = Double.compare(angle(ax, az), angle(bx, bz));
            return angle != 0 ? angle : Integer.compare(a, b);
        });
        order = new int[columns.length];
        successor = new int[width * depth];
        Arrays.fill(successor, -1);
        for (int i = 0; i < columns.length; i++) {
            order[i] = columns[i];
            if (i > 0) successor[columns[i - 1]] = columns[i];
        }
    }

    private static double angle(double x, double z) {
        double value = Math.atan2(x, -z);
        return value < 0 ? value + Math.PI * 2 : value;
    }

    BlockPosition first(CylinderExcavationSpec spec, int y) {
        return position(spec, order[0], y);
    }

    BlockPosition next(CylinderExcavationSpec spec, BlockPosition current) {
        int next = successor[(current.getZ() - spec.getMinimumZ()) * width + current.getX() - spec.getMinimumX()];
        return next < 0 ? null : position(spec, next, current.getY());
    }

    private BlockPosition position(CylinderExcavationSpec spec, int column, int y) {
        return new BlockPosition(spec.getMinimumX() + column % width, y, spec.getMinimumZ() + column / width);
    }
}
