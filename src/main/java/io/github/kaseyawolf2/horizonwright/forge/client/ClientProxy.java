package io.github.kaseyawolf2.horizonwright.forge.client;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import io.github.kaseyawolf2.horizonwright.HorizonwrightMod;
import io.github.kaseyawolf2.horizonwright.forge.CommonProxy;

public final class ClientProxy extends CommonProxy {

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);
    }

    @Override
    public void init(FMLInitializationEvent event) {
        ClientBootstrap.getInstance()
            .initialize();
        HorizonwrightMod.LOG.info("Horizonwright client bootstrap initialized");
    }
}
