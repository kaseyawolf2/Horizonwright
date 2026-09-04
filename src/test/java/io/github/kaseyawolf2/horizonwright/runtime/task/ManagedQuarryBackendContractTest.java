package io.github.kaseyawolf2.horizonwright.runtime.task;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.Collections;

import org.junit.Test;

import io.github.kaseyawolf2.horizonwright.core.excavation.CylinderExcavationGeometry;
import io.github.kaseyawolf2.horizonwright.core.excavation.CylinderExcavationSpec;
import io.github.kaseyawolf2.horizonwright.core.excavation.ExcavationBlockClassification;
import io.github.kaseyawolf2.horizonwright.core.excavation.ExcavationFrontier;
import io.github.kaseyawolf2.horizonwright.core.excavation.ExcavationMode;
import io.github.kaseyawolf2.horizonwright.core.excavation.ExcavationObservation;
import io.github.kaseyawolf2.horizonwright.core.excavation.ExcavationPlan;
import io.github.kaseyawolf2.horizonwright.core.excavation.ExcavationPlanner;
import io.github.kaseyawolf2.horizonwright.core.excavation.ExcavationPlanningWindow;
import io.github.kaseyawolf2.horizonwright.core.excavation.ExcavationTargetBatch;
import io.github.kaseyawolf2.horizonwright.core.excavation.ManagedQuarryConfiguration;
import io.github.kaseyawolf2.horizonwright.core.excavation.ManagedQuarryIntent;

public class ManagedQuarryBackendContractTest {

    @Test
    public void requestAndConfirmationCarryExactRevisionEpochFrontierAndIntent() {
        CylinderExcavationSpec spec = spec();
        ExcavationFrontier frontier = CylinderExcavationGeometry.initialFrontier(spec);
        ManagedQuarryIntent intent = managedIntent(spec, frontier);
        ManagedQuarryObservationRequest observation = new ManagedQuarryObservationRequest(
            "quarry",
            spec.getDimensionId(),
            3L,
            7L,
            spec.getGeometryKey(),
            frontier,
            intent);
        ManagedQuarryActionRequest action = new ManagedQuarryActionRequest(
            "quarry-ramp-3",
            observation,
            intent,
            "minecraft:air@0");
        ConfirmedManagedQuarryResult confirmation = new ConfirmedManagedQuarryResult(
            action.getTaskRevision(),
            action.getActionEpoch(),
            action.getGeometryKey(),
            action.getStartFrontier(),
            action.getIntent(),
            "minecraft:cobblestone@0",
            "minecraft:cobblestone");
        ManagedQuarryActionProgress progress = new ManagedQuarryActionProgress(
            action.getRequestId(),
            ExcavationActionState.CONFIRMED,
            "server-confirmed approved material",
            confirmation);

        assertEquals(intent, action.getIntent());
        assertEquals(frontier, confirmation.getStartFrontier());
        assertEquals("minecraft:air@0", action.getObservedFingerprint());
        assertTrue(
            progress.getConfirmation()
                .isPresent());
        assertEquals(
            "minecraft:cobblestone@0",
            progress.getConfirmation()
                .get()
                .getObservedFingerprint());
    }

    @Test
    public void confirmationEvidenceCannotAppearOnNonConfirmedState() {
        CylinderExcavationSpec spec = spec();
        ExcavationFrontier frontier = CylinderExcavationGeometry.initialFrontier(spec);
        ManagedQuarryIntent intent = managedIntent(spec, frontier);
        ConfirmedManagedQuarryResult confirmation = new ConfirmedManagedQuarryResult(
            1L,
            2L,
            spec.getGeometryKey(),
            frontier,
            intent,
            "minecraft:cobblestone@0",
            "minecraft:cobblestone");

        assertThrows(
            IllegalArgumentException.class,
            () -> new ManagedQuarryActionProgress(
                "request",
                ExcavationActionState.EXECUTING,
                "not done",
                confirmation));
        ManagedQuarryActionProgress executing = new ManagedQuarryActionProgress(
            "request",
            ExcavationActionState.EXECUTING,
            "placing",
            null);
        assertFalse(
            executing.getConfirmation()
                .isPresent());
    }

    @Test
    public void unsupportedBackendsFailClosedBeforeInfrastructureActions() {
        ExcavationBackend backend = new ExcavationBackend() {

            @Override
            public ExcavationBackendAvailability availability() {
                return ExcavationBackendAvailability.available("ordinary excavation");
            }

            @Override
            public ExcavationObservationResult observe(ExcavationObservationRequest request) {
                throw new UnsupportedOperationException();
            }

            @Override
            public ExcavationActionHandle execute(
                io.github.kaseyawolf2.horizonwright.runtime.task.ExcavationActionRequest request,
                io.github.kaseyawolf2.horizonwright.core.action.ActionLease actionLease) {
                throw new UnsupportedOperationException();
            }
        };

        assertFalse(
            backend.managedQuarryAvailability()
                .isAvailable());
        assertThrows(UnsupportedOperationException.class, () -> backend.observeManagedQuarry(null));
    }

    private static CylinderExcavationSpec spec() {
        return new CylinderExcavationSpec(0, 8, 8, 1, 12, 12, ExcavationMode.MANAGED_QUARRY);
    }

    private static ManagedQuarryIntent managedIntent(CylinderExcavationSpec spec, ExcavationFrontier frontier) {
        ExcavationTargetBatch batch = CylinderExcavationGeometry.nextBatch(spec, frontier, 1);
        ExcavationPlan plan = ExcavationPlanner.calculate(
            spec,
            new ExcavationPlanningWindow(
                1L,
                2L,
                batch,
                Collections.singletonList(
                    new ExcavationObservation(
                        batch.getTargets()
                            .get(0)
                            .getPosition(),
                        ExcavationBlockClassification.BREAKABLE,
                        "minecraft:stone@0"))),
            ManagedQuarryConfiguration.defaults());
        return plan.getManagedIntents()
            .get(0);
    }
}
