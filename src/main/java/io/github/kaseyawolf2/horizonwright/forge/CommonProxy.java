package io.github.kaseyawolf2.horizonwright.forge;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import io.github.kaseyawolf2.horizonwright.HorizonwrightMod;
import io.github.kaseyawolf2.horizonwright.Tags;

public class CommonProxy {

    public void preInit(FMLPreInitializationEvent event) {
        HorizonwrightMod.LOG.info("Loading Horizonwright {}", Tags.VERSION);
    }

    public void init(FMLInitializationEvent event) {
        HorizonwrightMod.LOG.info("Horizonwright is client-only; no server runtime was started");
    }
}
