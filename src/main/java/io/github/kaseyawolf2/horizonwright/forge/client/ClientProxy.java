package io.github.kaseyawolf2.horizonwright.forge.client;

import java.nio.file.Path;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import io.github.kaseyawolf2.horizonwright.HorizonwrightMod;
import io.github.kaseyawolf2.horizonwright.forge.CommonProxy;

public final class ClientProxy extends CommonProxy {

    private Path stateRoot;

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);
        stateRoot = event.getModConfigurationDirectory()
            .toPath()
            .resolve(HorizonwrightMod.MOD_ID);
    }

    @Override
    public void init(FMLInitializationEvent event) {
        ClientBootstrap.getInstance()
            .initialize(stateRoot);
        HorizonwrightMod.LOG.info("Horizonwright client bootstrap initialized");
    }
}
