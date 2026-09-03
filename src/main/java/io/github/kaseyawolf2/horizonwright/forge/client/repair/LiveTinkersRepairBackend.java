package io.github.kaseyawolf2.horizonwright.forge.client.repair;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

import io.github.kaseyawolf2.horizonwright.DevelopmentTrace;
import io.github.kaseyawolf2.horizonwright.core.action.ActionCapability;
import io.github.kaseyawolf2.horizonwright.core.action.ActionLease;
import io.github.kaseyawolf2.horizonwright.core.action.ActionSessionGuard;
import io.github.kaseyawolf2.horizonwright.core.container.ContainerSnapshot;
import io.github.kaseyawolf2.horizonwright.core.container.ContainerTransaction;
import io.github.kaseyawolf2.horizonwright.core.container.ItemFingerprint;
import io.github.kaseyawolf2.horizonwright.core.logistics.LoadoutReservation;
import io.github.kaseyawolf2.horizonwright.core.logistics.LoadoutRole;
import io.github.kaseyawolf2.horizonwright.core.logistics.NamedLoadout;
import io.github.kaseyawolf2.horizonwright.core.navigation.NavigationBackend;
import io.github.kaseyawolf2.horizonwright.core.navigation.NavigationHandle;
import io.github.kaseyawolf2.horizonwright.core.navigation.NavigationProgress;
import io.github.kaseyawolf2.horizonwright.core.navigation.NavigationRequest;
import io.github.kaseyawolf2.horizonwright.core.navigation.NavigationState;
import io.github.kaseyawolf2.horizonwright.core.persistence.NamedLocation;
import io.github.kaseyawolf2.horizonwright.core.repair.RepairToolSnapshot;
import io.github.kaseyawolf2.horizonwright.forge.client.MinecraftRuntimeAccess;
import io.github.kaseyawolf2.horizonwright.forge.client.container.ConfirmedContainerTransactionExecutor;
import io.github.kaseyawolf2.horizonwright.forge.client.container.MinecraftContainerSnapshotter;
import io.github.kaseyawolf2.horizonwright.forge.client.network.ActionPacketDispatch;
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

        NamedLocation resolveLocation(String stationId);
    }

    public interface NavigationSource {

        NavigationBackend getNavigationBackend();
    }

    private final Minecraft minecraft;
    private final ActionSessionGuard guard;
    private final NavigationSource navigationSource;
    private final ConfigurationSource configuration;
    private final ConfirmedContainerTransactionExecutor executor;
    private final TinkersRepairCompatibilityStatus compatibility;
    private final TinkersRepairContainerAdapter adapter = new TinkersRepairContainerAdapter();
    private final MinecraftContainerSnapshotter snapshots = new MinecraftContainerSnapshotter();
    private final TinkersRepairTransactionPredictor predictor = new TinkersRepairTransactionPredictor(
        adapter,
        snapshots);
    private StationAccessHandle activeStationAccess;
    private InputStagingHandle activeInputStaging;

    public LiveTinkersRepairBackend(Minecraft minecraft, ActionSessionGuard guard, NavigationSource navigationSource,
        ConfigurationSource configuration, ConfirmedContainerTransactionExecutor executor,
        TinkersRepairCompatibilityStatus compatibility) {
        if (minecraft == null || guard == null
            || navigationSource == null
            || configuration == null
            || executor == null
            || compatibility == null) {
            throw new IllegalArgumentException("complete live repair dependencies are required");
        }
        this.minecraft = minecraft;
        this.guard = guard;
        this.navigationSource = navigationSource;
        this.configuration = configuration;
        this.executor = executor;
        this.compatibility = compatibility;
    }

    @Override
    public synchronized StationAccessHandle accessStation(StationAccessRequest request, ActionLease lease) {
        if (request == null) throw new IllegalArgumentException("station access request is required");
        if (!compatibility.isAvailable()) throw new IllegalStateException(compatibility.getDiagnostic());
        if (!minecraft.func_152345_ab() || minecraft.thePlayer == null
            || minecraft.theWorld == null
            || minecraft.playerController == null) {
            throw new IllegalStateException("a joined Minecraft client thread is required");
        }
        requireStationAccessLease(request, lease);
        if (activeStationAccess != null) {
            throw new IllegalStateException("another repair-station access operation is already active");
        }
        NavigationBackend navigation = navigationSource.getNavigationBackend();
        if (navigation == null || !navigation.availability()
            .isAvailable()) {
            throw new IllegalStateException(
                navigation == null ? "No navigation backend is configured"
                    : navigation.availability()
                        .getDiagnostic());
        }
        NamedLocation location = configuration.resolveLocation(request.getStationId());
        LiveStationAccessHandle handle = new LiveStationAccessHandle(request, lease, navigation, location);
        activeStationAccess = handle;
        try {
            handle.start();
            return handle;
        } catch (RuntimeException failure) {
            activeStationAccess = null;
            handle.cancel();
            throw failure;
        }
    }

    @Override
    public synchronized InputStagingHandle stageInputs(RepairObservationRequest request, ActionLease lease) {
        requireClient(request);
        requireStagingLease(request, lease);
        if (activeInputStaging != null) {
            throw new IllegalStateException("another repair input-staging operation is already active");
        }
        Container container = minecraft.thePlayer.openContainer;
        NamedLoadout loadout = configuration.resolve(request.getStationId(), container);
        TinkersRepairContainerAdapter.Layout layout = TinkersRepairContainerAdapter
            .requirePinnedLayout(container, minecraft.thePlayer.inventory);
        if (minecraft.thePlayer.inventory.getItemStack() != null) {
            throw new IllegalStateException("repair input staging requires an empty cursor");
        }
        TinkersRepairContainerInspection inspection = adapter
            .inspect(container, minecraft.thePlayer.inventory, request.getReservedInventorySlot());
        if (isRepairPreviewReady(inspection)) return null;

        int toolSource = -1;
        ItemStack stationTool = slot(container, layout.getInputSlot()).getStack();
        if (stationTool == null) {
            toolSource = TinkersRepairContainerAdapter
                .containerSlotForPlayerInventory(layout, request.getReservedInventorySlot());
            if (slot(container, toolSource).getStack() == null) {
                throw new IllegalStateException("reserved inventory slot has no damaged tool to stage");
            }
            adapter.readTool(slot(container, toolSource).getStack(), request.getReservedInventorySlot());
        } else {
            adapter.readTool(stationTool, request.getReservedInventorySlot());
        }

        int materialTarget = approvedStationMaterialSlot(container, layout, loadout);
        int materialSource = -1;
        if (materialTarget < 0) {
            materialTarget = firstEmptyMaterialSlot(container, layout);
            if (materialTarget < 0) {
                throw new IllegalStateException(
                    "repair station has no empty material slot and no valid repair preview");
            }
            materialSource = approvedMaterialSource(container, layout, request.getReservedInventorySlot(), loadout);
            if (materialSource < 0) {
                throw new IllegalStateException(
                    "no inventory stack matches the registered REPAIR_MATERIAL reservation; inventory was not changed");
            }
        }
        LiveInputStagingHandle handle = new LiveInputStagingHandle(
            request,
            lease,
            loadout,
            layout,
            toolSource,
            materialSource,
            materialTarget);
        activeInputStaging = handle;
        try {
            handle.start();
            return handle;
        } catch (RuntimeException failure) {
            activeInputStaging = null;
            handle.cancel();
            throw failure;
        }
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
                    : RepairBackendAvailability
                        .waitingForOperator("Waiting for the exact pinned Tool Station or Tool Forge to be opened");
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
        if (slot(container, layout.getInputSlot()).getStack() == null) {
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
        if (slot(container, layout.getOutputSlot()).getStack() != null) {
            throw new IllegalStateException("station output exists without a corresponding input tool");
        }
        ItemStack tool = minecraft.thePlayer.inventory.getStackInSlot(request.getReservedInventorySlot());
        if (tool == null) {
            throw new IllegalStateException("station input and reserved tool slot are both empty");
        }
        RepairToolSnapshot toolEvidence = adapter.readTool(tool, request.getReservedInventorySlot());
        RepairObservationResult result = new RepairObservationResult(
            request.getTaskId(),
            request.getCheckpointRevision(),
            request.getActionEpoch(),
            request.getStationId(),
            container.windowId,
            layout.getStationSlotCount(),
            TinkersRepairContainerAdapter.containerSlotForPlayerInventory(layout, request.getReservedInventorySlot()),
            java.util.Collections.<Integer>emptyList(),
            true,
            toolEvidence,
            null,
            0,
            null);
        traceObservation("returned-tool-observed", result);
        return result;
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
        if (!guard.isReadyForSession()) {
            throw new IllegalStateException("previous action packets are still draining before repair execution");
        }
        guard.begin(lease);
        try {
            executor.begin(request.getTransaction());
            return new TinkersRepairActionHandle(
                request,
                executor,
                new LiveConfirmationSource(minecraft, adapter, snapshots),
                () -> stopActionSession(lease));
        } catch (RuntimeException failure) {
            stopActionSession(lease);
            throw failure;
        }
    }

    private void stopActionSession(ActionLease lease) {
        guard.quarantine(lease);
        guard.end(lease);
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

    private static void requireStagingLease(RepairObservationRequest request, ActionLease lease) {
        Set<ActionCapability> capabilities = lease == null ? null : lease.getCapabilities();
        if (lease == null || !lease.isValid()
            || lease.getEpoch() != request.getActionEpoch()
            || capabilities == null
            || !capabilities.contains(ActionCapability.CONTAINER)) {
            throw new IllegalStateException("an authoritative CONTAINER lease is required for repair input staging");
        }
    }

    private boolean isRepairPreviewReady(TinkersRepairContainerInspection inspection) {
        if (inspection == null || inspection.getStatus() != TinkersRepairContainerInspection.Status.RECOGNIZED) {
            return false;
        }
        TinkersRepairContainerEvidence evidence = inspection.getEvidence()
            .get();
        return evidence.getPredictedOutput() != null && evidence.getPredictedMaterialConsumed() > 0;
    }

    private int approvedMaterialSource(Container container, TinkersRepairContainerAdapter.Layout layout,
        int reservedInventorySlot, NamedLoadout loadout) {
        if (layout.getChestSlotStart() >= 0) {
            int chestSource = approvedMaterialInRange(
                container,
                layout.getChestSlotStart(),
                container.inventorySlots.size(),
                loadout,
                "attached-chest");
            if (chestSource >= 0) return chestSource;
        }
        for (int inventorySlot = 0; inventorySlot < 36; inventorySlot++) {
            if (inventorySlot == reservedInventorySlot) continue;
            int containerSlot = TinkersRepairContainerAdapter.containerSlotForPlayerInventory(layout, inventorySlot);
            ItemStack stack = slot(container, containerSlot).getStack();
            ItemFingerprint fingerprint = snapshots.fingerprint(stack);
            if (fingerprint == null) continue;
            for (LoadoutReservation reservation : loadout.getReservations()) {
                if (reservation.getRole() == LoadoutRole.REPAIR_MATERIAL && reservation.matches(fingerprint)) {
                    DevelopmentTrace.event(
                        "repair-input-staging",
                        "material-selected",
                        "inventorySlot",
                        inventorySlot,
                        "containerSlot",
                        containerSlot,
                        "item",
                        fingerprint,
                        "reservation",
                        reservation.getId());
                    return containerSlot;
                }
            }
        }
        return -1;
    }

    private int approvedMaterialInRange(Container container, int start, int end, NamedLoadout loadout, String source) {
        for (int containerSlot = start; containerSlot < end; containerSlot++) {
            ItemFingerprint fingerprint = snapshots.fingerprint(slot(container, containerSlot).getStack());
            if (fingerprint == null) continue;
            for (LoadoutReservation reservation : loadout.getReservations()) {
                if (reservation.getRole() == LoadoutRole.REPAIR_MATERIAL && reservation.matches(fingerprint)) {
                    DevelopmentTrace.event(
                        "repair-input-staging",
                        "material-selected",
                        "source",
                        source,
                        "containerSlot",
                        containerSlot,
                        "item",
                        fingerprint,
                        "reservation",
                        reservation.getId());
                    return containerSlot;
                }
            }
        }
        return -1;
    }

    private int approvedStationMaterialSlot(Container container, TinkersRepairContainerAdapter.Layout layout,
        NamedLoadout loadout) {
        for (int containerSlot : layout.getMaterialSlots()) {
            ItemFingerprint fingerprint = snapshots.fingerprint(slot(container, containerSlot).getStack());
            if (fingerprint == null) continue;
            for (LoadoutReservation reservation : loadout.getReservations()) {
                if (reservation.getRole() == LoadoutRole.REPAIR_MATERIAL && reservation.matches(fingerprint)) {
                    DevelopmentTrace.event(
                        "repair-input-staging",
                        "station-material-reused",
                        "containerSlot",
                        containerSlot,
                        "item",
                        fingerprint,
                        "reservation",
                        reservation.getId());
                    return containerSlot;
                }
            }
        }
        return -1;
    }

    private static int firstEmptyMaterialSlot(Container container, TinkersRepairContainerAdapter.Layout layout) {
        for (int materialSlot : layout.getMaterialSlots()) {
            if (slot(container, materialSlot).getStack() == null) return materialSlot;
        }
        return -1;
    }

    private static net.minecraft.inventory.Slot slot(Container container, int index) {
        Object value = container.inventorySlots.get(index);
        if (!(value instanceof net.minecraft.inventory.Slot)) {
            throw new IllegalStateException("container slot " + index + " is not a Minecraft Slot");
        }
        return (net.minecraft.inventory.Slot) value;
    }

    private static void requireStationAccessLease(StationAccessRequest request, ActionLease lease) {
        Set<ActionCapability> capabilities = lease == null ? null : lease.getCapabilities();
        if (lease == null || !lease.isValid()
            || lease.getEpoch() != request.getActionEpoch()
            || capabilities == null
            || !capabilities.contains(ActionCapability.MOVEMENT)
            || !capabilities.contains(ActionCapability.LOOK)
            || !capabilities.contains(ActionCapability.USE)) {
            throw new IllegalStateException("an authoritative MOVEMENT + LOOK + USE lease is required");
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

    private final class LiveInputStagingHandle implements InputStagingHandle {

        private static final long SETTLE_NANOS = 150_000_000L;
        private static final long TIMEOUT_NANOS = 20_000_000_000L;

        private enum Phase {
            PICK_UP_TOOL,
            PLACE_TOOL,
            PICK_UP_MATERIAL,
            PLACE_MATERIAL,
            WAIT_FOR_PREVIEW
        }

        private final RepairObservationRequest request;
        private final ActionLease lease;
        private final NamedLoadout loadout;
        private final TinkersRepairContainerAdapter.Layout layout;
        private final int toolSource;
        private final int materialSource;
        private final int materialTarget;
        private final int windowId;
        private final ItemFingerprint tool;
        private final ItemFingerprint material;
        private final long deadlineNanos = saturatingAdd(System.nanoTime(), TIMEOUT_NANOS);
        private final String requestId;
        private Phase phase;
        private InputStagingState state = InputStagingState.SUBMITTED;
        private String detail = "Preparing repair inputs";
        private boolean clickSubmitted;
        private long settleAfterNanos;
        private boolean ownsActionSession;

        private LiveInputStagingHandle(RepairObservationRequest request, ActionLease lease, NamedLoadout loadout,
            TinkersRepairContainerAdapter.Layout layout, int toolSource, int materialSource, int materialTarget) {
            this.request = request;
            this.lease = lease;
            this.loadout = loadout;
            this.layout = layout;
            this.toolSource = toolSource;
            this.materialSource = materialSource;
            this.materialTarget = materialTarget;
            Container container = minecraft.thePlayer.openContainer;
            windowId = container.windowId;
            ItemStack toolStack = toolSource < 0 ? slot(container, layout.getInputSlot()).getStack()
                : slot(container, toolSource).getStack();
            tool = snapshots.fingerprint(toolStack);
            material = snapshots
                .fingerprint(slot(container, materialSource < 0 ? materialTarget : materialSource).getStack());
            requestId = request.getTaskId() + "-input-staging-r" + request.getCheckpointRevision();
            phase = toolSource >= 0 ? Phase.PICK_UP_TOOL
                : materialSource >= 0 ? Phase.PICK_UP_MATERIAL : Phase.WAIT_FOR_PREVIEW;
        }

        private void start() {
            if (!guard.isReadyForSession()) {
                throw new IllegalStateException("previous action packets are still draining before input staging");
            }
            guard.begin(lease);
            ownsActionSession = true;
            state = InputStagingState.EXECUTING;
            detail = toolSource < 0
                ? materialSource < 0 ? "Tool and approved station material are staged; waiting for repair preview"
                    : "Tool is already staged; preparing repair material"
                : "Preparing to move the damaged tool into the repair station";
            trace(
                "started",
                "toolSource",
                toolSource,
                "materialSource",
                materialSource,
                "materialTarget",
                materialTarget,
                "tool",
                tool,
                "material",
                material);
        }

        @Override
        public String getRequestId() {
            return requestId;
        }

        @Override
        public synchronized InputStagingProgress progress() {
            if (isTerminal()) return snapshot();
            if (!minecraft.func_152345_ab() || minecraft.thePlayer == null
                || minecraft.thePlayer.openContainer == null
                || minecraft.playerController == null) {
                fail("Minecraft client or repair container became unavailable");
                return snapshot();
            }
            if (!lease.isValid()) {
                fail("Repair input-staging lease was revoked");
                return snapshot();
            }
            if (System.nanoTime() - deadlineNanos >= 0L) {
                fail("Timed out waiting for synchronized Tool Station input state during " + phase);
                return snapshot();
            }
            Container container = minecraft.thePlayer.openContainer;
            if (container.windowId != windowId) {
                fail("Open container changed during repair input staging");
                return snapshot();
            }
            try {
                configuration.resolve(request.getStationId(), container);
                TinkersRepairContainerAdapter.requirePinnedLayout(container, minecraft.thePlayer.inventory);
                advance(container);
            } catch (RuntimeException failure) {
                fail(
                    failure.getClass()
                        .getSimpleName() + ": "
                        + failure.getMessage());
            }
            return snapshot();
        }

        @Override
        public synchronized void cancel() {
            if (isTerminal()) return;
            state = InputStagingState.CANCELLED;
            detail = "Repair input staging was cancelled; any synchronized items remain visible in the station";
            trace("cancelled", "phase", phase);
            stopActionSession();
            clearInputStaging(this);
        }

        private void advance(Container container) {
            switch (phase) {
                case PICK_UP_TOOL:
                    if (!clickSubmitted) {
                        dispatch(toolSource, "Picking up the damaged tool from its reserved inventory slot");
                    } else if (settled() && slot(container, toolSource).getStack() == null
                        && tool.equals(snapshots.fingerprint(minecraft.thePlayer.inventory.getItemStack()))) {
                            next(Phase.PLACE_TOOL, "Damaged tool picked up; moving it into the Tool Station input");
                        }
                    break;
                case PLACE_TOOL:
                    if (!clickSubmitted) {
                        dispatch(layout.getInputSlot(), "Placing the damaged tool in the Tinkers repair input");
                    } else if (settled() && minecraft.thePlayer.inventory.getItemStack() == null
                        && tool.equals(snapshots.fingerprint(slot(container, layout.getInputSlot()).getStack()))) {
                            next(
                                materialSource < 0 ? Phase.WAIT_FOR_PREVIEW : Phase.PICK_UP_MATERIAL,
                                materialSource < 0 ? "Damaged tool staged; waiting for repair preview"
                                    : "Damaged tool staged; preparing compatible repair material");
                        }
                    break;
                case PICK_UP_MATERIAL:
                    if (!clickSubmitted) {
                        dispatch(materialSource, "Picking up the registered repair material");
                    } else if (settled() && slot(container, materialSource).getStack() == null
                        && material.equals(snapshots.fingerprint(minecraft.thePlayer.inventory.getItemStack()))) {
                            next(Phase.PLACE_MATERIAL, "Repair material picked up; moving it into the station");
                        }
                    break;
                case PLACE_MATERIAL:
                    if (!clickSubmitted) {
                        dispatch(materialTarget, "Placing the registered repair material in the station");
                    } else if (settled() && minecraft.thePlayer.inventory.getItemStack() == null
                        && material.equals(snapshots.fingerprint(slot(container, materialTarget).getStack()))) {
                            next(
                                Phase.WAIT_FOR_PREVIEW,
                                "Repair inputs staged; waiting for synchronized repair preview");
                        }
                    break;
                case WAIT_FOR_PREVIEW:
                    TinkersRepairContainerInspection inspection = adapter
                        .inspect(container, minecraft.thePlayer.inventory, request.getReservedInventorySlot());
                    if (isRepairPreviewReady(inspection)) {
                        TinkersRepairContainerEvidence evidence = inspection.getEvidence()
                            .get();
                        state = InputStagingState.CONFIRMED;
                        detail = "Tool Station produced a repair preview using approved material";
                        trace(
                            "confirmed",
                            "predictedDamage",
                            evidence.getPredictedOutput()
                                .getDamage(),
                            "predictedConsumed",
                            evidence.getPredictedMaterialConsumed());
                        stopActionSession();
                        clearInputStaging(this);
                    } else {
                        detail = "Waiting for the Tool Station to produce a compatible repair preview";
                    }
                    break;
                default:
                    throw new IllegalStateException("unknown repair input-staging phase");
            }
        }

        private void dispatch(int containerSlot, String nextDetail) {
            minecraft.playerController.windowClick(windowId, containerSlot, 0, 0, minecraft.thePlayer);
            clickSubmitted = true;
            settleAfterNanos = saturatingAdd(System.nanoTime(), SETTLE_NANOS);
            detail = nextDetail;
            trace(
                "click",
                "phase",
                phase,
                "slot",
                containerSlot,
                "cursor",
                snapshots.fingerprint(minecraft.thePlayer.inventory.getItemStack()));
        }

        private boolean settled() {
            return System.nanoTime() - settleAfterNanos >= 0L;
        }

        private void next(Phase next, String nextDetail) {
            trace("phase-complete", "phase", phase, "next", next);
            phase = next;
            detail = nextDetail;
            clickSubmitted = false;
        }

        private void fail(String message) {
            state = InputStagingState.FAILED;
            detail = message;
            trace(
                "failed",
                "phase",
                phase,
                "detail",
                message,
                "cursor",
                minecraft.thePlayer == null ? "unavailable"
                    : snapshots.fingerprint(minecraft.thePlayer.inventory.getItemStack()));
            stopActionSession();
            clearInputStaging(this);
        }

        private void stopActionSession() {
            if (!ownsActionSession) return;
            LiveTinkersRepairBackend.this.stopActionSession(lease);
            ownsActionSession = false;
        }

        private boolean isTerminal() {
            return state == InputStagingState.CONFIRMED || state == InputStagingState.CANCELLED
                || state == InputStagingState.FAILED;
        }

        private InputStagingProgress snapshot() {
            return new InputStagingProgress(requestId, state, detail);
        }

        private void trace(String event, Object... fields) {
            Object[] prefixed = new Object[fields.length + 8];
            prefixed[0] = "request";
            prefixed[1] = requestId;
            prefixed[2] = "station";
            prefixed[3] = request.getStationId();
            prefixed[4] = "window";
            prefixed[5] = windowId;
            prefixed[6] = "loadout";
            prefixed[7] = loadout.getId();
            System.arraycopy(fields, 0, prefixed, 8, fields.length);
            DevelopmentTrace.event("repair-input-staging", event, prefixed);
        }
    }

    private synchronized void clearInputStaging(InputStagingHandle expected) {
        if (activeInputStaging == expected) activeInputStaging = null;
    }

    private final class LiveStationAccessHandle implements StationAccessHandle {

        private static final double MAX_INTERACTION_DISTANCE = 3.25D;
        private static final long TIMEOUT_NANOS = TimeUnit.MINUTES.toNanos(2L);

        private final StationAccessRequest request;
        private final ActionLease lease;
        private final NavigationBackend navigation;
        private final NamedLocation location;
        private final long deadlineNanos = saturatingAdd(System.nanoTime(), TIMEOUT_NANOS);
        private NavigationHandle navigationHandle;
        private StationAccessState state = StationAccessState.SUBMITTED;
        private String detail = "Preparing repair-station approach";
        private boolean ownsActionSession;
        private boolean interactionSubmitted;
        private boolean interactionDispatched;
        private boolean cancellationRequested;

        private LiveStationAccessHandle(StationAccessRequest request, ActionLease lease, NavigationBackend navigation,
            NamedLocation location) {
            this.request = request;
            this.lease = lease;
            this.navigation = navigation;
            this.location = location;
        }

        private void start() {
            requireCurrentDimension();
            if (canReach()) {
                state = StationAccessState.INTERACTING;
                detail = "Registered repair station is within confirmed reach";
                trace("phase", "reach", true);
                return;
            }
            navigationHandle = navigation.submit(
                NavigationRequest.adjacentTo(
                    request.getRequestId() + "-approach",
                    request.getActionEpoch(),
                    location.getDimensionId(),
                    location.getX(),
                    location.getY(),
                    location.getZ(),
                    System.nanoTime(),
                    TIMEOUT_NANOS),
                lease);
            state = StationAccessState.APPROACHING;
            detail = "Baritone is approaching the registered repair station";
            trace("phase", "navigationRequest", navigationHandle.getRequestId());
        }

        @Override
        public String getRequestId() {
            return request.getRequestId();
        }

        @Override
        public synchronized StationAccessProgress progress() {
            if (isTerminal()) return snapshot();
            if (cancellationRequested) {
                cancelOnClientThread();
                return snapshot();
            }
            if (!minecraft.func_152345_ab() || minecraft.thePlayer == null
                || minecraft.theWorld == null
                || minecraft.playerController == null) {
                fail("Minecraft client or world became unavailable");
                return snapshot();
            }
            if (!lease.isValid()) {
                fail("Repair-station access lease was revoked");
                return snapshot();
            }
            if (System.nanoTime() - deadlineNanos >= 0L) {
                fail("Repair-station access deadline exceeded");
                return snapshot();
            }
            if (state == StationAccessState.APPROACHING) pollApproach();
            else if (state == StationAccessState.INTERACTING) interactWhenReady();
            return snapshot();
        }

        @Override
        public void cancel() {
            cancellationRequested = true;
            NavigationHandle moving;
            synchronized (this) {
                moving = navigationHandle;
            }
            if (moving != null) moving.cancel();
            if (minecraft.func_152345_ab()) cancelOnClientThread();
            else minecraft.func_152344_a(this::cancelOnClientThread);
        }

        private void pollApproach() {
            NavigationProgress progress = navigationHandle.progress();
            trace("approach", "state", progress.getState(), "detail", progress.getDetail());
            if (progress.getState() == NavigationState.COMPLETED) {
                navigationHandle = null;
                state = StationAccessState.INTERACTING;
                detail = "Repair-station approach complete; waiting for packet drain";
            } else if (progress.getState() == NavigationState.FAILED) {
                fail("Could not approach repair station: " + progress.getDetail());
            } else if (progress.getState() == NavigationState.CANCELLED) {
                state = StationAccessState.CANCELLED;
                detail = "Repair-station approach was cancelled";
                clearStationAccess(this);
            } else {
                detail = "Approaching repair station: " + progress.getDetail();
            }
        }

        private void interactWhenReady() {
            if (interactionDispatched) {
                confirmContainer();
                return;
            }
            if (interactionSubmitted) {
                detail = "Waiting for the repair-station packet boundary";
                return;
            }
            if (!guard.isReadyForSession()) {
                detail = "Waiting for approach packets to drain";
                return;
            }
            requireCurrentDimension();
            TileEntity tile = MinecraftRuntimeAccess
                .tileEntity(minecraft.theWorld, location.getX(), location.getY(), location.getZ());
            MovingObjectPosition hit = rayTraceStation();
            if (tile == null || hit == null || !canReach()) {
                fail("Registered repair station is missing or not reachable after approach");
                return;
            }
            guard.begin(lease);
            ownsActionSession = true;
            aimAtStation();
            boolean accepted = minecraft.playerController.onPlayerRightClick(
                minecraft.thePlayer,
                minecraft.theWorld,
                MinecraftRuntimeAccess.heldItem(minecraft.thePlayer),
                location.getX(),
                location.getY(),
                location.getZ(),
                hit.sideHit,
                hit.hitVec);
            trace(
                "interaction",
                "accepted",
                accepted,
                "tile",
                tile.getClass()
                    .getName(),
                "side",
                hit.sideHit);
            if (!accepted) {
                fail("Minecraft rejected the registered repair-station interaction");
                return;
            }
            interactionSubmitted = true;
            detail = "Dispatching the repair-station interaction";
            ActionPacketDispatch.afterPendingWrites(minecraft, () -> {
                synchronized (LiveStationAccessHandle.this) {
                    stopActionSession();
                    interactionDispatched = true;
                }
            });
        }

        private void confirmContainer() {
            Container open = minecraft.thePlayer.openContainer;
            if (open == null || TinkersRepairContainerAdapter.layoutFor(
                open.getClass()
                    .getName())
                == null) {
                detail = "Waiting for the server to open the repair station";
                return;
            }
            try {
                configuration.resolve(request.getStationId(), open);
            } catch (RuntimeException mismatch) {
                fail("Opened repair container did not match the registered station: " + mismatch.getMessage());
                return;
            }
            state = StationAccessState.CONFIRMED;
            detail = "Registered repair station opened and confirmed";
            trace(
                "confirmed",
                "container",
                open.getClass()
                    .getName(),
                "window",
                open.windowId);
            clearStationAccess(this);
        }

        private void requireCurrentDimension() {
            if (minecraft.theWorld.provider == null
                || minecraft.theWorld.provider.dimensionId != location.getDimensionId()) {
                throw new IllegalStateException("registered repair station is in another dimension");
            }
        }

        private boolean canReach() {
            EntityPlayer player = minecraft.thePlayer;
            Vec3 eyes = Vec3
                .createVectorHelper(player.posX, player.posY + MinecraftRuntimeAccess.eyeHeight(player), player.posZ);
            Vec3 center = Vec3
                .createVectorHelper(location.getX() + 0.5D, location.getY() + 0.5D, location.getZ() + 0.5D);
            double reach = Math.min(minecraft.playerController.getBlockReachDistance(), MAX_INTERACTION_DISTANCE);
            return eyes.squareDistanceTo(center) <= reach * reach && rayTraceStation() != null;
        }

        private MovingObjectPosition rayTraceStation() {
            EntityPlayer player = minecraft.thePlayer;
            Vec3 eyes = Vec3
                .createVectorHelper(player.posX, player.posY + MinecraftRuntimeAccess.eyeHeight(player), player.posZ);
            Vec3 center = Vec3
                .createVectorHelper(location.getX() + 0.5D, location.getY() + 0.5D, location.getZ() + 0.5D);
            MovingObjectPosition hit = MinecraftRuntimeAccess.rayTraceBlocks(minecraft.theWorld, eyes, center, false);
            return hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
                && hit.blockX == location.getX()
                && hit.blockY == location.getY()
                && hit.blockZ == location.getZ() ? hit : null;
        }

        private void aimAtStation() {
            Entity player = minecraft.thePlayer;
            double dx = location.getX() + 0.5D - player.posX;
            double dy = location.getY() + 0.5D - (player.posY + MinecraftRuntimeAccess.eyeHeight(player));
            double dz = location.getZ() + 0.5D - player.posZ;
            player.rotationYaw = (float) (Math.atan2(dz, dx) * 180.0D / Math.PI) - 90.0F;
            player.rotationPitch = (float) -(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * 180.0D / Math.PI);
        }

        private void fail(String message) {
            stopProducers();
            state = StationAccessState.FAILED;
            detail = message;
            trace("failed", "detail", message);
            clearStationAccess(this);
        }

        private void cancelOnClientThread() {
            synchronized (this) {
                if (isTerminal()) return;
                stopProducers();
                state = StationAccessState.CANCELLED;
                detail = "Repair-station access cancelled";
                clearStationAccess(this);
            }
        }

        private void stopProducers() {
            if (navigationHandle != null) {
                navigationHandle.cancel();
                navigationHandle = null;
            }
            stopActionSession();
        }

        private void stopActionSession() {
            if (!ownsActionSession) return;
            guard.quarantine(lease);
            guard.end(lease);
            ownsActionSession = false;
        }

        private boolean isTerminal() {
            return state == StationAccessState.CONFIRMED || state == StationAccessState.CANCELLED
                || state == StationAccessState.FAILED;
        }

        private StationAccessProgress snapshot() {
            return new StationAccessProgress(request.getRequestId(), state, detail);
        }

        private void trace(String event, Object... fields) {
            Object[] prefixed = new Object[fields.length + 6];
            prefixed[0] = "request";
            prefixed[1] = request.getRequestId();
            prefixed[2] = "station";
            prefixed[3] = request.getStationId();
            prefixed[4] = "position";
            prefixed[5] = location.getDimensionId() + ":"
                + location.getX()
                + ","
                + location.getY()
                + ","
                + location.getZ();
            System.arraycopy(fields, 0, prefixed, 6, fields.length);
            DevelopmentTrace.event("repair-station-access", event, prefixed);
        }
    }

    private synchronized void clearStationAccess(StationAccessHandle expected) {
        if (activeStationAccess == expected) activeStationAccess = null;
    }

    private static long saturatingAdd(long left, long right) {
        long result = left + right;
        return ((left ^ result) & (right ^ result)) < 0L ? Long.MAX_VALUE : result;
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
            TinkersRepairContainerAdapter.Layout layout = stationLayout(expected.getContainerType());
            int consumed = consumedMaterials(
                clicks.get(0)
                    .getExpectedBefore(),
                expected,
                layout);
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
            minecraft.thePlayer.closeScreen();
            DevelopmentTrace.event(
                "repair-live",
                "station-closed",
                "request",
                request.getRequestId(),
                "window",
                expected.getWindowId());
            return confirmation;
        }

        private static TinkersRepairContainerAdapter.Layout stationLayout(String containerType) {
            TinkersRepairContainerAdapter.Layout layout = TinkersRepairContainerAdapter.layoutFor(containerType);
            if (layout == null) throw new IllegalStateException("repair container type is no longer recognized");
            return layout;
        }

        private static int consumedMaterials(ContainerSnapshot before, ContainerSnapshot after,
            TinkersRepairContainerAdapter.Layout layout) {
            int consumed = 0;
            for (int slot : layout.getMaterialSlots()) {
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
