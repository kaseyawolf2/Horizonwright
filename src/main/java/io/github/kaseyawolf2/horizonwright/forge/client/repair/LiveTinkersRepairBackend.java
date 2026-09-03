package io.github.kaseyawolf2.horizonwright.forge.client.repair;

import java.util.List;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;

import io.github.kaseyawolf2.horizonwright.DevelopmentTrace;
import io.github.kaseyawolf2.horizonwright.core.action.ActionCapability;
import io.github.kaseyawolf2.horizonwright.core.action.ActionLease;
import io.github.kaseyawolf2.horizonwright.core.container.ContainerSnapshot;
import io.github.kaseyawolf2.horizonwright.core.container.ContainerTransaction;
import io.github.kaseyawolf2.horizonwright.core.container.ItemFingerprint;
import io.github.kaseyawolf2.horizonwright.core.logistics.LoadoutReservation;
import io.github.kaseyawolf2.horizonwright.core.logistics.LoadoutRole;
import io.github.kaseyawolf2.horizonwright.core.logistics.NamedLoadout;
import io.github.kaseyawolf2.horizonwright.core.repair.RepairToolSnapshot;
import io.github.kaseyawolf2.horizonwright.forge.client.container.ConfirmedContainerTransactionExecutor;
import io.github.kaseyawolf2.horizonwright.forge.client.container.MinecraftContainerSnapshotter;
import io.github.kaseyawolf2.horizonwright.runtime.task.RepairActionConfirmation;
import io.github.kaseyawolf2.horizonwright.runtime.task.RepairActionHandle;
import io.github.kaseyawolf2.horizonwright.runtime.task.RepairActionRequest;
import io.github.kaseyawolf2.horizonwright.runtime.task.RepairBackend;
import io.github.kaseyawolf2.horizonwright.runtime.task.RepairBackendAvailability;
import io.github.kaseyawolf2.horizonwright.runtime.task.RepairObservationRequest;
import io.github.kaseyawolf2.horizonwright.runtime.task.RepairObservationResult;

/** Exact-version live backend for a prepared TConstruct Tool Station or Tool Forge. */
public final class LiveTinkersRepairBackend implements RepairBackend {

    public interface ConfigurationSource {

        NamedLoadout resolve(String stationId, Container container);
    }

    private final Minecraft minecraft;
    private final ConfigurationSource configuration;
    private final ConfirmedContainerTransactionExecutor executor;
    private final TinkersRepairCompatibilityStatus compatibility;
    private final TinkersRepairContainerAdapter adapter = new TinkersRepairContainerAdapter();
    private final MinecraftContainerSnapshotter snapshots = new MinecraftContainerSnapshotter();
    private final TinkersRepairTransactionPredictor predictor = new TinkersRepairTransactionPredictor(
        adapter,
        snapshots);

    public LiveTinkersRepairBackend(Minecraft minecraft, ConfigurationSource configuration,
        ConfirmedContainerTransactionExecutor executor, TinkersRepairCompatibilityStatus compatibility) {
        if (minecraft == null || configuration == null || executor == null || compatibility == null) {
            throw new IllegalArgumentException("complete live repair dependencies are required");
        }
        this.minecraft = minecraft;
        this.configuration = configuration;
        this.executor = executor;
        this.compatibility = compatibility;
    }

    @Override
    public RepairBackendAvailability availability() {
        RepairBackendAvailability result;
        if (!compatibility.isAvailable()) {
            result = RepairBackendAvailability.unavailable(compatibility.getDiagnostic());
        } else if (!minecraft.func_152345_ab() || minecraft.thePlayer == null) {
            result = RepairBackendAvailability.unavailable("A joined Minecraft client thread is required");
        } else {
            Container open = minecraft.thePlayer.openContainer;
            result = open != null && TinkersRepairContainerAdapter.layoutFor(
                open.getClass()
                    .getName())
                != null ? RepairBackendAvailability.available(compatibility.getDiagnostic())
                    : RepairBackendAvailability.unavailable("Open the exact pinned Tool Station or Tool Forge");
        }
        DevelopmentTrace.event(
            "repair-live",
            "availability",
            "available",
            result.isAvailable(),
            "diagnostic",
            result.getDiagnostic(),
            "container",
            minecraft.thePlayer == null || minecraft.thePlayer.openContainer == null ? "none"
                : minecraft.thePlayer.openContainer.getClass()
                    .getName());
        return result;
    }

    @Override
    public RepairObservationResult observe(RepairObservationRequest request) {
        requireClient(request);
        Container container = minecraft.thePlayer.openContainer;
        DevelopmentTrace.event(
            "repair-live",
            "observe-start",
            "task",
            request.getTaskId(),
            "revision",
            request.getCheckpointRevision(),
            "epoch",
            request.getActionEpoch(),
            "station",
            request.getStationId(),
            "reservedSlot",
            request.getReservedInventorySlot(),
            "window",
            container.windowId,
            "container",
            container.getClass()
                .getName());
        NamedLoadout loadout = configuration.resolve(request.getStationId(), container);
        TinkersRepairContainerAdapter.Layout layout = TinkersRepairContainerAdapter
            .requirePinnedLayout(container, minecraft.thePlayer.inventory);
        if (minecraft.thePlayer.inventory.getItemStack() != null) {
            throw new IllegalStateException("repair observation requires an empty cursor");
        }
        if (((net.minecraft.inventory.Slot) container.inventorySlots.get(1)).getStack() == null) {
            return observeReturnedTool(request, container, loadout, layout);
        }
        String transactionId = request.getTaskId() + "-repair-r" + request.getCheckpointRevision();
        TinkersRepairTransactionPredictor.Prediction prediction = predictor.predict(
            container,
            minecraft.thePlayer.inventory,
            request.getReservedInventorySlot(),
            loadout,
            transactionId,
            request.getActionEpoch());
        TinkersRepairContainerEvidence evidence = prediction.getEvidence();
        ContainerTransaction transaction = prediction.getTransaction();
        RepairObservationResult result = new RepairObservationResult(
            request.getTaskId(),
            request.getCheckpointRevision(),
            request.getActionEpoch(),
            request.getStationId(),
            evidence.getWindowId(),
            evidence.getStationSlotCount(),
            evidence.getReservedContainerSlot(),
            prediction.getApprovedMaterialSlots(),
            true,
            evidence.getInputTool(),
            transaction == null ? null : evidence.getPredictedOutput(),
            transaction == null ? 0 : evidence.getPredictedMaterialConsumed(),
            transaction);
        traceObservation("observed", result);
        return result;
    }

    private RepairObservationResult observeReturnedTool(RepairObservationRequest request, Container container,
        NamedLoadout loadout, TinkersRepairContainerAdapter.Layout layout) {
        if (((net.minecraft.inventory.Slot) container.inventorySlots.get(0)).getStack() != null) {
            throw new IllegalStateException("station output exists without a corresponding input tool");
        }
        ItemStack tool = minecraft.thePlayer.inventory.getStackInSlot(request.getReservedInventorySlot());
        if (tool == null) {
            throw new IllegalStateException("station input and reserved tool slot are both empty");
        }
        ItemFingerprint fingerprint = snapshots.fingerprint(tool);
        requireToolReservation(loadout, fingerprint);
        RepairToolSnapshot toolEvidence = adapter.readTool(tool, request.getReservedInventorySlot());
        RepairObservationResult result = new RepairObservationResult(
            request.getTaskId(),
            request.getCheckpointRevision(),
            request.getActionEpoch(),
            request.getStationId(),
            container.windowId,
            layout.getStationSlotCount(),
            TinkersRepairContainerAdapter
                .containerSlotForPlayerInventory(layout.getStationSlotCount(), request.getReservedInventorySlot()),
            java.util.Collections.<Integer>emptyList(),
            true,
            toolEvidence,
            null,
            0,
            null);
        traceObservation("returned-tool-observed", result);
        return result;
    }

    private static void requireToolReservation(NamedLoadout loadout, ItemFingerprint tool) {
        for (LoadoutReservation reservation : loadout.getReservations()) {
            if (reservation.getRole() == LoadoutRole.TOOL && reservation.matches(tool)) return;
        }
        throw new IllegalStateException("reserved returned tool is not approved by loadout '" + loadout.getId() + "'");
    }

    @Override
    public RepairActionHandle execute(RepairActionRequest request, ActionLease lease) {
        requireClient(request);
        requireLease(request, lease);
        DevelopmentTrace.event(
            "repair-live",
            "execute",
            "request",
            request.getRequestId(),
            "revision",
            request.getCheckpointRevision(),
            "epoch",
            request.getActionEpoch(),
            "transaction",
            request.getTransaction()
                .getTransactionId(),
            "clicks",
            request.getTransaction()
                .getClicks()
                .size(),
            "inputDamage",
            request.getInputTool()
                .getDamage(),
            "inputMaximumDamage",
            request.getInputTool()
                .getMaximumDamage());
        executor.begin(request.getTransaction());
        return new TinkersRepairActionHandle(
            request,
            executor,
            new LiveConfirmationSource(minecraft, adapter, snapshots));
    }

    private void requireClient(Object request) {
        if (request == null) throw new IllegalArgumentException("request must not be null");
        RepairBackendAvailability current = availability();
        if (!current.isAvailable()) throw new IllegalStateException(current.getDiagnostic());
    }

    private static void requireLease(RepairActionRequest request, ActionLease lease) {
        Set<ActionCapability> capabilities = lease == null ? null : lease.getCapabilities();
        if (lease == null || !lease.isValid()
            || lease.getEpoch() != request.getActionEpoch()
            || capabilities == null
            || !capabilities.contains(ActionCapability.CONTAINER)) {
            throw new IllegalStateException("an authoritative CONTAINER lease is required");
        }
    }

    private static void traceObservation(String event, RepairObservationResult result) {
        DevelopmentTrace.event(
            "repair-live",
            event,
            "task",
            result.getTaskId(),
            "revision",
            result.getCheckpointRevision(),
            "epoch",
            result.getActionEpoch(),
            "station",
            result.getStationId(),
            "window",
            result.getWindowId(),
            "stationSlots",
            result.getStationSlotCount(),
            "reservedContainerSlot",
            result.getReservedContainerSlot(),
            "materialSlots",
            result.getApprovedMaterialContainerSlots(),
            "inputDamage",
            result.getInputTool()
                .getDamage(),
            "inputMaximumDamage",
            result.getInputTool()
                .getMaximumDamage(),
            "predictedDamage",
            result.getPredictedOutput() == null ? "none"
                : result.getPredictedOutput()
                    .getDamage(),
            "predictedConsumed",
            result.getPredictedMaterialConsumed(),
            "transaction",
            result.getTransaction() == null ? "none"
                : result.getTransaction()
                    .getTransactionId());
    }

    private static final class LiveConfirmationSource implements TinkersRepairActionHandle.ConfirmationSource {

        private final Minecraft minecraft;
        private final TinkersRepairContainerAdapter adapter;
        private final MinecraftContainerSnapshotter snapshots;

        private LiveConfirmationSource(Minecraft minecraft, TinkersRepairContainerAdapter adapter,
            MinecraftContainerSnapshotter snapshots) {
            this.minecraft = minecraft;
            this.adapter = adapter;
            this.snapshots = snapshots;
        }

        @Override
        public RepairActionConfirmation confirm(RepairActionRequest request) {
            DevelopmentTrace.event("repair-live", "confirm-start", "request", request.getRequestId());
            if (!minecraft.func_152345_ab() || minecraft.thePlayer == null
                || minecraft.thePlayer.openContainer == null) {
                throw new IllegalStateException("joined client and repair container are no longer available");
            }
            ContainerTransaction transaction = request.getTransaction();
            List<io.github.kaseyawolf2.horizonwright.core.container.VerifiedContainerClick> clicks = transaction
                .getClicks();
            ContainerSnapshot expected = clicks.get(clicks.size() - 1)
                .getExpectedAfter();
            ContainerSnapshot actual = snapshots.capture(
                minecraft.thePlayer.openContainer,
                minecraft.thePlayer.inventory.getItemStack(),
                expected.getRevision());
            if (!expected.equals(actual)) {
                throw new IllegalStateException("repair container changed after server confirmation");
            }
            int reserved = request.getInputTool()
                .getReservedInventorySlot();
            ItemStack output = minecraft.thePlayer.inventory.getStackInSlot(reserved);
            if (output == null) throw new IllegalStateException("reserved inventory slot has no repaired tool");
            RepairToolSnapshot outputTool = adapter.readTool(output, reserved);
            int stationSlots = stationSlotCount(expected.getContainerType());
            int consumed = consumedMaterials(
                clicks.get(0)
                    .getExpectedBefore(),
                expected,
                stationSlots);
            RepairActionConfirmation confirmation = new RepairActionConfirmation(
                request.getTransactionFingerprint(),
                outputTool,
                consumed,
                true);
            DevelopmentTrace.event(
                "repair-live",
                "confirmed",
                "request",
                request.getRequestId(),
                "outputDamage",
                outputTool.getDamage(),
                "outputMaximumDamage",
                outputTool.getMaximumDamage(),
                "materialConsumed",
                consumed);
            return confirmation;
        }

        private static int stationSlotCount(String containerType) {
            TinkersRepairContainerAdapter.Layout layout = TinkersRepairContainerAdapter.layoutFor(containerType);
            if (layout == null) throw new IllegalStateException("repair container type is no longer recognized");
            return layout.getStationSlotCount();
        }

        private static int consumedMaterials(ContainerSnapshot before, ContainerSnapshot after, int stationSlots) {
            int consumed = 0;
            for (int slot = 2; slot < stationSlots; slot++) {
                int beforeCount = count(
                    before.getSlots()
                        .get(slot));
                int afterCount = count(
                    after.getSlots()
                        .get(slot));
                if (afterCount > beforeCount) {
                    throw new IllegalStateException("repair material count increased unexpectedly");
                }
                consumed = Math.addExact(consumed, beforeCount - afterCount);
            }
            return consumed;
        }

        private static int count(ItemFingerprint item) {
            return item == null ? 0 : item.getCount();
        }
    }
}
