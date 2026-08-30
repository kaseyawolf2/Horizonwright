package io.github.kaseyawolf2.horizonwright.core.excavation;

import java.util.ArrayList;
import java.util.List;

final class ExcavationTestSupport {

    private ExcavationTestSupport() {}

    static ExcavationPlanningWindow window(CylinderExcavationSpec spec, ExcavationFrontier frontier, int count,
        long taskRevision, long actionEpoch, ExcavationBlockClassification... classifications) {
        ExcavationTargetBatch batch = CylinderExcavationGeometry.nextBatch(spec, frontier, count);
        if (classifications.length != batch.getTargets()
            .size()) {
            throw new IllegalArgumentException("classification count must equal the target batch size");
        }
        List<ExcavationObservation> observations = new ArrayList<>();
        for (int index = 0; index < classifications.length; index++) {
            observations.add(
                new ExcavationObservation(
                    batch.getTargets()
                        .get(index)
                        .getPosition(),
                    classifications[index],
                    "block-" + index));
        }
        return new ExcavationPlanningWindow(taskRevision, actionEpoch, batch, observations);
    }

    static ExcavationPlanningWindow uniformWindow(CylinderExcavationSpec spec, ExcavationFrontier frontier, int count,
        long taskRevision, long actionEpoch, ExcavationBlockClassification classification) {
        ExcavationTargetBatch batch = CylinderExcavationGeometry.nextBatch(spec, frontier, count);
        ExcavationBlockClassification[] classifications = new ExcavationBlockClassification[batch.getTargets()
            .size()];
        for (int index = 0; index < classifications.length; index++) {
            classifications[index] = classification;
        }
        return window(spec, frontier, count, taskRevision, actionEpoch, classifications);
    }

    static List<ExcavationTargetResult> outcomes(ExcavationPlan plan, ExcavationTargetOutcome... outcomes) {
        List<ExcavationTargetResult> results = new ArrayList<>();
        for (int index = 0; index < outcomes.length; index++) {
            results.add(
                new ExcavationTargetResult(
                    plan.getIntents()
                        .get(index)
                        .getPosition(),
                    outcomes[index]));
        }
        return results;
    }
}
