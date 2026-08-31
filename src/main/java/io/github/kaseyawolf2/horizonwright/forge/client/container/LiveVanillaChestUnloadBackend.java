package io.github.kaseyawolf2.horizonwright.forge.client.container;

import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.inventory.ContainerChest;

import io.github.kaseyawolf2.horizonwright.core.action.ActionCapability;
import io.github.kaseyawolf2.horizonwright.core.action.ActionLease;
import io.github.kaseyawolf2.horizonwright.core.container.ContainerTransaction;
import io.github.kaseyawolf2.horizonwright.core.container.ContainerTransactionState;
import io.github.kaseyawolf2.horizonwright.core.logistics.NamedLoadout;
import io.github.kaseyawolf2.horizonwright.core.logistics.StorageItemFilter;
import io.github.kaseyawolf2.horizonwright.runtime.task.UnloadActionHandle;
import io.github.kaseyawolf2.horizonwright.runtime.task.UnloadActionProgress;
import io.github.kaseyawolf2.horizonwright.runtime.task.UnloadActionRequest;
import io.github.kaseyawolf2.horizonwright.runtime.task.UnloadActionState;
import io.github.kaseyawolf2.horizonwright.runtime.task.UnloadBackend;
import io.github.kaseyawolf2.horizonwright.runtime.task.UnloadBackendAvailability;
import io.github.kaseyawolf2.horizonwright.runtime.task.UnloadObservationRequest;
import io.github.kaseyawolf2.horizonwright.runtime.task.UnloadObservationResult;

/** Live adapter for exact vanilla 1.7.10 chest quick-moves. */
public final class LiveVanillaChestUnloadBackend implements UnloadBackend {

    public interface ConfigurationSource {

        Configuration resolve(String loadoutId, String storageId, ContainerChest chest);
    }

    public static final class Configuration {

        private final NamedLoadout loadout;
        private final StorageItemFilter destinationFilter;

        public Configuration(NamedLoadout loadout, StorageItemFilter destinationFilter) {
            if (loadout == null || destinationFilter == null) {
                throw new IllegalArgumentException("loadout and destinationFilter are required");
            }
            this.loadout = loadout;
            this.destinationFilter = destinationFilter;
        }
    }

    private final Minecraft minecraft;
    private final ConfigurationSource configuration;
    private final ConfirmedContainerTransactionExecutor executor;
    private final VanillaChestQuickMovePredictor predictor;

    public LiveVanillaChestUnloadBackend(Minecraft minecraft, ConfigurationSource configuration,
        ConfirmedContainerTransactionExecutor executor) {
        if (minecraft == null || configuration == null || executor == null) {
            throw new IllegalArgumentException("minecraft, configuration, and executor are required");
        }
        this.minecraft = minecraft;
        this.configuration = configuration;
        this.executor = executor;
        predictor = new VanillaChestQuickMovePredictor(new MinecraftContainerSnapshotter());
    }

    @Override
    public UnloadBackendAvailability availability() {
        if (!minecraft.func_152345_ab() || minecraft.thePlayer == null) {
            return UnloadBackendAvailability.unavailable("A joined Minecraft client thread is required");
        }
        return minecraft.thePlayer.openContainer != null
            && minecraft.thePlayer.openContainer.getClass() == ContainerChest.class
                ? UnloadBackendAvailability.available("Exact vanilla 1.7.10 chest adapter ready")
                : UnloadBackendAvailability
                    .unavailable("Open an exact vanilla chest; modded containers are not inferred");
    }

    @Override
    public UnloadObservationResult observe(UnloadObservationRequest request) {
        requireClient(request);
        ContainerChest chest = (ContainerChest) minecraft.thePlayer.openContainer;
        Configuration resolved = configuration.resolve(request.getLoadoutId(), request.getStorageId(), chest);
        NamedLoadout loadout = resolved.loadout;
        StorageItemFilter filter = resolved.destinationFilter;
        VanillaChestQuickMovePredictor.Prediction prediction = predictor.predict(
            chest,
            minecraft.thePlayer.inventory.getItemStack(),
            loadout,
            filter,
            request.getTaskId() + "-r" + request.getCheckpointRevision());
        return new UnloadObservationResult(
            request.getTaskId(),
            request.getCheckpointRevision(),
            request.getActionEpoch(),
            request.getStorageId(),
            loadout,
            prediction.getPlayerSlots(),
            filter,
            prediction.getPredictions());
    }

    @Override
    public UnloadActionHandle execute(UnloadActionRequest request, ActionLease lease) {
        requireClient(request);
        requireLease(request, lease);
        executor.begin(request.getTransaction());
        return new Handle(request.getRequestId(), request.getTransaction(), executor);
    }

    private void requireClient(Object request) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        UnloadBackendAvailability current = availability();
        if (!current.isAvailable()) {
            throw new IllegalStateException(current.getDiagnostic());
        }
    }

    private static void requireLease(UnloadActionRequest request, ActionLease lease) {
        Set<ActionCapability> capabilities = lease == null ? null : lease.getCapabilities();
        if (lease == null || !lease.isValid()
            || lease.getEpoch() != request.getActionEpoch()
            || capabilities == null
            || !capabilities.contains(ActionCapability.CONTAINER)) {
            throw new IllegalStateException("an authoritative CONTAINER lease is required");
        }
    }

    private static final class Handle implements UnloadActionHandle {

        private final String requestId;
        private final ContainerTransaction transaction;
        private final ConfirmedContainerTransactionExecutor executor;

        private Handle(String requestId, ContainerTransaction transaction,
            ConfirmedContainerTransactionExecutor executor) {
            this.requestId = requestId;
            this.transaction = transaction;
            this.executor = executor;
        }

        @Override
        public String getRequestId() {
            return requestId;
        }

        @Override
        public UnloadActionProgress progress() {
            ContainerTransactionState state = transaction.getState();
            if (state == ContainerTransactionState.COMPLETED) {
                return progress(UnloadActionState.CONFIRMED, "Server confirmed the exact unload transaction");
            }
            if (state == ContainerTransactionState.ABORTED) {
                String reason = transaction.getAbortReason();
                UnloadActionState result = reason.startsWith("server rejected") ? UnloadActionState.REJECTED
                    : UnloadActionState.FAILED;
                return progress(result, reason);
            }
            return progress(UnloadActionState.EXECUTING, "Awaiting exact server confirmation and synchronized state");
        }

        @Override
        public void cancel() {
            executor.cancel(transaction, "unload task released its live transaction");
        }

        private UnloadActionProgress progress(UnloadActionState state, String detail) {
            return new UnloadActionProgress(requestId, state, detail);
        }
    }
}
