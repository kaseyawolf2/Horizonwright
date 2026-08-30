package io.github.kaseyawolf2.horizonwright.forge.client.safety;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;

import io.github.kaseyawolf2.horizonwright.core.safety.death.DeathSafetyDirective;
import io.github.kaseyawolf2.horizonwright.core.safety.death.DeathSafetySnapshot;
import io.github.kaseyawolf2.horizonwright.core.safety.death.ManualHoldReason;
import io.github.kaseyawolf2.horizonwright.core.safety.death.RecoveryNavigationRequest;

/**
 * Deterministic Minecraft-side implementation of the non-durable death directives.
 *
 * <p>
 * The collaborators deliberately describe only the producer which owns each action. Critical-health cleanup stays
 * inside Horizonwright's automation owners, while the evidence-backed death directives are the only path which
 * releases Minecraft's global key, mining, and held-use state. Packet writes are not exposed here: respawn and grave
 * activation remain owned by their exact write gates, and unknown traffic is therefore outside this class entirely.
 * </p>
 *
 * <p>
 * Collaborators must be idempotent. The death kernel can reaffirm lockdown after reconnect, and repeating cleanup is
 * safer than recording an effect as complete before its owner has actually released control.
 * </p>
 */
public final class MinecraftDeathSafetyDirectiveEffect implements DeathSafetyDirectiveEffect {

    private final TaskWorkControl taskWork;
    private final ActionAuthorityControl actionAuthority;
    private final NavigationControl navigation;
    private final ContainerEpochControl containerEpoch;
    private final ManualHoldControl manualHold;
    private final MinecraftInputControl minecraftInput;

    /** Uses the real Minecraft client for the two true-death global input directives. */
    public MinecraftDeathSafetyDirectiveEffect(TaskWorkControl taskWork, ActionAuthorityControl actionAuthority,
        NavigationControl navigation, ContainerEpochControl containerEpoch, ManualHoldControl manualHold) {
        this(taskWork, actionAuthority, navigation, containerEpoch, manualHold, new LiveMinecraftInputControl());
    }

    MinecraftDeathSafetyDirectiveEffect(TaskWorkControl taskWork, ActionAuthorityControl actionAuthority,
        NavigationControl navigation, ContainerEpochControl containerEpoch, ManualHoldControl manualHold,
        MinecraftInputControl minecraftInput) {
        if (taskWork == null || actionAuthority == null
            || navigation == null
            || containerEpoch == null
            || manualHold == null
            || minecraftInput == null) {
            throw new IllegalArgumentException("death directive collaborators must not be null");
        }
        this.taskWork = taskWork;
        this.actionAuthority = actionAuthority;
        this.navigation = navigation;
        this.containerEpoch = containerEpoch;
        this.manualHold = manualHold;
        this.minecraftInput = minecraftInput;
    }

    @Override
    public void apply(DeathSafetyDirective directive, DeathSafetySnapshot snapshot) {
        if (directive == null || snapshot == null) {
            throw new IllegalArgumentException("directive and snapshot must not be null");
        }
        switch (directive) {
            case ENTER_CRITICAL_RESTRICTIONS:
                actionAuthority.enterCriticalRestrictions(snapshot);
                return;
            case RELEASE_CRITICAL_RESTRICTIONS:
                actionAuthority.releaseCriticalRestrictions(snapshot);
                return;
            case FORCE_CHECKPOINT_ACTIVE_TASK:
                taskWork.forceCheckpointActiveTask(snapshot);
                return;
            case CANCEL_ALL_NAVIGATION_AND_PENDING_WORK:
                taskWork.cancelAllNavigationAndPendingWork(snapshot);
                return;
            case REVOKE_ALL_ACTION_LEASES:
                actionAuthority.revokeAllActionLeases(snapshot);
                return;
            case CLEAR_ALL_INPUT_AND_KEYBINDINGS:
                minecraftInput.clearAllInputAndKeybindings();
                return;
            case CLEAR_NAVIGATION_PRIVATE_INPUT:
                navigation.clearPrivateInput(snapshot);
                return;
            case RELEASE_ALL_HELD_USE:
                minecraftInput.releaseAllHeldUse();
                return;
            case INVALIDATE_ACTION_AND_CONTAINER_EPOCHS:
                invalidateEpochs(snapshot);
                return;
            case ENGAGE_DEATH_LOCKDOWN:
                actionAuthority.engageDeathLockdown(snapshot);
                return;
            case START_INTERACTION_DISABLED_RECOVERY_NAVIGATION:
                navigation.startInteractionDisabledRecoveryNavigation(requireRecoveryRequest(snapshot), snapshot);
                return;
            case ENTER_MANUAL_HOLD:
                manualHold.enterManualHold(requireManualHoldReason(snapshot), snapshot);
                return;
            case RELEASE_DEATH_LOCKDOWN:
                actionAuthority.releaseDeathLockdown(snapshot);
                return;
            case SEND_EXACTLY_ONE_RESPAWN:
                throw exactWriteBoundary("respawn", directive);
            case AUTHORIZE_EXACT_GRAVE_ACTIVATION:
                throw exactWriteBoundary("grave activation", directive);
            case PERSIST_UNRESOLVED_DEATH:
            case CLEAR_UNRESOLVED_DEATH:
                throw new IllegalArgumentException(
                    directive + " belongs to DeathSafetyDirectiveProcessor, not its effect delegate");
            default:
                throw new IllegalStateException("unhandled death safety directive: " + directive);
        }
    }

    private void invalidateEpochs(DeathSafetySnapshot snapshot) {
        RuntimeException failure = null;
        try {
            actionAuthority.invalidateActionEpoch(snapshot);
        } catch (RuntimeException actionFailure) {
            failure = actionFailure;
        }
        try {
            containerEpoch.invalidateContainerEpoch(snapshot);
        } catch (RuntimeException containerFailure) {
            if (failure == null) {
                failure = containerFailure;
            } else {
                failure.addSuppressed(containerFailure);
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    private static RecoveryNavigationRequest requireRecoveryRequest(DeathSafetySnapshot snapshot) {
        return snapshot.getRecoveryNavigationRequest()
            .orElseThrow(
                () -> new IllegalStateException(
                    "recovery-navigation directive requires an interaction-disabled navigation request"));
    }

    private static ManualHoldReason requireManualHoldReason(DeathSafetySnapshot snapshot) {
        return snapshot.getManualHoldReason()
            .orElseThrow(() -> new IllegalStateException("manual-hold directive requires a recorded reason"));
    }

    private static IllegalStateException exactWriteBoundary(String action, DeathSafetyDirective directive) {
        return new IllegalStateException(directive + " must be consumed by the exact " + action + " packet-write gate");
    }

    /** Checkpoint ownership and cancellation of already-started or queued automation work. */
    public interface TaskWorkControl {

        void forceCheckpointActiveTask(DeathSafetySnapshot snapshot);

        void cancelAllNavigationAndPendingWork(DeathSafetySnapshot snapshot);
    }

    /** Action-lease and lockdown authority; these operations must not mutate direct player input. */
    public interface ActionAuthorityControl {

        void enterCriticalRestrictions(DeathSafetySnapshot snapshot);

        void releaseCriticalRestrictions(DeathSafetySnapshot snapshot);

        void revokeAllActionLeases(DeathSafetySnapshot snapshot);

        void invalidateActionEpoch(DeathSafetySnapshot snapshot);

        void engageDeathLockdown(DeathSafetySnapshot snapshot);

        void releaseDeathLockdown(DeathSafetySnapshot snapshot);
    }

    /** Navigation-owner cleanup plus the explicitly interaction-disabled recovery route. */
    public interface NavigationControl {

        void clearPrivateInput(DeathSafetySnapshot snapshot);

        void startInteractionDisabledRecoveryNavigation(RecoveryNavigationRequest request,
            DeathSafetySnapshot snapshot);
    }

    /** Invalidates every pending container action without inspecting or blocking network traffic. */
    public interface ContainerEpochControl {

        void invalidateContainerEpoch(DeathSafetySnapshot snapshot);
    }

    /** Presents and maintains an indefinite operator hold after the kernel has persisted its reason. */
    public interface ManualHoldControl {

        void enterManualHold(ManualHoldReason reason, DeathSafetySnapshot snapshot);
    }

    interface MinecraftInputControl {

        void clearAllInputAndKeybindings();

        void releaseAllHeldUse();
    }

    private static final class LiveMinecraftInputControl implements MinecraftInputControl {

        @Override
        public void clearAllInputAndKeybindings() {
            runOnClientThread(new Runnable() {

                @Override
                public void run() {
                    Minecraft minecraft = Minecraft.getMinecraft();
                    KeyBinding.unPressAllKeys();
                    if (minecraft.playerController != null) {
                        minecraft.playerController.resetBlockRemoving();
                    }
                }
            });
        }

        @Override
        public void releaseAllHeldUse() {
            runOnClientThread(new Runnable() {

                @Override
                public void run() {
                    Minecraft minecraft = Minecraft.getMinecraft();
                    if (minecraft.thePlayer != null) {
                        minecraft.thePlayer.clearItemInUse();
                    }
                }
            });
        }

        private static void runOnClientThread(Runnable action) {
            Minecraft minecraft = Minecraft.getMinecraft();
            if (minecraft.func_152345_ab()) {
                action.run();
            } else {
                minecraft.func_152344_a(action);
            }
        }
    }
}
