package io.github.kaseyawolf2.horizonwright.core.excavation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

public class ExcavationPlannerTest {

    @Test
    public void managedQuarryMapsProtectionSourcesContainmentAndFailuresExactly() {
        CylinderExcavationSpec spec = spec(ExcavationMode.MANAGED_QUARRY, 2, 7, 7);
        ExcavationFrontier start = CylinderExcavationGeometry.initialFrontier(spec);
        ExcavationPlanningWindow window = ExcavationTestSupport.window(
            spec,
            start,
            9,
            12L,
            41L,
            ExcavationBlockClassification.AIR,
            ExcavationBlockClassification.BREAKABLE,
            ExcavationBlockClassification.PROTECTED_GRAVE,
            ExcavationBlockClassification.PROTECTED_INFRASTRUCTURE,
            ExcavationBlockClassification.FLUID_SOURCE_REACHABLE,
            ExcavationBlockClassification.FLUID_SOURCE_UNREACHABLE,
            ExcavationBlockClassification.FLUID_FLOWING,
            ExcavationBlockClassification.UNREACHABLE,
            ExcavationBlockClassification.FAILED);

        ExcavationPlan plan = ExcavationPlanner.calculate(spec, window, ManagedQuarryConfiguration.defaults());

        assertEquals(12L, plan.getTaskRevision());
        assertEquals(41L, plan.getActionEpoch());
        assertEquals(spec.getGeometryKey(), plan.getGeometryKey());
        assertEquals(
            Arrays.asList(
                ExcavationIntentKind.ALREADY_CLEAR,
                ExcavationIntentKind.BREAK_BLOCK,
                ExcavationIntentKind.PROTECT_GRAVE,
                ExcavationIntentKind.PROTECT_INFRASTRUCTURE,
                ExcavationIntentKind.CLEAR_FLUID_SOURCE,
                ExcavationIntentKind.CONTAIN_FLUID,
                ExcavationIntentKind.CONTAIN_FLUID,
                ExcavationIntentKind.MARK_UNREACHABLE,
                ExcavationIntentKind.MARK_FAILED),
            kinds(plan));
        assertEquals(
            ManagedQuarryConfiguration.defaults()
                .getFluidFillerMaterial(),
            plan.getIntents()
                .get(5)
                .getApprovedMaterial()
                .get());
        assertEquals(
            2,
            plan.getManagedIntents()
                .size());
        assertEquals(
            ManagedQuarryIntentKind.MAINTAIN_PERIMETER_RAMP,
            plan.getManagedIntents()
                .get(0)
                .getKind());
        assertEquals(
            ManagedQuarryIntentKind.PLACE_APPROVED_LIGHT,
            plan.getManagedIntents()
                .get(1)
                .getKind());
        assertThrows(
            UnsupportedOperationException.class,
            () -> plan.getIntents()
                .clear());
        assertThrows(
            UnsupportedOperationException.class,
            () -> plan.getManagedIntents()
                .clear());
    }

    @Test
    public void cleanVolumeNeverIntroducesManagedFillerRampOrLight() {
        CylinderExcavationSpec spec = spec(ExcavationMode.CLEAN_VOLUME, 1, 7, 7);
        ExcavationPlanningWindow window = ExcavationTestSupport.window(
            spec,
            CylinderExcavationGeometry.initialFrontier(spec),
            3,
            1L,
            2L,
            ExcavationBlockClassification.FLUID_SOURCE_REACHABLE,
            ExcavationBlockClassification.FLUID_SOURCE_UNREACHABLE,
            ExcavationBlockClassification.FLUID_FLOWING);

        ExcavationPlan plan = ExcavationPlanner.calculate(spec, window, null);

        assertEquals(
            Arrays.asList(
                ExcavationIntentKind.CLEAR_FLUID_SOURCE,
                ExcavationIntentKind.MARK_UNREACHABLE,
                ExcavationIntentKind.MARK_UNREACHABLE),
            kinds(plan));
        assertTrue(
            plan.getManagedIntents()
                .isEmpty());
        assertFalse(
            plan.getIntents()
                .get(1)
                .getApprovedMaterial()
                .isPresent());
        assertThrows(
            IllegalArgumentException.class,
            () -> ExcavationPlanner.calculate(spec, window, ManagedQuarryConfiguration.defaults()));
    }

    @Test
    public void managedInfrastructureIsEmittedOncePerLayerWithDeterministicLightCadence() {
        CylinderExcavationSpec spec = spec(ExcavationMode.MANAGED_QUARRY, 0, 8, 9);
        ExcavationPlanningWindow window = ExcavationTestSupport.uniformWindow(
            spec,
            CylinderExcavationGeometry.initialFrontier(spec),
            2,
            3L,
            4L,
            ExcavationBlockClassification.BREAKABLE);
        ManagedQuarryConfiguration configuration = new ManagedQuarryConfiguration("ramp", "light", "filler", 1);

        ExcavationPlan plan = ExcavationPlanner.calculate(spec, window, configuration);

        assertEquals(
            4,
            plan.getManagedIntents()
                .size());
        assertEquals(
            9,
            plan.getManagedIntents()
                .get(0)
                .getPosition()
                .getY());
        assertEquals(
            9,
            plan.getManagedIntents()
                .get(1)
                .getPosition()
                .getY());
        assertEquals(
            8,
            plan.getManagedIntents()
                .get(2)
                .getPosition()
                .getY());
        assertEquals(
            8,
            plan.getManagedIntents()
                .get(3)
                .getPosition()
                .getY());
        assertEquals(
            "ramp",
            plan.getManagedIntents()
                .get(2)
                .getApprovedMaterial());
        assertEquals(
            "light",
            plan.getManagedIntents()
                .get(3)
                .getApprovedMaterial());
    }

    @Test
    public void snapshotWindowIsAnExactImmutableOrderedBatch() {
        CylinderExcavationSpec spec = spec(ExcavationMode.CLEAN_VOLUME, 1, 5, 5);
        ExcavationTargetBatch batch = CylinderExcavationGeometry
            .nextBatch(spec, CylinderExcavationGeometry.initialFrontier(spec), 2);
        List<ExcavationObservation> observations = new ArrayList<>();
        for (ExcavationTarget target : batch.getTargets()) {
            observations
                .add(new ExcavationObservation(target.getPosition(), ExcavationBlockClassification.BREAKABLE, "stone"));
        }
        ExcavationPlanningWindow window = new ExcavationPlanningWindow(0L, 1L, batch, observations);
        observations.clear();

        assertEquals(
            2,
            window.getObservations()
                .size());
        assertThrows(
            UnsupportedOperationException.class,
            () -> window.getObservations()
                .clear());

        List<ExcavationObservation> reversed = Arrays.asList(
            new ExcavationObservation(
                batch.getTargets()
                    .get(1)
                    .getPosition(),
                ExcavationBlockClassification.BREAKABLE,
                "stone"),
            new ExcavationObservation(
                batch.getTargets()
                    .get(0)
                    .getPosition(),
                ExcavationBlockClassification.BREAKABLE,
                "stone"));
        assertThrows(IllegalArgumentException.class, () -> new ExcavationPlanningWindow(0L, 1L, batch, reversed));
        assertThrows(
            IllegalArgumentException.class,
            () -> new ExcavationPlanningWindow(0L, 1L, batch, Collections.singletonList(reversed.get(0))));
    }

    @Test
    public void managedModeRequiresExplicitApprovedMaterials() {
        CylinderExcavationSpec managed = spec(ExcavationMode.MANAGED_QUARRY, 0, 5, 5);
        ExcavationPlanningWindow window = ExcavationTestSupport.uniformWindow(
            managed,
            CylinderExcavationGeometry.initialFrontier(managed),
            1,
            1L,
            1L,
            ExcavationBlockClassification.AIR);

        assertThrows(IllegalArgumentException.class, () -> ExcavationPlanner.calculate(managed, window, null));
        assertThrows(IllegalArgumentException.class, () -> new ManagedQuarryConfiguration("", "light", "fill", 4));
        assertThrows(IllegalArgumentException.class, () -> new ManagedQuarryConfiguration("ramp", "light", "fill", 0));
    }

    private static CylinderExcavationSpec spec(ExcavationMode mode, int radius, int bottomY, int topY) {
        return new CylinderExcavationSpec(0, -16, -16, radius, bottomY, topY, mode);
    }

    private static List<ExcavationIntentKind> kinds(ExcavationPlan plan) {
        List<ExcavationIntentKind> result = new ArrayList<>();
        for (ExcavationIntent intent : plan.getIntents()) {
            result.add(intent.getKind());
        }
        return result;
    }
}
