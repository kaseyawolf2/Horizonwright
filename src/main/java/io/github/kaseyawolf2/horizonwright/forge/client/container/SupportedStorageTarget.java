package io.github.kaseyawolf2.horizonwright.forge.client.container;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import net.minecraft.inventory.IInventory;
import net.minecraft.tileentity.TileEntityChest;

/** Block capture policy separate from runtime container-layout and identity verification. */
public final class SupportedStorageTarget {

    private static final Set<String> IRON_TILES = new HashSet<>(
        Arrays.asList(
            "TileEntityIronChest",
            "TileEntityCopperChest",
            "TileEntitySilverChest",
            "TileEntityGoldChest",
            "TileEntityDiamondChest",
            "TileEntityCrystalChest",
            "TileEntityObsidianChest",
            "TileEntityDirtChest",
            "TileEntitySteelChest",
            "TileEntityDarkSteelChest",
            "TileEntityNetheriteChest"));

    private SupportedStorageTarget() {}

    public static boolean supports(Object tile) {
        return tile instanceof IInventory && (tile.getClass() == TileEntityChest.class || knownModTile(
            tile.getClass()
                .getName()));
    }

    static boolean knownModTile(String name) {
        String prefix = "cpw.mods.ironchest.";
        return "ganymedes01.etfuturum.tileentities.TileEntityBarrel".equals(name)
            || name != null && name.startsWith(prefix) && IRON_TILES.contains(name.substring(prefix.length()));
    }

    public static void requireSupported(Object tile) {
        if (!supports(tile)) throw new IllegalArgumentException(
            "Look directly at a vanilla/Iron Chests chest or Et Futurum barrel. Unsupported storage is not inferred.");
    }
}
