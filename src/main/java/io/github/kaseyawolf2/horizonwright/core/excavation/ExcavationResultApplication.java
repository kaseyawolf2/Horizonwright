package io.github.kaseyawolf2.horizonwright.core.excavation;

import java.util.Objects;

public final class ExcavationResultApplication {

    private final ExcavationResultDisposition disposition;
    private final ExcavationCheckpoint checkpoint;

    ExcavationResultApplication(ExcavationResultDisposition disposition, ExcavationCheckpoint checkpoint) {
        this.disposition = Objects.requireNonNull(disposition, "disposition");
        this.checkpoint = Objects.requireNonNull(checkpoint, "checkpoint");
    }

    public ExcavationResultDisposition getDisposition() {
        return disposition;
    }

    public ExcavationCheckpoint getCheckpoint() {
        return checkpoint;
    }

    public boolean wasApplied() {
        return disposition == ExcavationResultDisposition.APPLIED;
    }
}
