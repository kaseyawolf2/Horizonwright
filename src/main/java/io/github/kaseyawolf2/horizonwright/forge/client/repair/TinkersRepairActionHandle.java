package io.github.kaseyawolf2.horizonwright.forge.client.repair;

import io.github.kaseyawolf2.horizonwright.core.container.ContainerTransaction;
import io.github.kaseyawolf2.horizonwright.core.container.ContainerTransactionState;
import io.github.kaseyawolf2.horizonwright.forge.client.container.ConfirmedContainerTransactionExecutor;
import io.github.kaseyawolf2.horizonwright.runtime.task.RepairActionConfirmation;
import io.github.kaseyawolf2.horizonwright.runtime.task.RepairActionHandle;
import io.github.kaseyawolf2.horizonwright.runtime.task.RepairActionProgress;
import io.github.kaseyawolf2.horizonwright.runtime.task.RepairActionRequest;
import io.github.kaseyawolf2.horizonwright.runtime.task.RepairActionState;

/** Maps one exact transaction and synchronized evidence source onto the durable repair handle contract. */
final class TinkersRepairActionHandle implements RepairActionHandle {

    interface ConfirmationSource {

        RepairActionConfirmation confirm(RepairActionRequest request);
    }

    private final RepairActionRequest request;
    private final ConfirmedContainerTransactionExecutor executor;
    private final ConfirmationSource confirmations;
    private RepairActionConfirmation confirmation;
    private RuntimeException confirmationFailure;

    TinkersRepairActionHandle(RepairActionRequest request, ConfirmedContainerTransactionExecutor executor,
        ConfirmationSource confirmations) {
        if (request == null || executor == null || confirmations == null) {
            throw new IllegalArgumentException("request, executor, and confirmations are required");
        }
        this.request = request;
        this.executor = executor;
        this.confirmations = confirmations;
    }

    @Override
    public String getRequestId() {
        return request.getRequestId();
    }

    @Override
    public synchronized RepairActionProgress progress() {
        ContainerTransaction transaction = request.getTransaction();
        ContainerTransactionState state = transaction.getState();
        if (state == ContainerTransactionState.ABORTED) {
            String reason = transaction.getAbortReason();
            RepairActionState result = reason.startsWith("server rejected") ? RepairActionState.REJECTED
                : RepairActionState.FAILED;
            return progress(result, reason, null);
        }
        if (state != ContainerTransactionState.COMPLETED) {
            return progress(
                RepairActionState.EXECUTING,
                "Awaiting exact server confirmation and synchronized repair state",
                null);
        }
        if (confirmation == null && confirmationFailure == null) {
            try {
                confirmation = confirmations.confirm(request);
                if (confirmation == null) throw new IllegalStateException("confirmation source returned no evidence");
            } catch (RuntimeException failure) {
                confirmationFailure = failure;
            }
        }
        if (confirmationFailure != null) {
            return progress(
                RepairActionState.FAILED,
                "Repair confirmation failed: " + confirmationFailure.getMessage(),
                null);
        }
        return progress(RepairActionState.CONFIRMED, "Verified repaired tool in its reserved slot", confirmation);
    }

    @Override
    public void cancel() {
        executor.cancel(request.getTransaction(), "repair task released its live transaction");
    }

    private RepairActionProgress progress(RepairActionState state, String detail,
        RepairActionConfirmation currentConfirmation) {
        return new RepairActionProgress(request.getRequestId(), state, detail, currentConfirmation);
    }
}
