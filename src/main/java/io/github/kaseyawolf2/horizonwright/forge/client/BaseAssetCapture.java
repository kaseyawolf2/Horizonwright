package io.github.kaseyawolf2.horizonwright.forge.client;

import net.minecraft.client.Minecraft;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.MovingObjectPosition;

import io.github.kaseyawolf2.horizonwright.core.logistics.StorageItemFilter;
import io.github.kaseyawolf2.horizonwright.core.persistence.NamedLocation;
import io.github.kaseyawolf2.horizonwright.core.persistence.NamedStorageEndpoint;
import io.github.kaseyawolf2.horizonwright.runtime.persistence.profile.ProfileAssetEditor;
import io.github.kaseyawolf2.horizonwright.runtime.persistence.profile.ProfileAssetUpdate;

final class BaseAssetCapture {

    private BaseAssetCapture() {}

    static NamedLocation location(Minecraft mc, String id) {
        if (mc.thePlayer == null || mc.theWorld == null || mc.theWorld.provider == null)
            throw new IllegalStateException("Join the bound world first.");
        MovingObjectPosition hit = mc.objectMouseOver;
        if (hit == null || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK)
            throw new IllegalArgumentException("Look directly at the block, then reopen this screen.");
        return new NamedLocation(id, id, mc.theWorld.provider.dimensionId, hit.blockX, hit.blockY, hit.blockZ);
    }

    static void chest(Minecraft mc, ProfileAssetEditor editor, String id) {
        NamedLocation location = location(mc, id + "-location");
        Object tile = MinecraftRuntimeAccess.tileEntity(mc.theWorld, location.getX(), location.getY(), location.getZ());
        if (tile == null || tile.getClass() != TileEntityChest.class)
            throw new IllegalArgumentException("Look directly at a vanilla chest first.");
        editor.apply(
            ProfileAssetUpdate.of(
                location,
                null,
                new NamedStorageEndpoint(id, id, location.getId(), StorageItemFilter.acceptAll()),
                null));
    }
}
