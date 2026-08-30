package io.github.kaseyawolf2.horizonwright.core.excavation;

import java.util.Objects;

/** A geometry target and the exact frontier immediately after it. */
public final class ExcavationTarget {

    private final BlockPosition position;
    private final ExcavationFrontier nextFrontier;

    ExcavationTarget(BlockPosition position, ExcavationFrontier nextFrontier) {
        this.position = Objects.requireNonNull(position, "position");
        this.nextFrontier = Objects.requireNonNull(nextFrontier, "nextFrontier");
    }

    public BlockPosition getPosition() {
        return position;
    }

    public ExcavationFrontier getNextFrontier() {
        return nextFrontier;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ExcavationTarget)) {
            return false;
        }
        ExcavationTarget that = (ExcavationTarget) other;
        return position.equals(that.position) && nextFrontier.equals(that.nextFrontier);
    }

    @Override
    public int hashCode() {
        return Objects.hash(position, nextFrontier);
    }
}
