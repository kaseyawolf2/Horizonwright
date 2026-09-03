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
