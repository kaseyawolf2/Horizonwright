package io.github.kaseyawolf2.horizonwright.core.excavation;

import java.util.ArrayList;
import java.util.List;

/** Pure off-thread conversion from observations to verified action intents. */
public final class ExcavationPlanner {

    private ExcavationPlanner() {}

    public static ExcavationPlan calculate(CylinderExcavationSpec spec, ExcavationPlanningWindow window,
        ManagedQuarryConfiguration managedConfiguration) {
        if (spec == null || window == null) {
            throw new IllegalArgumentException("spec and window must not be null");
        }
        ExcavationTargetBatch batch = window.getTargetBatch();
        CylinderExcavationGeometry.validate(spec, batch.getStartFrontier());
        if (spec.getMode() == ExcavationMode.MANAGED_QUARRY && managedConfiguration == null) {
            throw new IllegalArgumentException("managed quarry planning requires an approved configuration");
        }
        if (spec.getMode() == ExcavationMode.CLEAN_VOLUME && managedConfiguration != null) {
            throw new IllegalArgumentException("clean-volume planning must not carry managed quarry materials");
        }

        List<ExcavationIntent> intents = new ArrayList<>(
            window.getObservations()
                .size());
        List<ManagedQuarryIntent> managedIntents = new ArrayList<>();
        for (int index = 0; index < window.getObservations()
            .size(); index++) {
            ExcavationObservation observation = window.getObservations()
                .get(index);
            ExcavationTarget target = batch.getTargets()
                .get(index);
            ExcavationFrontier targetFrontier = index == 0 ? batch.getStartFrontier()
                : batch.getTargets()
                    .get(index - 1)
                    .getNextFrontier();
            if (spec.getMode() == ExcavationMode.MANAGED_QUARRY
                && CylinderExcavationGeometry.isFirstTargetOfLayer(spec, targetFrontier)) {
                addLayerInfrastructure(
                    spec,
                    observation.getPosition()
                        .getY(),
                    managedConfiguration,
                    managedIntents);
            }
            intents.add(toIntent(spec, observation, target.getNextFrontier(), managedConfiguration));
        }
        return new ExcavationPlan(
            window.getTaskRevision(),
            window.getActionEpoch(),
            spec.getGeometryKey(),
            batch.getStartFrontier(),
            batch.getNextFrontier(),
            intents,
            managedIntents);
    }

    private static ExcavationIntent toIntent(CylinderExcavationSpec spec, ExcavationObservation observation,
        ExcavationFrontier nextFrontier, ManagedQuarryConfiguration configuration) {
        ExcavationIntentKind kind;
        String material = null;
        switch (observation.getClassification()) {
            case AIR:
                kind = ExcavationIntentKind.ALREADY_CLEAR;
                break;
            case BREAKABLE:
                kind = ExcavationIntentKind.BREAK_BLOCK;
                break;
            case PROTECTED_GRAVE:
                kind = ExcavationIntentKind.PROTECT_GRAVE;
                break;
            case PROTECTED_INFRASTRUCTURE:
                kind = ExcavationIntentKind.PROTECT_INFRASTRUCTURE;
                break;
            case FLUID_SOURCE_REACHABLE:
                kind = ExcavationIntentKind.CLEAR_FLUID_SOURCE;
                break;
            case FLUID_SOURCE_UNREACHABLE:
            case FLUID_FLOWING:
                if (spec.getMode() == ExcavationMode.MANAGED_QUARRY) {
                    kind = ExcavationIntentKind.CONTAIN_FLUID;
                    material = configuration.getFluidFillerMaterial();
                } else {
                    kind = ExcavationIntentKind.MARK_UNREACHABLE;
                }
                break;
            case UNREACHABLE:
                kind = ExcavationIntentKind.MARK_UNREACHABLE;
                break;
            case FAILED:
                kind = ExcavationIntentKind.MARK_FAILED;
                break;
            default:
                throw new IllegalStateException("unhandled excavation classification");
        }
        return new ExcavationIntent(
            observation.getPosition(),
            kind,
            observation.getBlockFingerprint(),
            material,
            nextFrontier);
    }

    private static void addLayerInfrastructure(CylinderExcavationSpec spec, int layerY,
        ManagedQuarryConfiguration configuration, List<ManagedQuarryIntent> managedIntents) {
        BlockPosition rampStep = ManagedQuarryGeometry.rampStep(spec, layerY);
        managedIntents.add(
            new ManagedQuarryIntent(
                ManagedQuarryIntentKind.MAINTAIN_PERIMETER_RAMP,
                rampStep,
                configuration.getRampMaterial()));
        if ((spec.getTopY() - layerY) % configuration.getLightLayerInterval() == 0) {
            managedIntents.add(
                new ManagedQuarryIntent(
                    ManagedQuarryIntentKind.PLACE_APPROVED_LIGHT,
                    ManagedQuarryGeometry.lightPosition(spec, layerY),
                    configuration.getLightMaterial()));
        }
    }
}
