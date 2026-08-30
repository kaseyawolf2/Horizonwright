package io.github.kaseyawolf2.horizonwright.forge.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.client.ClientCommandHandler;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.InputEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import io.github.kaseyawolf2.horizonwright.HorizonwrightMod;
import io.github.kaseyawolf2.horizonwright.HorizonwrightRuntime;
import io.github.kaseyawolf2.horizonwright.core.action.ActionSessionGuard;
import io.github.kaseyawolf2.horizonwright.forge.client.network.ClientPacketFirewallInstaller;

public final class ClientBootstrap {

    private static final ClientBootstrap INSTANCE = new ClientBootstrap();

    private final KeyBinding dashboardKey = new KeyBinding(
        "key.horizonwright.dashboard",
        Keyboard.KEY_H,
        "key.categories.horizonwright");
    private final HorizonwrightRuntime runtime = HorizonwrightRuntime.getInstance();
    private final ClientInputArbiter inputArbiter = new ClientInputArbiter();
    private final ClientPacketFirewallInstaller packetFirewall = new ClientPacketFirewallInstaller(
        runtime.getActionSessionGuard());
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
        ClientCommandHandler.instance.registerCommand(new HorizonwrightClientCommand(runtime));
        runtime.getActionBroker()
            .addRevocationListener(inputArbiter);
        initialized = true;
    }

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        if (Keyboard.getEventKeyState()) {
            preemptForPhysicalInput(Keyboard.getEventKey());
        }
        if (dashboardKey.isPressed()) {
            openDashboard();
        }
    }

    @SubscribeEvent
    public void onMouseInput(InputEvent.MouseInputEvent event) {
        if (Mouse.getEventButtonState() && Mouse.getEventButton() >= 0) {
            preemptForPhysicalInput(Mouse.getEventButton() - 100);
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            runtime.clientTick();
            packetFirewall.ensureInstalled();
        }
    }

    public static void openDashboard() {
        Minecraft.getMinecraft()
            .displayGuiScreen(new GuiHorizonwrightDashboard(HorizonwrightRuntime.getInstance()));
    }

    private void preemptForPhysicalInput(int keyCode) {
        if (runtime.getActionSessionGuard()
            .getMode() != ActionSessionGuard.Mode.ACTIVE || !isPlayerActionBinding(keyCode)) {
            return;
        }
        try {
            runtime.getActionBroker()
                .revokeAll();
            HorizonwrightMod.LOG.info("Physical player input preempted Horizonwright navigation");
        } catch (RuntimeException failure) {
            HorizonwrightMod.LOG
                .error("Physical input revocation listener failed after the action epoch advanced", failure);
        }
    }

    private static boolean isPlayerActionBinding(int keyCode) {
        net.minecraft.client.settings.GameSettings settings = Minecraft.getMinecraft().gameSettings;
        if (matches(
            keyCode,
            settings.keyBindForward,
            settings.keyBindBack,
            settings.keyBindLeft,
            settings.keyBindRight,
            settings.keyBindJump,
            settings.keyBindSneak,
            settings.keyBindSprint,
            settings.keyBindAttack,
            settings.keyBindUseItem,
            settings.keyBindPickBlock,
            settings.keyBindDrop,
            settings.keyBindInventory)) {
            return true;
        }
        for (KeyBinding hotbar : settings.keyBindsHotbar) {
            if (hotbar.getKeyCode() == keyCode) {
                return true;
            }
        }
        return false;
    }

    private static boolean matches(int keyCode, KeyBinding... bindings) {
        for (KeyBinding binding : bindings) {
            if (binding.getKeyCode() == keyCode) {
                return true;
            }
        }
        return false;
    }
}
