package io.github.kaseyawolf2.horizonwright.forge.client.container;

import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.inventory.Container;

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

        Configuration resolve(String loadoutId, String storageId, Container chest);

        default io.github.kaseyawolf2.horizonwright.core.persistence.NamedLocation location(String storageId) {
            return null;
        }

        default boolean matches(String storageId, Container container) {
            return false;
        }

        default boolean bindOpened(String storageId, Container container) {
            return false;
        }
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
    private io.github.kaseyawolf2.horizonwright.core.action.ActionSessionGuard accessGuard;
    private java.util.function.Supplier<io.github.kaseyawolf2.horizonwright.core.navigation.NavigationBackend> navigation;

    public LiveVanillaChestUnloadBackend(Minecraft minecraft, ConfigurationSource configuration,
        ConfirmedContainerTransactionExecutor executor,
        io.github.kaseyawolf2.horizonwright.core.action.ActionSessionGuard guard,
        java.util.function.Supplier<io.github.kaseyawolf2.horizonwright.core.navigation.NavigationBackend> navigation) {
        this(minecraft, configuration, executor);
        this.accessGuard = guard;
        this.navigation = navigation;
    }

    @Override
    public UnloadActionHandle accessStorage(String id, String storageId, long epoch, ActionLease lease) {
        if (accessGuard == null || navigation == null) return null;
        return new LiveStorageAccess(
            minecraft,
            accessGuard,
            navigation.get(),
            configuration,
            id,
            storageId,
            epoch,
            lease);
    }

    @Override
    public UnloadActionHandle closeStorage(String id, String storageId, long epoch, ActionLease lease) {
        if (accessGuard == null) return null;
        return new LiveStorageClose(minecraft, accessGuard, configuration, id, storageId, epoch, lease);
    }

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
        return SupportedChestLayout.supports(minecraft.thePlayer.openContainer)
            ? UnloadBackendAvailability.available("Integrated chest adapter ready")
            : UnloadBackendAvailability.unavailable(
                "Open the saved vanilla, Iron Chests, or Et Futurum chest; other storage integrations are not ready");
    }

    @Override
    public UnloadObservationResult observe(UnloadObservationRequest request) {
        requireClient(request);
        Container chest = (Container) minecraft.thePlayer.openContainer;
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
