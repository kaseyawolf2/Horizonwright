package io.github.kaseyawolf2.horizonwright.forge.client.husbandry;

import java.util.EnumSet;
import java.util.concurrent.TimeUnit;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.passive.EntityAnimal;

import io.github.kaseyawolf2.horizonwright.DevelopmentTrace;
import io.github.kaseyawolf2.horizonwright.core.action.ActionCapability;
import io.github.kaseyawolf2.horizonwright.core.action.ActionLease;
import io.github.kaseyawolf2.horizonwright.core.action.ActionSessionGuard;
import io.github.kaseyawolf2.horizonwright.core.base.AnimalObservation;
import io.github.kaseyawolf2.horizonwright.core.base.BasePosition;
import io.github.kaseyawolf2.horizonwright.core.base.HusbandryAction;
import io.github.kaseyawolf2.horizonwright.core.base.HusbandryActionKind;
import io.github.kaseyawolf2.horizonwright.core.base.HusbandryDropObservation;
import io.github.kaseyawolf2.horizonwright.core.base.HusbandryObservation;
import io.github.kaseyawolf2.horizonwright.core.base.HusbandryPlan;
import io.github.kaseyawolf2.horizonwright.core.base.HusbandryPlanner;
import io.github.kaseyawolf2.horizonwright.core.base.HusbandryPolicy;
import io.github.kaseyawolf2.horizonwright.core.navigation.BackendAvailability;
import io.github.kaseyawolf2.horizonwright.core.navigation.NavigationBackend;
import io.github.kaseyawolf2.horizonwright.core.navigation.NavigationHandle;
import io.github.kaseyawolf2.horizonwright.core.navigation.NavigationProgress;
import io.github.kaseyawolf2.horizonwright.core.navigation.NavigationRequest;
import io.github.kaseyawolf2.horizonwright.core.navigation.NavigationState;
import io.github.kaseyawolf2.horizonwright.forge.client.MinecraftRuntimeAccess;
import io.github.kaseyawolf2.horizonwright.forge.client.network.ActionPacketDispatch;
import io.github.kaseyawolf2.horizonwright.runtime.task.HusbandryActionAuthorization;
import io.github.kaseyawolf2.horizonwright.runtime.task.HusbandryBackend;

/** Vanilla husbandry with explicit per-request authorization and fresh validation before each attack. */
public final class LiveVanillaHusbandryBackend implements HusbandryBackend {

    public interface NavigationSource {

        NavigationBackend getNavigationBackend();
    }

    private static final EnumSet<ActionCapability> FEED_CAPABILITIES = EnumSet.of(
        ActionCapability.MOVEMENT,
        ActionCapability.LOOK,
        ActionCapability.USE,
        ActionCapability.HELD_USE,
        ActionCapability.CONTAINER);
    private static final long APPROACH_TIMEOUT_NANOS = NavigationRequest.MAX_RUNTIME_NANOS;
    private static final EnumSet<ActionCapability> CULL_CAPABILITIES = EnumSet.of(
        ActionCapability.MOVEMENT,
        ActionCapability.LOOK,
        ActionCapability.ATTACK,
        ActionCapability.HELD_USE,
        ActionCapability.CONTAINER);
    private static final long ACTION_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(30L);
    private static final double ENTITY_REACH_SQUARED = 20.25D;

    private final Minecraft minecraft;
    private final ActionSessionGuard guard;
    private final NavigationSource navigationSource;
    private final MinecraftHusbandryObserver observer;
    private HusbandryObservation lastObservation;
    private LiveHandle active;

    public LiveVanillaHusbandryBackend(Minecraft minecraft, ActionSessionGuard guard, NavigationSource navigationSource,
        ProfileHusbandryConfiguration configuration) {
        if (minecraft == null || guard == null || navigationSource == null || configuration == null) {
            throw new IllegalArgumentException("complete live husbandry dependencies are required");
        }
        this.minecraft = minecraft;
        this.guard = guard;
        this.navigationSource = navigationSource;
        this.observer = new MinecraftHusbandryObserver(minecraft, configuration);
    }

    @Override
    public Availability availability() {
        NavigationBackend navigation = navigationSource.getNavigationBackend();
        if (navigation == null) return Availability.unavailable("No navigation backend is configured for husbandry");
        BackendAvailability status = navigation.availability();
        return status.isAvailable()
            ? Availability.available("Vanilla feeding, collection, and explicitly authorized culling ready")
            : Availability.unavailable("Husbandry navigation unavailable: " + status.getDiagnostic());
    }

    @Override
    public synchronized ObservationSnapshot observe(ObservationRequest request) {
        requireClient(request);
        HusbandryObservation observation = observer.observe(request.getPenId());
        lastObservation = observation;
        return new ObservationSnapshot(
            request.getTaskId(),
            request.getActionEpoch(),
            request.getVerifiedActions(),
            observation);
    }

    @Override
    public synchronized ActionReadiness readiness(HusbandryPlan plan) {
        requireClient(plan);
        HusbandryAction action = firstAction(plan);
        if (action.getKind() == HusbandryActionKind.CULL_EXCESS_ADULT) {
            if (GregTechCullingKnife.best(minecraft.thePlayer.inventory.mainInventory) == null) return ActionReadiness
                .unavailable("Culling requires a usable GregTech knife or butchery knife in player inventory");
            return isFreshCullTarget(plan, action.getAnimalIdentity())
                ? ActionReadiness.ready("Fresh complete pen scan permits this exact cull target")
                : ActionReadiness.unavailable("The selected animal no longer meets the culling policy");
        }
        if (action.getKind() == HusbandryActionKind.COLLECT_DROPS) {
            EntityItem drop = observer.findDrop(
                action.getDropTarget()
                    .getIdentity());
            return observer.matchesDrop(drop, action.getDropTarget()) && withinPen(plan, drop)
                ? ActionReadiness.ready("The exact observed drop is still available")
                : ActionReadiness.unavailable("The exact observed drop is no longer available");
        }
        EntityAnimal animal = observer.findSupportedAnimal(action.getAnimalIdentity());
        VanillaLivestockClassifier.Descriptor descriptor = observer.descriptor(animal);
        if (!eligibleFeedTarget(plan, animal, descriptor)) {
            return ActionReadiness.unavailable("The exact observed adult is no longer eligible for feeding");
        }
        int slot = observer.findBreedingItemSlot(descriptor, 0, 36);
        return slot >= 0 ? ActionReadiness.ready("The exact vanilla breeding item is available")
            : ActionReadiness.unavailable("The required vanilla breeding item is absent from player inventory");
    }

    @Override
    public synchronized ActionHandle execute(ActionRequest request, ActionLease lease) {
        requireClient(request);
        if (active != null && !active.isTerminal())
            throw new IllegalStateException("another husbandry action is active");
        HusbandryPlan plan = request.getPlan();
        HusbandryAction action = firstAction(plan);
        if (!HusbandryActionAuthorization.isAuthorized(action.getKind(), request.isCullingAllowed())) {
            throw new IllegalStateException(HusbandryActionAuthorization.diagnostic(action.getKind()));
        }
        EnumSet<ActionCapability> required = action.getKind() == HusbandryActionKind.FEED_ADULT ? FEED_CAPABILITIES
            : action.getKind() == HusbandryActionKind.CULL_EXCESS_ADULT ? CULL_CAPABILITIES
                : EnumSet.of(ActionCapability.MOVEMENT);
        if (lease == null || !lease.isValid()
            || lease.getEpoch() != request.getActionEpoch()
            || !lease.getCapabilities()
                .containsAll(required)) {
            throw new IllegalArgumentException("matching husbandry action authority is required");
        }
        HusbandryObservation planned = lastObservation;
        HusbandryPolicy policy = new HusbandryPolicy(
            plan.getPen(),
            plan.getSpecies(),
            plan.getPolicyRevision(),
            plan.getMinimumAdults(),
            plan.getMaximumAdults());
        if (planned == null || !plan.isCurrentFor(policy, planned)) {
            throw new IllegalStateException("husbandry plan is stale or belongs to another complete pen scan");
        }
        ActionReadiness ready = readiness(plan);
        if (!ready.isReady()) throw new IllegalStateException(ready.getDiagnostic());
        NavigationBackend navigation = navigationSource.getNavigationBackend();
        Availability available = availability();
        if (navigation == null || !available.isAvailable()) throw new IllegalStateException(available.getDiagnostic());
        LiveHandle handle = new LiveHandle(request, lease, navigation, action);
        active = handle;
        try {
            handle.start();
            return handle;
        } catch (RuntimeException failure) {
            active = null;
            handle.cancel();
            throw failure;
        }
    }

    private static HusbandryAction firstAction(HusbandryPlan plan) {
        if (plan == null || plan.getActions()
            .size() != 1) {
            throw new IllegalArgumentException("one actionable husbandry plan is required");
        }
        return plan.getActions()
            .get(0);
    }

    private boolean isFreshCullTarget(HusbandryPlan original, String identity) {
        HusbandryObservation fresh = observer.observe(original.getPenId());
        HusbandryPolicy policy = new HusbandryPolicy(
            original.getPen(),
            original.getSpecies(),
            original.getPolicyRevision(),
            original.getMinimumAdults(),
            original.getMaximumAdults());
        HusbandryPlan replanned = new HusbandryPlanner().plan(policy, fresh);
        boolean eligible = !replanned.isHeld() && replanned.getActions()
            .size() == 1
            && replanned.getActions()
                .get(0)
                .getKind() == HusbandryActionKind.CULL_EXCESS_ADULT
            && identity.equals(
                replanned.getActions()
                    .get(0)
                    .getAnimalIdentity());
        DevelopmentTrace.event(
            "husbandry-live",
            "cull-revalidation",
            "identity",
            identity,
            "eligible",
            eligible,
            "adults",
            replanned.getObservedAdults(),
            "maximum",
            original.getMaximumAdults(),
            "hold",
            replanned.getHoldReason());
        return eligible;
    }

    private boolean eligibleFeedTarget(HusbandryPlan plan, EntityAnimal animal,
        VanillaLivestockClassifier.Descriptor descriptor) {
        return animal != null && descriptor != null
            && descriptor.getSpecies() == plan.getSpecies()
            && !animal.isDead
            && withinPen(plan, animal)
            && !animal.isChild()
            && !animal.hasCustomNameTag()
            && !animal.isInLove()
            && animal.getGrowingAge() == 0;
    }

    private void requireClient(Object request) {
        if (request == null) throw new IllegalArgumentException("husbandry request is required");
        if (!minecraft.func_152345_ab() || minecraft.thePlayer == null
            || minecraft.theWorld == null
            || minecraft.playerController == null) {
            throw new IllegalStateException("live husbandry requires a joined Minecraft client thread");
        }
    }

    private synchronized void clearActive(LiveHandle handle) {
        if (active == handle) active = null;
    }

    private final class LiveHandle implements ActionHandle {

        private final ActionRequest request;
        private final ActionLease lease;
        private final NavigationBackend navigation;
        private final HusbandryAction action;
        private final int priorHotbarSlot;
        private NavigationHandle navigationHandle;
        private ActionState state = ActionState.SUBMITTED;
        private Phase phase = Phase.APPROACHING;
        private String detail = "Preparing exact husbandry approach";
        private long deadlineNanos;
        private boolean ownsSession;
        private boolean staged;
        private int sourceSlot = -1;
        private int hotbarSlot = -1;
        private boolean interactionDispatched;
        private EntityAnimal cullTarget;
        private long nextAttackNanos;
        private long cullDeadlineNanos;
        private volatile boolean cancellationRequested;

        private LiveHandle(ActionRequest request, ActionLease lease, NavigationBackend navigation,
            HusbandryAction action) {
            this.request = request;
            this.lease = lease;
            this.navigation = navigation;
            this.action = action;
            this.priorHotbarSlot = minecraft.thePlayer.inventory.currentItem;
        }

        private void start() {
            if (action.getKind() == HusbandryActionKind.CULL_EXCESS_ADULT) {
                cullTarget = exactAnimal();
                if (cullTarget == null || cullTarget.getHealth() <= 0.0F) {
                    throw new IllegalStateException("Cull target is no longer a living loaded animal");
                }
                cullDeadlineNanos = add(System.nanoTime(), APPROACH_TIMEOUT_NANOS);
            }
            if (action.getKind() != HusbandryActionKind.COLLECT_DROPS && canReachAnimal()) {
                phase = Phase.WAITING_FOR_SESSION;
                state = ActionState.EXECUTING;
                deadlineNanos = add(System.nanoTime(), ACTION_TIMEOUT_NANOS);
                detail = "Exact adult is within interaction reach";
                return;
            }
            BasePosition position = targetPosition();
            long now = System.nanoTime();
            navigationHandle = navigation.submit(
                new NavigationRequest(
                    request.getRequestId() + "-approach",
                    request.getActionEpoch(),
                    position.getDimensionId(),
                    position.getX(),
                    position.getY(),
                    position.getZ(),
                    action.getKind() == HusbandryActionKind.COLLECT_DROPS ? 0 : 2,
                    now,
                    APPROACH_TIMEOUT_NANOS),
                lease);
            state = ActionState.EXECUTING;
            deadlineNanos = add(now, APPROACH_TIMEOUT_NANOS);
            detail = action.getKind() == HusbandryActionKind.COLLECT_DROPS ? "Moving to collect the exact observed drop"
                : "Approaching the exact adult to feed";
        }

        @Override
        public String getRequestId() {
            return request.getRequestId();
        }

        @Override
        public synchronized ActionProgress progress() {
            requireClient(request);
            trace("progress");
            if (isTerminal()) return snapshot();
            if (cancellationRequested) {
                cancelOnClientThread();
                return snapshot();
            }
            if (!lease.isValid()) {
                fail("Husbandry action lease was revoked");
                return snapshot();
            }
            if (action.getKind() == HusbandryActionKind.CULL_EXCESS_ADULT
                && System.nanoTime() - cullDeadlineNanos >= 0L) {
                fail("Cull deadline exceeded for the selected animal");
                return snapshot();
            }
            if (System.nanoTime() - deadlineNanos >= 0L) {
                fail("Husbandry action deadline exceeded during " + phase);
                return snapshot();
            }
            if (phase == Phase.APPROACHING) pollApproach();
            else if (phase == Phase.WAITING_FOR_SESSION) {
                if (action.getKind() == HusbandryActionKind.CULL_EXCESS_ADULT) cullWhenReady();
                else feedWhenReady();
            } else if (phase == Phase.WAITING_FOR_DISPATCH) awaitDispatch();
            else if (phase == Phase.CONFIRMING) confirm();
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
            if (action.getKind() != HusbandryActionKind.COLLECT_DROPS && canReachAnimal()) {
                navigationHandle.cancel();
                navigationHandle = null;
                phase = Phase.WAITING_FOR_SESSION;
                deadlineNanos = add(System.nanoTime(), ACTION_TIMEOUT_NANOS);
                detail = "Exact adult became reachable";
                return;
            }
            if (action.getKind() == HusbandryActionKind.COLLECT_DROPS && exactDrop() == null) {
                navigationHandle.cancel();
                navigationHandle = null;
                phase = Phase.CONFIRMING;
                deadlineNanos = add(System.nanoTime(), ACTION_TIMEOUT_NANOS);
                detail = "Exact drop disappeared during collection approach";
                return;
            }
            NavigationProgress progress = navigationHandle.progress();
            if (progress.getState() == NavigationState.COMPLETED) {
                navigationHandle = null;
                phase = action.getKind() == HusbandryActionKind.COLLECT_DROPS ? Phase.CONFIRMING
                    : Phase.WAITING_FOR_SESSION;
                deadlineNanos = add(System.nanoTime(), ACTION_TIMEOUT_NANOS);
                detail = action.getKind() == HusbandryActionKind.COLLECT_DROPS
                    ? "Collection position reached; confirming pickup"
                    : "Animal approach completed";
            } else if (progress.getState() == NavigationState.FAILED)
                fail("Could not approach husbandry target: " + progress.getDetail());
            else if (progress.getState() == NavigationState.CANCELLED) {
                state = ActionState.CANCELLED;
                detail = "Husbandry approach was cancelled";
                clearActive(this);
            } else detail = "Approaching husbandry target: " + progress.getDetail();
        }

        private void feedWhenReady() {
            if (!guard.isReadyForSession()) {
                detail = "Waiting for navigation packets to drain";
                return;
            }
            EntityAnimal animal = exactAnimal();
            VanillaLivestockClassifier.Descriptor descriptor = observer.descriptor(animal);
            if (!eligibleFeedTarget(request.getPlan(), animal, descriptor)) {
                fail("Exact adult changed before feeding");
                return;
            }
            if (!canReachAnimal()) {
                fail("Exact adult moved outside interaction reach after approach");
                return;
            }
            hotbarSlot = observer.findBreedingItemSlot(descriptor, 0, 9);
            sourceSlot = hotbarSlot < 0 ? observer.findBreedingItemSlot(descriptor, 9, 36) : -1;
            if (hotbarSlot < 0 && sourceSlot < 0) {
                fail("Required breeding item disappeared before feeding");
                return;
            }
            guard.begin(lease);
            ownsSession = true;
            if (hotbarSlot < 0) {
                if (minecraft.thePlayer.inventory.getItemStack() != null
                    || minecraft.thePlayer.openContainer != minecraft.thePlayer.inventoryContainer) {
                    fail("Cannot safely stage feed while another container or cursor item is active");
                    return;
                }
                hotbarSlot = chooseHotbarSlot();
                minecraft.playerController.windowClick(
                    minecraft.thePlayer.openContainer.windowId,
                    sourceSlot,
                    hotbarSlot,
                    2,
                    minecraft.thePlayer);
                staged = true;
            }
            minecraft.thePlayer.inventory.currentItem = hotbarSlot;
            minecraft.playerController.updateController();
            aimAt(animal.posX, animal.posY + animal.height * 0.5D, animal.posZ);
            boolean accepted = minecraft.playerController.interactWithEntitySendPacket(minecraft.thePlayer, animal);
            minecraft.thePlayer.swingItem();
            if (!accepted) {
                fail("Minecraft rejected the exact vanilla feeding interaction");
                return;
            }
            phase = Phase.WAITING_FOR_DISPATCH;
            interactionDispatched = false;
            detail = "Dispatching exact feeding interaction";
            ActionPacketDispatch.afterPendingWrites(minecraft, () -> {
                synchronized (LiveHandle.this) {
                    returnStaged();
                    stopSession();
                    interactionDispatched = true;
                }
            });
        }

        private void cullWhenReady() {
            if (!request.isCullingAllowed() || !lease.getCapabilities()
                .contains(ActionCapability.ATTACK)) {
                fail("Culling was not authorized for this request");
                return;
            }
            if (!guard.isReadyForSession()) {
                detail = "Waiting for attack/navigation packets to drain";
                return;
            }
            // Entity disappearance alone can mean unloading or leaving the pen, never proof of death.
            if (cullTarget != null && cullTarget.getHealth() <= 0.0F) {
                phase = Phase.CONFIRMING;
                detail = "Selected animal has died; rescanning complete pen";
                return;
            }
            long now = System.nanoTime();
            if (now < nextAttackNanos) return;
            EntityAnimal animal = exactAnimal();
            if (animal == null || animal != cullTarget
                || animal.isDead
                || !isFreshCullTarget(request.getPlan(), action.getAnimalIdentity())) {
                fail("Cull stopped: target protection, population, or loaded pen changed");
                return;
            }
            if (!canReachAnimal()) {
                long approachNow = System.nanoTime();
                navigationHandle = navigation.submit(
                    new NavigationRequest(
                        request.getRequestId() + "-follow",
                        request.getActionEpoch(),
                        minecraft.theWorld.provider.dimensionId,
                        (int) Math.floor(animal.posX),
                        (int) Math.floor(animal.posY),
                        (int) Math.floor(animal.posZ),
                        1,
                        approachNow,
                        Math.min(APPROACH_TIMEOUT_NANOS, cullDeadlineNanos - approachNow)),
                    lease);
                phase = Phase.APPROACHING;
                deadlineNanos = cullDeadlineNanos;
                detail = "Following the selected adult back into visible attack reach";
                return;
            }
            guard.begin(lease);
            ownsSession = true;
            if (!equipCullingKnife()) return;
            aimAt(animal.posX, animal.posY + animal.height * 0.5D, animal.posZ);
            minecraft.playerController.attackEntity(minecraft.thePlayer, animal);
            minecraft.thePlayer.swingItem();
            nextAttackNanos = add(now, TimeUnit.MILLISECONDS.toNanos(600L));
            detail = "Attacked the revalidated excess adult; waiting for server health update";
            trace("cull-attack");
            ActionPacketDispatch.afterPendingWrites(minecraft, () -> {
                synchronized (LiveHandle.this) {
                    // Keep the knife held through server death/drop processing. Only release the session.
                    guard.quarantine(lease);
                    guard.end(lease);
                    ownsSession = false;
                }
            });
        }

        private boolean equipCullingKnife() {
            if (minecraft.thePlayer.inventory.getItemStack() != null
                || minecraft.thePlayer.openContainer != minecraft.thePlayer.inventoryContainer) {
                fail("Close the container and clear the cursor before equipping a culling knife");
                return false;
            }
            GregTechCullingKnife.Candidate best = GregTechCullingKnife
                .best(minecraft.thePlayer.inventory.mainInventory);
            if (staged && best != null && best.slot != hotbarSlot) {
                returnStaged();
                best = GregTechCullingKnife.best(minecraft.thePlayer.inventory.mainInventory);
            }
            if (best == null) {
                fail("Cull stopped: no GregTech knife has enough durability for another hit");
                return false;
            }
            if (best.slot >= 9) {
                returnStaged();
                best = GregTechCullingKnife.best(minecraft.thePlayer.inventory.mainInventory);
                if (best == null) {
                    fail("Cull stopped: knife inventory changed");
                    return false;
                }
                if (best.slot >= 9) {
                    sourceSlot = best.slot;
                    hotbarSlot = chooseHotbarSlot();
                    minecraft.playerController.windowClick(
                        minecraft.thePlayer.openContainer.windowId,
                        sourceSlot,
                        hotbarSlot,
                        2,
                        minecraft.thePlayer);
                    staged = true;
                } else hotbarSlot = best.slot;
            } else hotbarSlot = best.slot;
            minecraft.thePlayer.inventory.currentItem = hotbarSlot;
            minecraft.playerController.updateController();
            GregTechCullingKnife.Candidate held = GregTechCullingKnife
                .inspect(MinecraftRuntimeAccess.heldItem(minecraft.thePlayer), hotbarSlot);
            if (held == null || held.looting != best.looting) {
                fail("Cull stopped: selected knife did not match the verified inventory selection");
                return false;
            }
            DevelopmentTrace.event(
                "husbandry-live",
                "cull-knife",
                "slot",
                hotbarSlot,
                "looting",
                held.looting,
                "remainingDurability",
                held.remaining);
            return true;
        }

        private void awaitDispatch() {
            if (!interactionDispatched) {
                detail = "Waiting for feeding packet boundary";
                return;
            }
            if (!guard.isReadyForSession()) {
                detail = "Waiting for feeding packets to drain";
                return;
            }
            phase = Phase.CONFIRMING;
            detail = "Waiting for a fresh complete pen observation";
        }

        private void confirm() {
            HusbandryObservation after = observer.observe(
                request.getPlan()
                    .getPenId());
            if (action.getKind() == HusbandryActionKind.CULL_EXCESS_ADULT) {
                AnimalObservation remaining = animal(after, action.getAnimalIdentity());
                if (cullTarget == null || cullTarget.getHealth() > 0.0F || remaining != null) {
                    detail = "Waiting for verified death and removal from the complete pen scan";
                    return;
                }
                state = ActionState.CONFIRMED;
                detail = "Selected adult death confirmed; complete pen rescanned before next action";
                stopSession();
                trace("cull-confirmed");
                clearActive(this);
                return;
            }
            if (after.getRevision() <= request.getPlan()
                .getObservationRevision() || after.getObservationFingerprint()
                    .equals(
                        request.getPlan()
                            .getObservationFingerprint())) {
                detail = "Waiting for changed husbandry postcondition";
                return;
            }
            if (action.getKind() == HusbandryActionKind.FEED_ADULT) {
                AnimalObservation animal = animal(after, action.getAnimalIdentity());
                if (animal == null || !animal.isBreedingEngaged()) {
                    detail = "Waiting for the exact adult to enter breeding state";
                    return;
                }
            } else if (drop(
                after,
                action.getDropTarget()
                    .getIdentity())
                != null) {
                    detail = "Waiting for the exact drop to be collected";
                    return;
                }
            state = ActionState.CONFIRMED;
            detail = action.getKind() == HusbandryActionKind.FEED_ADULT
                ? "Exact adult feeding confirmed by a fresh complete pen scan"
                : "Exact drop collection confirmed by a fresh complete pen scan";
            clearActive(this);
        }

        private EntityAnimal exactAnimal() {
            return observer.findSupportedAnimal(action.getAnimalIdentity());
        }

        private EntityItem exactDrop() {
            EntityItem drop = observer.findDrop(
                action.getDropTarget()
                    .getIdentity());
            return observer.matchesDrop(drop, action.getDropTarget()) && withinPen(request.getPlan(), drop) ? drop
                : null;
        }

        private boolean canReachAnimal() {
            EntityAnimal animal = exactAnimal();
            return animal != null && withinPen(request.getPlan(), animal)
                && minecraft.thePlayer.getDistanceSqToEntity(animal)
                    <= (action.getKind() == HusbandryActionKind.CULL_EXCESS_ADULT ? 9.0D : ENTITY_REACH_SQUARED)
                && minecraft.thePlayer.canEntityBeSeen(animal);
        }

        private BasePosition targetPosition() {
            if (action.getKind() == HusbandryActionKind.COLLECT_DROPS) return action.getDropTarget()
                .getPosition();
            AnimalObservation animal = animal(lastObservation, action.getAnimalIdentity());
            if (animal == null) throw new IllegalStateException("planned animal is absent from the complete scan");
            return animal.getPosition();
        }

        private int chooseHotbarSlot() {
            for (int slot = 0; slot < 9; slot++)
                if (minecraft.thePlayer.inventory.mainInventory[slot] == null) return slot;
            return priorHotbarSlot;
        }

        private void returnStaged() {
            if (!staged) return;
            if (action.getKind() == HusbandryActionKind.CULL_EXCESS_ADULT && !ownsSession) return;
            if (minecraft.thePlayer.inventory.getItemStack() != null
                || minecraft.thePlayer.openContainer != minecraft.thePlayer.inventoryContainer) return;
            minecraft.playerController.windowClick(
                minecraft.thePlayer.openContainer.windowId,
                sourceSlot,
                hotbarSlot,
                2,
                minecraft.thePlayer);
            staged = false;
        }

        private void stopSession() {
            if (action.getKind() == HusbandryActionKind.CULL_EXCESS_ADULT && !ownsSession
                && lease.isValid()
                && guard.isReadyForSession()) {
                guard.begin(lease);
                ownsSession = true;
            }
            returnStaged();
            boolean feeding = action.getKind() == HusbandryActionKind.FEED_ADULT
                || action.getKind() == HusbandryActionKind.CULL_EXCESS_ADULT && ownsSession;
            if (feeding) minecraft.thePlayer.inventory.currentItem = priorHotbarSlot;
            if (ownsSession) {
                if (feeding) minecraft.playerController.updateController();
                guard.quarantine(lease);
                guard.end(lease);
                ownsSession = false;
            }
        }

        private void aimAt(double x, double y, double z) {
            double dx = x - minecraft.thePlayer.posX;
            double dy = y - (minecraft.thePlayer.posY + MinecraftRuntimeAccess.eyeHeight(minecraft.thePlayer));
            double dz = z - minecraft.thePlayer.posZ;
            minecraft.thePlayer.rotationYaw = (float) (Math.atan2(dz, dx) * 180.0D / Math.PI) - 90.0F;
            minecraft.thePlayer.rotationPitch = (float) -(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * 180.0D
                / Math.PI);
        }

        private synchronized void cancelOnClientThread() {
            if (isTerminal()) return;
            if (navigationHandle != null) {
                navigationHandle.cancel();
                navigationHandle = null;
            }
            stopSession();
            state = ActionState.CANCELLED;
            detail = "Husbandry action cancelled before checkpoint advancement";
            clearActive(this);
        }

        private void fail(String failure) {
            if (navigationHandle != null) {
                navigationHandle.cancel();
                navigationHandle = null;
            }
            stopSession();
            state = ActionState.FAILED;
            detail = failure == null ? "Husbandry action failed" : failure;
            clearActive(this);
        }

        private ActionProgress snapshot() {
            return new ActionProgress(request.getRequestId(), state, detail);
        }

        private boolean isTerminal() {
            return state == ActionState.CONFIRMED || state == ActionState.CANCELLED || state == ActionState.FAILED;
        }

        private void trace(String event) {
            DevelopmentTrace.event(
                "husbandry-live",
                event,
                "request",
                request.getRequestId(),
                "phase",
                phase,
                "state",
                state,
                "detail",
                detail,
                "action",
                action.getKind());
        }
    }

    private static AnimalObservation animal(HusbandryObservation observation, String identity) {
        if (observation == null) return null;
        for (AnimalObservation animal : observation.getAnimals()) if (animal.getIdentity()
            .equals(identity)) return animal;
        return null;
    }

    private static HusbandryDropObservation drop(HusbandryObservation observation, String identity) {
        for (HusbandryDropObservation drop : observation.getDrops()) if (drop.getIdentity()
            .equals(identity)) return drop;
        return null;
    }

    private boolean withinPen(HusbandryPlan plan, Entity entity) {
        if (entity == null || minecraft.theWorld == null || minecraft.theWorld.provider == null) return false;
        try {
            return plan.getPen()
                .contains(
                    HusbandryPenGeometry.policyPosition(
                        plan.getPen(),
                        minecraft.theWorld.provider.dimensionId,
                        entity.posX,
                        entity.posY,
                        entity.posZ));
        } catch (IllegalArgumentException mismatch) {
            return false;
        }
    }

    private static long add(long left, long right) {
        long result = left + right;
        return ((left ^ result) & (right ^ result)) < 0L ? Long.MAX_VALUE : result;
    }

    private enum Phase {
        APPROACHING,
        WAITING_FOR_SESSION,
        WAITING_FOR_DISPATCH,
        CONFIRMING
    }
}
