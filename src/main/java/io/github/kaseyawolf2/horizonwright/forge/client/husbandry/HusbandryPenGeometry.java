package io.github.kaseyawolf2.horizonwright.forge.client.husbandry;

import io.github.kaseyawolf2.horizonwright.core.base.BasePosition;
import io.github.kaseyawolf2.horizonwright.core.base.NamedArea;

/** Shared geometry rules for support-block areas used as livestock pens. */
final class HusbandryPenGeometry {

    static final long MAX_OBSERVATION_VOLUME = 65_536L;

    private HusbandryPenGeometry() {}

    static long observationVolume(NamedArea pen) {
        if (pen == null) throw new IllegalArgumentException("named pen is required");
        BasePosition minimum = pen.getMinimum();
        BasePosition maximum = pen.getMaximum();
        long x = (long) maximum.getX() - minimum.getX() + 1L;
        long y = (long) maximum.getY() - minimum.getY() + 3L;
        long z = (long) maximum.getZ() - minimum.getZ() + 1L;
        try {
            return Math.multiplyExact(Math.multiplyExact(x, y), z);
        } catch (ArithmeticException failure) {
            throw new IllegalStateException("named livestock pen volume is too large", failure);
        }
    }

    static BasePosition policyPosition(NamedArea pen, int dimensionId, double x, double y, double z) {
        if (pen == null || dimensionId != pen.getMinimum()
            .getDimensionId()) throw new IllegalArgumentException("entity and named pen dimensions must match");
        int observedY = floor(y);
        return new BasePosition(
            dimensionId,
            floor(x),
            Math.max(
                pen.getMinimum()
                    .getY(),
                Math.min(
                    pen.getMaximum()
                        .getY(),
                    observedY)),
            floor(z));
    }

    private static int floor(double value) {
        int whole = (int) value;
        return value < whole ? whole - 1 : whole;
    }
}
