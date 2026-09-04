package io.github.kaseyawolf2.horizonwright.forge.client.excavation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.entity.Entity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraftforge.client.event.RenderWorldLastEvent;

import org.lwjgl.opengl.GL11;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import io.github.kaseyawolf2.horizonwright.core.excavation.BlockPosition;

/** Renders the exact block currently targeted by the live excavation backend. */
public final class ExcavationTargetOverlay {

    private static volatile Target target;

    public static void show(BlockPosition position) {
        if (position == null) return;
        target = new Target(position.getX(), position.getY(), position.getZ());
    }

    public static void clear(BlockPosition position) {
        if (position == null) return;
        Target current = target;
        if (current != null && current.matches(position)) target = null;
    }

    public static void clear() {
        target = null;
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        Minecraft minecraft = Minecraft.getMinecraft();
        Target current = target;
        Entity camera = minecraft.renderViewEntity;
        if (current == null || camera == null || minecraft.theWorld == null) return;

        double partialTicks = event.partialTicks;
        double cameraX = camera.lastTickPosX + (camera.posX - camera.lastTickPosX) * partialTicks;
        double cameraY = camera.lastTickPosY + (camera.posY - camera.lastTickPosY) * partialTicks;
        double cameraZ = camera.lastTickPosZ + (camera.posZ - camera.lastTickPosZ) * partialTicks;
        AxisAlignedBB box = AxisAlignedBB
            .getBoundingBox(current.x, current.y, current.z, current.x + 1, current.y + 1, current.z + 1)
            .expand(0.003D, 0.003D, 0.003D)
            .offset(-cameraX, -cameraY, -cameraZ);

        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);
        GL11.glLineWidth(2.5F);
        RenderGlobal.drawOutlinedBoundingBox(box, 0x40E0FF);
        GL11.glDepthMask(true);
        GL11.glPopAttrib();
    }

    private static final class Target {

        private final int x;
        private final int y;
        private final int z;

        private Target(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        private boolean matches(BlockPosition position) {
            return x == position.getX() && y == position.getY() && z == position.getZ();
        }
    }
}
