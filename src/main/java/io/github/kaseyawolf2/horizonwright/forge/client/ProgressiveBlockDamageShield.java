package io.github.kaseyawolf2.horizonwright.forge.client;

import java.lang.reflect.Field;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.item.ItemStack;

import io.github.kaseyawolf2.horizonwright.DevelopmentTrace;

/** Preserves one leased progressive block-damage operation across vanilla's input processing. */
public final class ProgressiveBlockDamageShield {

    private final Minecraft minecraft;
    private String owner;
    private SavedState saved;
    private SavedState lastKnown;

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
        lastKnown = null;
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
        lastKnown = null;
    }

    /**
     * Runs at client-tick START and temporarily disarms the controller before vanilla can advance,
     * retarget, or cancel the exact automation-owned block.
     */
    public synchronized void beforeVanillaInput() {
        if (owner == null || minecraft.playerController == null || saved != null) {
            return;
        }
        PlayerControllerMP controller = minecraft.playerController;
        saved = ControllerStateAccess.isHittingBlock(controller) ? new SavedState(controller) : lastKnown;
        if (saved == null) {
            DevelopmentTrace.event(
                "block-damage-shield",
                "input-boundary-without-checkpoint",
                "owner",
                owner,
                "screen",
                minecraft.currentScreen.getClass()
                    .getSimpleName());
            return;
        }
        ControllerStateAccess.setHittingBlock(controller, false);
        DevelopmentTrace.event(
            "block-damage-shield",
            "disarmed-input-boundary",
            "owner",
            owner,
            "screen",
            minecraft.currentScreen.getClass()
                .getSimpleName(),
            "damage",
            saved.damage);
    }

    /** Runs at client-tick END immediately before the owning backend adds its one exact damage tick. */
    public synchronized void afterVanillaInput() {
        restoreIfNeeded();
    }

    /** Records the active controller state after Horizonwright advances block damage. */
    public synchronized void checkpoint() {
        if (owner == null || minecraft.playerController == null) return;
        if (ControllerStateAccess.isHittingBlock(minecraft.playerController)) {
            lastKnown = new SavedState(minecraft.playerController);
            DevelopmentTrace.event(
                "block-damage-shield",
                "checkpoint",
                "owner",
                owner,
                "damage",
                lastKnown.damage,
                "screen",
                minecraft.currentScreen == null ? "none"
                    : minecraft.currentScreen.getClass()
                        .getSimpleName());
        }
    }

    private void restoreIfNeeded() {
        if (saved == null || minecraft.playerController == null) return;
        SavedState state = saved;
        saved = null;
        state.restore(minecraft.playerController);
        DevelopmentTrace
            .event("block-damage-shield", "restored-after-input-boundary", "owner", owner, "damage", state.damage);
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
            x = ControllerStateAccess.integer(controller, ControllerStateAccess.CURRENT_BLOCK_X);
            y = ControllerStateAccess.integer(controller, ControllerStateAccess.CURRENT_BLOCK_Y);
            z = ControllerStateAccess.integer(controller, ControllerStateAccess.CURRENT_BLOCK_Z);
            item = (ItemStack) ControllerStateAccess.value(controller, ControllerStateAccess.CURRENT_ITEM);
            damage = ControllerStateAccess.floating(controller, ControllerStateAccess.CURRENT_DAMAGE);
            soundTicks = ControllerStateAccess.floating(controller, ControllerStateAccess.SOUND_TICKS);
            hitDelay = ControllerStateAccess.integer(controller, ControllerStateAccess.HIT_DELAY);
        }

        private void restore(PlayerControllerMP controller) {
            ControllerStateAccess.set(controller, ControllerStateAccess.CURRENT_BLOCK_X, x);
            ControllerStateAccess.set(controller, ControllerStateAccess.CURRENT_BLOCK_Y, y);
            ControllerStateAccess.set(controller, ControllerStateAccess.CURRENT_BLOCK_Z, z);
            ControllerStateAccess.set(controller, ControllerStateAccess.CURRENT_ITEM, item);
            ControllerStateAccess.set(controller, ControllerStateAccess.CURRENT_DAMAGE, damage);
            ControllerStateAccess.set(controller, ControllerStateAccess.SOUND_TICKS, soundTicks);
            ControllerStateAccess.set(controller, ControllerStateAccess.HIT_DELAY, hitDelay);
            ControllerStateAccess.setHittingBlock(controller, true);
        }
    }

    private static final class ControllerStateAccess {

        private static final Field CURRENT_BLOCK_X = field("currentBlockX", "field_78775_c");
        private static final Field CURRENT_BLOCK_Y = field("currentBlockY", "field_78772_d");
        private static final Field CURRENT_BLOCK_Z = field("currentblockZ", "field_78773_e");
        private static final Field CURRENT_ITEM = field("currentItemHittingBlock", "field_85183_f");
        private static final Field CURRENT_DAMAGE = field("curBlockDamageMP", "field_78770_f");
        private static final Field SOUND_TICKS = field("stepSoundTickCounter", "field_78780_h");
        private static final Field HIT_DELAY = field("blockHitDelay", "field_78781_i");
        private static final Field HITTING_BLOCK = field("isHittingBlock", "field_78778_j");

        private ControllerStateAccess() {}

        private static boolean isHittingBlock(PlayerControllerMP controller) {
            return (Boolean) value(controller, HITTING_BLOCK);
        }

        private static void setHittingBlock(PlayerControllerMP controller, boolean value) {
            set(controller, HITTING_BLOCK, value);
        }

        private static int integer(PlayerControllerMP controller, Field field) {
            return (Integer) value(controller, field);
        }

        private static float floating(PlayerControllerMP controller, Field field) {
            return (Float) value(controller, field);
        }

        private static Object value(PlayerControllerMP controller, Field field) {
            try {
                return field.get(controller);
            } catch (IllegalAccessException failure) {
                throw new IllegalStateException("Cannot read progressive block-damage state", failure);
            }
        }

        private static void set(PlayerControllerMP controller, Field field, Object value) {
            try {
                field.set(controller, value);
            } catch (IllegalAccessException failure) {
                throw new IllegalStateException("Cannot restore progressive block-damage state", failure);
            }
        }

        private static Field field(String developmentName, String runtimeName) {
            for (String name : new String[] { developmentName, runtimeName }) {
                try {
                    Field field = PlayerControllerMP.class.getDeclaredField(name);
                    field.setAccessible(true);
                    return field;
                } catch (NoSuchFieldException ignored) {
                    // Try the other known name for this exact Minecraft field.
                }
            }
            throw new IllegalStateException(
                "Unsupported PlayerControllerMP: missing " + developmentName + " / " + runtimeName);
        }
    }
}
