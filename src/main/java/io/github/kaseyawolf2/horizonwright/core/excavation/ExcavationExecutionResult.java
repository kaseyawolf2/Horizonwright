package io.github.kaseyawolf2.horizonwright.core.excavation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Immutable ordered execution prefix for one tagged plan. */
public final class ExcavationExecutionResult {

    private final ExcavationPlan plan;
    private final List<ExcavationTargetResult> targetResults;
    private final ExcavationSuspensionReason suspensionReason;
    private final ExcavationFrontier nextFrontier;

    public ExcavationExecutionResult(ExcavationPlan plan, List<ExcavationTargetResult> targetResults,
        ExcavationSuspensionReason suspensionReason) {
        this.plan = Objects.requireNonNull(plan, "plan");
        this.suspensionReason = Objects.requireNonNull(suspensionReason, "suspensionReason");
        if (targetResults == null || targetResults.size() > plan.getIntents()
            .size()) {
            throw new IllegalArgumentException("targetResults must be an ordered prefix of the plan");
        }
        List<ExcavationTargetResult> copy = new ArrayList<>(targetResults.size());
        for (int index = 0; index < targetResults.size(); index++) {
            ExcavationTargetResult result = Objects.requireNonNull(targetResults.get(index), "targetResult");
            ExcavationIntent intent = plan.getIntents()
                .get(index);
            if (!intent.getPosition()
                .equals(result.getPosition())) {
                throw new IllegalArgumentException("targetResults must preserve plan order and position");
            }
            requireCompatible(intent.getKind(), result.getOutcome());
            copy.add(result);
        }
        if (copy.size() < plan.getIntents()
            .size() && suspensionReason == ExcavationSuspensionReason.NONE) {
            throw new IllegalArgumentException("a partial execution result must carry an exact suspension reason");
        }
        this.targetResults = Collections.unmodifiableList(copy);
        this.nextFrontier = copy.isEmpty() ? plan.getStartFrontier()
            : plan.getIntents()
                .get(copy.size() - 1)
                .getNextFrontier();
    }

    public long getTaskRevision() {
        return plan.getTaskRevision();
    }

    public long getActionEpoch() {
        return plan.getActionEpoch();
    }

    public String getGeometryKey() {
        return plan.getGeometryKey();
    }

    public ExcavationFrontier getStartFrontier() {
        return plan.getStartFrontier();
    }

    public ExcavationFrontier getNextFrontier() {
        return nextFrontier;
    }

    public List<ExcavationTargetResult> getTargetResults() {
        return targetResults;
    }

    public ExcavationSuspensionReason getSuspensionReason() {
        return suspensionReason;
    }

    public boolean executedEntirePlan() {
        return targetResults.size() == plan.getIntents()
            .size();
    }

    private static void requireCompatible(ExcavationIntentKind intent, ExcavationTargetOutcome outcome) {
        boolean compatible;
        switch (intent) {
            case ALREADY_CLEAR:
                compatible = outcome == ExcavationTargetOutcome.COMPLETED;
                break;
            case PROTECT_GRAVE:
            case PROTECT_INFRASTRUCTURE:
            case IGNORE_FOLIAGE:
                compatible = outcome == ExcavationTargetOutcome.PROTECTED;
                break;
            case CONTAIN_FLUID:
                compatible = outcome == ExcavationTargetOutcome.FLUID_CONTAINED
                    || outcome == ExcavationTargetOutcome.UNREACHABLE
                    || outcome == ExcavationTargetOutcome.FAILED;
                break;
            case MARK_UNREACHABLE:
                compatible = outcome == ExcavationTargetOutcome.UNREACHABLE;
                break;
            case MARK_FAILED:
                compatible = outcome == ExcavationTargetOutcome.FAILED;
                break;
            case BREAK_BLOCK:
            case CLEAR_FLUID_SOURCE:
                compatible = outcome == ExcavationTargetOutcome.COMPLETED
                    || outcome == ExcavationTargetOutcome.UNREACHABLE
                    || outcome == ExcavationTargetOutcome.FAILED;
                break;
            default:
                compatible = false;
        }
        if (!compatible) {
            throw new IllegalArgumentException("outcome " + outcome + " is incompatible with intent " + intent);
        }
    }
}
