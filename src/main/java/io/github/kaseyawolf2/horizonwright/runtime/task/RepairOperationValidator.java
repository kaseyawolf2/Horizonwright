package io.github.kaseyawolf2.horizonwright.runtime.task;

import java.util.HashSet;
import java.util.Set;

import io.github.kaseyawolf2.horizonwright.core.container.ContainerSnapshot;
import io.github.kaseyawolf2.horizonwright.core.container.ContainerTransaction;
import io.github.kaseyawolf2.horizonwright.core.container.VerifiedContainerClick;
import io.github.kaseyawolf2.horizonwright.core.repair.RepairVerification;
import io.github.kaseyawolf2.horizonwright.core.repair.TinkersRepairVerifier;

/** Independent semantic validation of a pinned-layout repair prediction. */
final class RepairOperationValidator {

    private RepairOperationValidator() {}

    static void validate(RepairObservationResult observation, int reservedInventorySlot) {
        if (observation == null || observation.getTransaction() == null || observation.getPredictedOutput() == null) {
            throw new IllegalArgumentException("a predicted repair transaction is required");
        }
        if (!observation.isRecognizedLayout()
            || (observation.getStationSlotCount() != 4 && observation.getStationSlotCount() != 5)) {
            throw new IllegalArgumentException("repair prediction is not from a pinned Tool Station or Tool Forge");
        }
        if (observation.getInputTool()
            .getReservedInventorySlot() != reservedInventorySlot
            || observation.getPredictedOutput()
                .getReservedInventorySlot() != reservedInventorySlot) {
            throw new IllegalArgumentException("repair prediction is not bound to the configured reserved slot");
        }
        RepairVerification verification = TinkersRepairVerifier.verify(
            observation.getInputTool(),
            observation.getPredictedOutput(),
            observation.getPredictedMaterialConsumed(),
            true);
        if (!verification.isAccepted()) {
            throw new IllegalArgumentException("predicted repair was rejected: " + verification.getDiagnostic());
        }

        ContainerTransaction transaction = observation.getTransaction();
        if (transaction.getActionEpoch() != observation.getActionEpoch()) {
            throw new IllegalArgumentException("repair transaction belongs to another action epoch");
        }
        Set<Integer> allowed = new HashSet<>();
        for (int slot = 0; slot < observation.getStationSlotCount(); slot++) allowed.add(slot);
        allowed.add(observation.getReservedContainerSlot());
        allowed.addAll(observation.getApprovedMaterialContainerSlots());
        for (VerifiedContainerClick click : transaction.getClicks()) {
            ContainerSnapshot before = click.getExpectedBefore();
            ContainerSnapshot after = click.getExpectedAfter();
            if (before.getWindowId() != observation.getWindowId() || after.getWindowId() != observation.getWindowId()
                || observation.getReservedContainerSlot() >= before.getSlots()
                    .size()
                || !allowed.contains(click.getSlot())) {
                throw new IllegalArgumentException("repair transaction touches an unapproved slot or window");
            }
            for (Integer materialSlot : observation.getApprovedMaterialContainerSlots()) {
                if (materialSlot >= before.getSlots()
                    .size()) {
                    throw new IllegalArgumentException("approved repair material slot is outside the container");
                }
            }
        }
    }
}
