package io.github.kaseyawolf2.horizonwright.core.excavation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** A bounded immutable request for client-thread world observations. */
public final class ExcavationTargetBatch {

    private final ExcavationFrontier startFrontier;
    private final ExcavationFrontier nextFrontier;
    private final List<ExcavationTarget> targets;

    ExcavationTargetBatch(ExcavationFrontier startFrontier, ExcavationFrontier nextFrontier,
        List<ExcavationTarget> targets) {
        this.startFrontier = Objects.requireNonNull(startFrontier, "startFrontier");
        this.nextFrontier = Objects.requireNonNull(nextFrontier, "nextFrontier");
        this.targets = Collections.unmodifiableList(new ArrayList<>(targets));
    }

    public ExcavationFrontier getStartFrontier() {
        return startFrontier;
    }

    public ExcavationFrontier getNextFrontier() {
        return nextFrontier;
    }

    public List<ExcavationTarget> getTargets() {
        return targets;
    }

    public boolean isEmpty() {
        return targets.isEmpty();
    }
}
