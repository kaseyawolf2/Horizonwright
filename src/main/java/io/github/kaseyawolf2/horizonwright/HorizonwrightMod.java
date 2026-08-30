package io.github.kaseyawolf2.horizonwright;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import io.github.kaseyawolf2.horizonwright.forge.CommonProxy;

@Mod(
    modid = HorizonwrightMod.MOD_ID,
    name = HorizonwrightMod.NAME,
    version = Tags.VERSION,
    dependencies = "after:baritone",
    acceptedMinecraftVersions = "[1.7.10]",
    acceptableRemoteVersions = "*")
public final class HorizonwrightMod {

    public static final String MOD_ID = "horizonwright";
    public static final String NAME = "Horizonwright";
    public static final Logger LOG = LogManager.getLogger(MOD_ID);

    @SidedProxy(
        clientSide = "io.github.kaseyawolf2.horizonwright.forge.client.ClientProxy",
        serverSide = "io.github.kaseyawolf2.horizonwright.forge.CommonProxy")
    public static CommonProxy proxy;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        proxy.preInit(event);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init(event);
    }
}
