package io.github.kaseyawolf2.horizonwright.forge.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.item.ItemStack;

import io.github.kaseyawolf2.horizonwright.DevelopmentTrace;

/** Preserves one leased progressive block-damage operation across vanilla's GUI reset. */
public final class ProgressiveBlockDamageShield {

    private final Minecraft minecraft;
    private String owner;
    private SavedState saved;

    public ProgressiveBlockDamageShield(Minecraft minecraft) {
        if (minecraft == null) throw new IllegalArgumentException("minecraft must not be null");
        this.minecraft = minecraft;
    }

    public synchronized void acquire(String requestedOwner) {
        String normalized = requireOwner(requestedOwner);
        if (owner != null && !owner.equals(normalized)) {
            throw new IllegalStateException("progressive block damage is already owned by " + owner);
        }
        owner = normalized;
        DevelopmentTrace.event("block-damage-shield", "acquired", "owner", owner);
    }

    public synchronized void release(String requestedOwner) {
        if (owner == null) return;
        String normalized = requireOwner(requestedOwner);
        if (!owner.equals(normalized)) {
            throw new IllegalStateException("only the active block-damage owner may release the shield");
        }
        restoreIfNeeded();
        DevelopmentTrace.event("block-damage-shield", "released", "owner", owner);
        owner = null;
    }

    /** Runs at client-tick START, before vanilla turns an open screen into a cancel-dig. */
    public synchronized void beforeVanillaInput() {
        if (owner == null || minecraft.currentScreen == null || minecraft.playerController == null || saved != null) {
            return;
        }
        PlayerControllerMP controller = minecraft.playerController;
        if (!controller.isHittingBlock) return;
        saved = new SavedState(controller);
        controller.isHittingBlock = false;
        DevelopmentTrace.event(
            "block-damage-shield",
            "disarmed-gui-reset",
            "owner",
            owner,
            "screen",
            minecraft.currentScreen.getClass()
                .getSimpleName(),
            "damage",
            saved.damage);
    }

    /** Runs at client-tick END immediately before the owning backend adds its normal damage tick. */
    public synchronized void afterVanillaInput() {
        restoreIfNeeded();
    }

    private void restoreIfNeeded() {
        if (saved == null || minecraft.playerController == null) return;
        if (minecraft.currentScreen == null && minecraft.playerController.isHittingBlock) {
            saved = null;
            DevelopmentTrace.event("block-damage-shield", "vanilla-resumed-after-gui-close", "owner", owner);
            return;
        }
        SavedState state = saved;
        saved = null;
        state.restore(minecraft.playerController);
        DevelopmentTrace
            .event("block-damage-shield", "restored-after-gui-reset", "owner", owner, "damage", state.damage);
    }

    private static String requireOwner(String value) {
        if (value == null || value.trim()
            .isEmpty()) throw new IllegalArgumentException("owner must not be blank");
        return value.trim();
    }

    private static final class SavedState {

        private final int x;
        private final int y;
        private final int z;
        private final ItemStack item;
        private final float damage;
        private final float soundTicks;
        private final int hitDelay;

        private SavedState(PlayerControllerMP controller) {
            x = controller.currentBlockX;
            y = controller.currentBlockY;
            z = controller.currentblockZ;
            item = controller.currentItemHittingBlock;
            damage = controller.curBlockDamageMP;
            soundTicks = controller.stepSoundTickCounter;
            hitDelay = controller.blockHitDelay;
        }

        private void restore(PlayerControllerMP controller) {
            controller.currentBlockX = x;
            controller.currentBlockY = y;
            controller.currentblockZ = z;
            controller.currentItemHittingBlock = item;
            controller.curBlockDamageMP = damage;
            controller.stepSoundTickCounter = soundTicks;
            controller.blockHitDelay = hitDelay;
            controller.isHittingBlock = true;
        }
    }
}
