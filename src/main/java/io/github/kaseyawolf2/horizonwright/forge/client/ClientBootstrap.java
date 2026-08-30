package io.github.kaseyawolf2.horizonwright.forge.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.client.ClientCommandHandler;

import org.lwjgl.input.Keyboard;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.InputEvent;
import io.github.kaseyawolf2.horizonwright.HorizonwrightRuntime;

public final class ClientBootstrap {

    private static final ClientBootstrap INSTANCE = new ClientBootstrap();

    private final KeyBinding dashboardKey = new KeyBinding(
        "key.horizonwright.dashboard",
        Keyboard.KEY_H,
        "key.categories.horizonwright");
    private boolean initialized;

    private ClientBootstrap() {}

    public static ClientBootstrap getInstance() {
        return INSTANCE;
    }

    public synchronized void initialize() {
        if (initialized) {
            return;
        }
        ClientRegistry.registerKeyBinding(dashboardKey);
        FMLCommonHandler.instance()
            .bus()
            .register(this);
        ClientCommandHandler.instance
            .registerCommand(new HorizonwrightClientCommand(HorizonwrightRuntime.getInstance()));
        initialized = true;
    }

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        if (dashboardKey.isPressed()) {
            openDashboard();
        }
    }

    public static void openDashboard() {
        Minecraft.getMinecraft()
            .displayGuiScreen(new GuiHorizonwrightDashboard(HorizonwrightRuntime.getInstance()));
    }
}
