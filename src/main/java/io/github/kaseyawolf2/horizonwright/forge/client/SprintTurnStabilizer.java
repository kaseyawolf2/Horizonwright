package io.github.kaseyawolf2.horizonwright.forge.client;

/** Retains an already-established sprint across a few transient path-turn ticks without starting sprint itself. */
final class SprintTurnStabilizer {

    private static final int TURN_GRACE_TICKS = 4;
    private int remainingGraceTicks;

    boolean shouldRestore(boolean navigationMoving, boolean sprintAllowed, boolean eligible,
        boolean currentlySprinting) {
        if (!navigationMoving || !sprintAllowed || !eligible) {
            remainingGraceTicks = 0;
            return false;
        }
        if (currentlySprinting) {
            remainingGraceTicks = TURN_GRACE_TICKS;
            return false;
        }
        if (remainingGraceTicks <= 0) return false;
        remainingGraceTicks--;
        return true;
    }
}
