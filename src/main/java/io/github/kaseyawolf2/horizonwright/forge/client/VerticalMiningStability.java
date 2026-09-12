package io.github.kaseyawolf2.horizonwright.forge.client;

import net.minecraft.entity.player.EntityPlayer;

/** Grounded and vertically stationary: an airborne jump apex must never steal control from pillaring. */
public final class VerticalMiningStability {

    private static final double EPSILON = 0.00001D;

    private VerticalMiningStability() {}

    public static boolean isStable(EntityPlayer player) {
        return player != null && isStable(player.onGround, player.posY, player.prevPosY, player.motionY);
    }

    public static boolean isStable(boolean onGround, double y, double previousY, double motionY) {
        // Vanilla applies gravity after ground collision, leaving approximately -0.0784 motionY at rest.
        return onGround && Double.isFinite(y)
            && Double.isFinite(previousY)
            && Double.isFinite(motionY)
            && Math.abs(y - previousY) <= EPSILON
            && motionY <= EPSILON
            && motionY >= -0.08001D;
    }
}
