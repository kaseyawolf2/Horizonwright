package io.github.kaseyawolf2.horizonwright.forge.client.container;

import static org.junit.Assert.*;

import org.junit.Test;

public class SupportedStorageTargetTest {

    @Test
    public void capturesOnlyInspectedIronChestNames() {
        assertTrue(SupportedStorageTarget.knownModTile("cpw.mods.ironchest.TileEntityIronChest"));
        assertTrue(SupportedStorageTarget.knownModTile("cpw.mods.ironchest.TileEntityDiamondChest"));
        assertTrue(SupportedStorageTarget.knownModTile("cpw.mods.ironchest.TileEntityNetheriteChest"));
        assertFalse(SupportedStorageTarget.knownModTile("cpw.mods.ironchest.TileEntityUnknownChest"));
        assertFalse(SupportedStorageTarget.knownModTile("other.mod.TileEntityIronChest"));
        assertFalse(SupportedStorageTarget.knownModTile(null));
        assertTrue(SupportedStorageTarget.knownModTile("ganymedes01.etfuturum.tileentities.TileEntityBarrel"));
        assertFalse(SupportedStorageTarget.knownModTile("mcp.mobius.betterbarrels.common.blocks.TileEntityBarrel"));
    }

    @Test
    public void refusesNonInventories() {
        assertFalse(SupportedStorageTarget.supports(null));
        assertFalse(SupportedStorageTarget.supports(new Object()));
        assertThrows(IllegalArgumentException.class, () -> SupportedStorageTarget.requireSupported(new Object()));
    }
}
