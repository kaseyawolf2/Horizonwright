package io.github.kaseyawolf2.horizonwright.core.excavation;

import java.util.Objects;

public final class ExcavationTargetResult {

    private final BlockPosition position;
    private final ExcavationTargetOutcome outcome;

    public ExcavationTargetResult(BlockPosition position, ExcavationTargetOutcome outcome) {
        this.position = Objects.requireNonNull(position, "position");
        this.outcome = Objects.requireNonNull(outcome, "outcome");
    }

    public BlockPosition getPosition() {
        return position;
    }

    public ExcavationTargetOutcome getOutcome() {
        return outcome;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ExcavationTargetResult)) {
            return false;
        }
        ExcavationTargetResult that = (ExcavationTargetResult) other;
        return position.equals(that.position) && outcome == that.outcome;
    }

    @Override
    public int hashCode() {
        return Objects.hash(position, outcome);
    }
}
