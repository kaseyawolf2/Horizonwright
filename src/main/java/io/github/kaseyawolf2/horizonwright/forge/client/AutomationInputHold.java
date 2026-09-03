package io.github.kaseyawolf2.horizonwright.forge.client;

import io.github.kaseyawolf2.horizonwright.DevelopmentTrace;

/**
 * Owns one continuous Minecraft input for the duration of an exact leased interaction.
 *
 * <p>
 * Minecraft 1.7.10 resets progressive block damage whenever the attack binding is not held. A
 * direct {@code PlayerControllerMP.onPlayerDamageBlock} call is therefore not sufficient across
 * ticks: vanilla clears the accumulator before the next automation tick. This hold preserves the
 * binding only while the caller owns the corresponding action session and restores the state it
 * observed on acquisition.
 */
public final class AutomationInputHold {

    public interface Binding {

        boolean isPressed();

        void setPressed(boolean pressed);
    }

    private final String owner;
    private final Binding binding;
    private boolean held;
    private boolean restorePressed;

    public AutomationInputHold(String owner, Binding binding) {
        if (owner == null || owner.trim()
            .isEmpty() || binding == null) {
            throw new IllegalArgumentException("owner and binding are required");
        }
        this.owner = owner.trim();
        this.binding = binding;
    }

    public synchronized void hold() {
        if (!held) {
            restorePressed = binding.isPressed();
            held = true;
            DevelopmentTrace.event("automation-input", "acquired", "owner", owner, "restorePressed", restorePressed);
        }
        binding.setPressed(true);
        DevelopmentTrace.event("automation-input", "asserted", "owner", owner, "pressed", binding.isPressed());
    }

    public synchronized void release() {
        if (!held) return;
        binding.setPressed(restorePressed);
        DevelopmentTrace.event(
            "automation-input",
            "released",
            "owner",
            owner,
            "restoredPressed",
            restorePressed,
            "pressed",
            binding.isPressed());
        held = false;
        restorePressed = false;
    }

    public synchronized boolean isHeld() {
        return held;
    }
}
