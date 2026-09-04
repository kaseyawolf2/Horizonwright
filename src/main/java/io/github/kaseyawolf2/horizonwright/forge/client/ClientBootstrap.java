package io.github.kaseyawolf2.horizonwright.forge.client;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.command.ICommand;
import net.minecraft.network.NetworkManager;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.common.MinecraftForge;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.InputEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent;
import io.github.kaseyawolf2.horizonwright.HorizonwrightMod;
import io.github.kaseyawolf2.horizonwright.HorizonwrightRuntime;
import io.github.kaseyawolf2.horizonwright.core.action.ActionSessionGuard;
import io.github.kaseyawolf2.horizonwright.core.persistence.HorizonwrightPersistenceStore;
import io.github.kaseyawolf2.horizonwright.core.persistence.ProfileBindingIndexStore;
import io.github.kaseyawolf2.horizonwright.core.persistence.ProfileBindingKey;
import io.github.kaseyawolf2.horizonwright.core.persistence.WorldProfileIdentity;
import io.github.kaseyawolf2.horizonwright.core.task.BlockedReason;
import io.github.kaseyawolf2.horizonwright.core.task.TaskSnapshot;
import io.github.kaseyawolf2.horizonwright.forge.client.container.LiveContainerTransactionExecutor;
import io.github.kaseyawolf2.horizonwright.forge.client.container.LiveVanillaChestUnloadBackend;
import io.github.kaseyawolf2.horizonwright.forge.client.container.ProfileVanillaChestUnloadConfiguration;
import io.github.kaseyawolf2.horizonwright.forge.client.excavation.ExcavationTargetOverlay;
import io.github.kaseyawolf2.horizonwright.forge.client.excavation.LiveExcavationBackend;
import io.github.kaseyawolf2.horizonwright.forge.client.farm.LiveVanillaFarmBackend;
import io.github.kaseyawolf2.horizonwright.forge.client.farm.ProfileFarmConfiguration;
import io.github.kaseyawolf2.horizonwright.forge.client.network.ClientPacketFirewallInstaller;
import io.github.kaseyawolf2.horizonwright.forge.client.network.ContainerTransactionPacketCoordinator;
import io.github.kaseyawolf2.horizonwright.forge.client.persistence.SingleplayerWorldBindingEvidence;
import io.github.kaseyawolf2.horizonwright.forge.client.persistence.SingleplayerWorldMarkerRegistry;
import io.github.kaseyawolf2.horizonwright.forge.client.persistence.SingleplayerWorldMarkerSnapshot;
import io.github.kaseyawolf2.horizonwright.forge.client.repair.LiveTinkersRepairBackend;
import io.github.kaseyawolf2.horizonwright.forge.client.repair.ProfileTinkersRepairConfiguration;
import io.github.kaseyawolf2.horizonwright.forge.client.repair.TinkersRepairCompatibilityProbe;
import io.github.kaseyawolf2.horizonwright.forge.client.sleep.LiveVanillaSleepBackend;
import io.github.kaseyawolf2.horizonwright.forge.client.sleep.ProfileSleepConfiguration;
import io.github.kaseyawolf2.horizonwright.runtime.persistence.profile.ProfileAssetEditor;
import io.github.kaseyawolf2.horizonwright.runtime.persistence.profile.ProfileAssetEditorProvider;
import io.github.kaseyawolf2.horizonwright.runtime.persistence.session.ClientProfileBindingCoordinator;
import io.github.kaseyawolf2.horizonwright.runtime.persistence.session.ClientProfileBindingObservation;
import io.github.kaseyawolf2.horizonwright.runtime.persistence.session.ClientProfileBindingSnapshot;
import io.github.kaseyawolf2.horizonwright.runtime.persistence.session.ClientProfileBindingState;
import io.github.kaseyawolf2.horizonwright.runtime.persistence.session.ClientRuntimeSessionManager;
import io.github.kaseyawolf2.horizonwright.runtime.persistence.session.CurrentRuntimeProvider;
import io.github.kaseyawolf2.horizonwright.runtime.persistence.session.DisabledDeathSafetyBoundary;
import io.github.kaseyawolf2.horizonwright.runtime.persistence.session.HorizonwrightRuntimeSessionFactory;
import io.github.kaseyawolf2.horizonwright.runtime.persistence.session.RuntimeConnectionToken;
import io.github.kaseyawolf2.horizonwright.runtime.persistence.session.TaskControllerRuntimeSessionPersistence;

public final class ClientBootstrap {

    private static final ClientBootstrap INSTANCE = new ClientBootstrap();

    private final KeyBinding dashboardKey = new KeyBinding(
        "key.horizonwright.dashboard",
        Keyboard.KEY_H,
        "key.categories.horizonwright");
    private final ClientInputArbiter inputArbiter = new ClientInputArbiter();
    private final ClientScheduleEnvironmentTracker scheduleEnvironment = new ClientScheduleEnvironmentTracker();
    private final ProgressiveBlockDamageShield blockDamageShield = new ProgressiveBlockDamageShield(
        Minecraft.getMinecraft());
    private ClientRuntimeSessionManager runtimeSessions;
    private HorizonwrightPersistenceStore persistenceStore;
    private ClientProfileBindingCoordinator profileBindings;
    private NetworkManager connectionManager;
    private RuntimeConnectionToken connectionToken;
    private boolean localConnection;
    private long nextConnectionToken = 1L;
    private long observedMarkerRevision = -1L;
    private WorldProfileIdentity activeIdentity;
    private HorizonwrightRuntime attachedRuntime;
    private ClientPacketFirewallInstaller packetFirewall;
    private ContainerTransactionPacketCoordinator containerTransactions;
    private LiveContainerTransactionExecutor containerTransactionExecutor;
    private LiveExcavationBackend liveExcavationBackend;
    private LiveVanillaFarmBackend liveFarmBackend;
    private LiveVanillaChestUnloadBackend liveUnloadBackend;
    private LiveTinkersRepairBackend liveRepairBackend;
    private LiveVanillaSleepBackend liveSleepBackend;
    private final Set<String> announcedBlockedTasks = new HashSet<>();
    private boolean initialized;

    private ClientBootstrap() {}

    public static ClientBootstrap getInstance() {
        return INSTANCE;
    }

    public synchronized void initialize(Path stateRoot) {
        if (initialized) {
            return;
        }
        if (stateRoot == null) {
            throw new IllegalArgumentException("stateRoot must not be null");
        }
        persistenceStore = new HorizonwrightPersistenceStore(stateRoot);
        profileBindings = new ClientProfileBindingCoordinator(
            new ProfileBindingIndexStore(stateRoot),
            persistenceStore,
            ClientBootstrap::randomStableId,
            ClientBootstrap::randomStableId,
            System::currentTimeMillis);
        runtimeSessions = new ClientRuntimeSessionManager(new HorizonwrightRuntimeSessionFactory(connection -> {
            Minecraft minecraft = Minecraft.getMinecraft();
            boolean connected = minecraft.theWorld != null && minecraft.thePlayer != null;
            long worldTime = connected ? MinecraftRuntimeAccess.worldTime(minecraft.theWorld) : 0L;
            return scheduleEnvironment.observe(connected, worldTime, Collections.<String>emptySet());
        }, (runtime, connection) -> new DisabledDeathSafetyBoundary()),
            identity -> new TaskControllerRuntimeSessionPersistence(persistenceStore, identity),
            System::currentTimeMillis);
        ClientRegistry.registerKeyBinding(dashboardKey);
        FMLCommonHandler.instance()
            .bus()
            .register(this);
        MinecraftForge.EVENT_BUS.register(new ExcavationTargetOverlay());
        SingleplayerWorldMarkerRegistry.getInstance()
            .initialize();
        registerClientCommand(
            new HorizonwrightClientCommand(runtimeSessions, profileBindings, profileEditorProvider()));
        initialized = true;
    }

    /**
     * Bridges the incompatible Forge/RFB command-registration return descriptors present in GTNH.
     * The runtime method returns void while the development mapping declares ICommand.
     */
    private static void registerClientCommand(ICommand command) {
        for (Method method : ClientCommandHandler.class.getMethods()) {
            if (!isCommandRegistrationMethod(method)) continue;
            try {
                method.invoke(ClientCommandHandler.instance, command);
                return;
            } catch (IllegalAccessException failure) {
                throw new IllegalStateException("Forge client command registration is inaccessible", failure);
            } catch (InvocationTargetException failure) {
                Throwable cause = failure.getCause();
                if (cause instanceof RuntimeException) throw (RuntimeException) cause;
                throw new IllegalStateException("Forge client command registration failed", cause);
            }
        }
        throw new IllegalStateException("Compatible Forge client command registration method was not found");
    }

    private static boolean isCommandRegistrationMethod(Method method) {
        String name = method.getName();
        if (!"registerCommand".equals(name) && !"func_71560_a".equals(name) && !"a".equals(name)) return false;
        Class<?>[] parameters = method.getParameterTypes();
        return parameters.length == 1 && ICommand.class.isAssignableFrom(parameters[0]);
    }

    @SubscribeEvent
    public synchronized void onClientConnected(FMLNetworkEvent.ClientConnectedToServerEvent event) {
        if (event == null || event.manager == null) {
            return;
        }
        retireConnection();
        connectionManager = event.manager;
        connectionToken = new RuntimeConnectionToken("client-connection-" + nextConnectionToken++);
        localConnection = event.isLocal;
        observedMarkerRevision = -1L;
        profileBindings.clearWorld();
        HorizonwrightMod.LOG.info(
            "Horizonwright observed {} client connection {}",
            localConnection ? "local" : "remote",
            connectionToken);
    }

    @SubscribeEvent
    public synchronized void onClientDisconnected(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        if (event == null || event.manager != connectionManager) {
            return;
        }
        retireConnection();
        connectionManager = null;
        connectionToken = null;
        localConnection = false;
        observedMarkerRevision = -1L;
        profileBindings.clearWorld();
        ExcavationTargetOverlay.clear();
    }

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        if (Minecraft.getMinecraft().currentScreen == null && Keyboard.getEventKeyState()) {
            preemptForPhysicalInput(Keyboard.getEventKey());
        }
        if (dashboardKey.isPressed()) {
            openDashboard();
        }
    }

    @SubscribeEvent
    public void onMouseInput(InputEvent.MouseInputEvent event) {
        if (Minecraft.getMinecraft().currentScreen == null && Mouse.getEventButtonState()
            && Mouse.getEventButton() >= 0) {
            preemptForPhysicalInput(Mouse.getEventButton() - 100);
        }
    }

    @SubscribeEvent
    public synchronized void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            blockDamageShield.beforeVanillaInput();
            return;
        }
        blockDamageShield.afterVanillaInput();
        if (runtimeSessions == null) {
            return;
        }
        try {
            synchronizeSingleplayerProfile();
            activateReadyProfile();
            tickAttachedRuntime();
        } catch (RuntimeException failure) {
            HorizonwrightMod.LOG.error("Horizonwright client session tick failed safely", failure);
        }
    }

    public static ProgressiveBlockDamageShield blockDamageShield() {
        return INSTANCE.blockDamageShield;
    }

    public static void openDashboard() {
        CurrentRuntimeProvider provider = INSTANCE.runtimeSessions;
        if (provider == null) {
            HorizonwrightMod.LOG.warn("Horizonwright dashboard requested before client runtime initialization");
            return;
        }
        Minecraft.getMinecraft()
            .displayGuiScreen(new GuiHorizonwrightDashboard(provider, INSTANCE.profileEditorProvider()));
    }

    private synchronized ProfileAssetEditorProvider profileEditorProvider() {
        return () -> {
            synchronized (ClientBootstrap.this) {
                if (persistenceStore == null || activeIdentity == null
                    || runtimeSessions == null
                    || !runtimeSessions.getCurrentRuntime()
                        .isPresent()) {
                    return Optional.empty();
                }
                return Optional.of(new ProfileAssetEditor(persistenceStore, activeIdentity, System::currentTimeMillis));
            }
        };
    }

    private void synchronizeSingleplayerProfile() {
        if (connectionToken == null || !localConnection) {
            return;
        }
        SingleplayerWorldMarkerSnapshot marker = SingleplayerWorldMarkerRegistry.getInstance()
            .snapshot();
        if (marker.getRevision() == observedMarkerRevision) {
            return;
        }
        observedMarkerRevision = marker.getRevision();
        Optional<SingleplayerWorldBindingEvidence> evidence = marker.getEvidence();
        if (!evidence.isPresent()) {
            profileBindings.clearWorld();
            retireProfile();
            HorizonwrightMod.LOG.warn("Horizonwright world profile unavailable: {}", marker.getDiagnostic());
            return;
        }
        SingleplayerWorldBindingEvidence world = evidence.get();
        ClientProfileBindingObservation observation = new ClientProfileBindingObservation(
            ProfileBindingKey.singleplayer(world.getLocatorKey(), world.getWorldFingerprint()),
            currentSingleplayerDisplayName(),
            "singleplayer",
            world.getWorldFingerprint());
        ClientProfileBindingSnapshot binding = profileBindings.observe(observation);
        HorizonwrightMod.LOG.info("Horizonwright world profile {}: {}", binding.getState(), binding.getDiagnostic());
        if (binding.getState() != ClientProfileBindingState.READY) {
            retireProfile();
        }
    }

    private void activateReadyProfile() {
        if (connectionToken == null || !localConnection) {
            return;
        }
        Optional<WorldProfileIdentity> selected = profileBindings.getSnapshot()
            .getSelectedIdentity();
        if (!selected.isPresent()) {
            return;
        }
        WorldProfileIdentity identity = selected.get();
        if (activeIdentity == null || !activeIdentity.equals(identity)) {
            retireProfile();
            runtimeSessions.bindProfile(identity);
            activeIdentity = identity;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.theWorld == null || minecraft.thePlayer == null || minecraft.getNetHandler() == null) {
            return;
        }
        runtimeSessions.worldReady(identity, connectionToken);
        Optional<HorizonwrightRuntime> current = runtimeSessions.getCurrentRuntime();
        if (current.isPresent() && current.get() != attachedRuntime) {
            attachedRuntime = current.get();
            attachedRuntime.getActionBroker()
                .addRevocationListener(inputArbiter);
            ClientNavigationBootstrap.initialize(attachedRuntime);
            liveExcavationBackend = new LiveExcavationBackend(
                minecraft,
                attachedRuntime.getActionSessionGuard(),
                attachedRuntime::getNavigationBackend);
            attachedRuntime.getTaskServices()
                .bindExcavationBackend(liveExcavationBackend);
            liveFarmBackend = new LiveVanillaFarmBackend(
                minecraft,
                attachedRuntime.getActionSessionGuard(),
                attachedRuntime::getNavigationBackend,
                new ProfileFarmConfiguration(profileEditorProvider()));
            attachedRuntime.getTaskServices()
                .bindFarmBackend(liveFarmBackend);
            liveSleepBackend = new LiveVanillaSleepBackend(
                minecraft,
                attachedRuntime.getActionSessionGuard(),
                attachedRuntime::getNavigationBackend,
                new ProfileSleepConfiguration(persistenceStore, identity));
            attachedRuntime.getTaskServices()
                .bindSleepBackend(liveSleepBackend);
            containerTransactions = new ContainerTransactionPacketCoordinator();
            containerTransactionExecutor = new LiveContainerTransactionExecutor(
                minecraft,
                attachedRuntime.getActionSessionGuard(),
                containerTransactions);
            liveUnloadBackend = new LiveVanillaChestUnloadBackend(
                minecraft,
                new ProfileVanillaChestUnloadConfiguration(minecraft, persistenceStore, identity),
                containerTransactionExecutor);
            attachedRuntime.getTaskServices()
                .bindUnloadBackend(liveUnloadBackend);
            liveRepairBackend = new LiveTinkersRepairBackend(
                minecraft,
                attachedRuntime.getActionSessionGuard(),
                attachedRuntime::getNavigationBackend,
                new ProfileTinkersRepairConfiguration(minecraft, persistenceStore, identity),
                containerTransactionExecutor,
                TinkersRepairCompatibilityProbe.inspect());
            attachedRuntime.getTaskServices()
                .bindRepairBackend(liveRepairBackend);
            packetFirewall = new ClientPacketFirewallInstaller(
                attachedRuntime.getActionSessionGuard(),
                null,
                containerTransactions);
        }
    }

    private void tickAttachedRuntime() {
        if (attachedRuntime == null || packetFirewall == null || activeIdentity == null || connectionToken == null) {
            return;
        }
        packetFirewall.ensureInstalled();
        if (packetFirewall.isInstalled()) {
            runtimeSessions.clientTick(activeIdentity, connectionToken);
            containerTransactionExecutor.tick();
            announceNewBlockedTasks();
        }
    }

    private void announceNewBlockedTasks() {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (attachedRuntime == null || minecraft.thePlayer == null) return;
        Set<String> currentlyBlocked = new HashSet<>();
        for (TaskSnapshot task : attachedRuntime.controllerSnapshot()
            .getTasks()) {
            BlockedReason reason = task.getBlockedReason()
                .orElse(null);
            if (reason == null) continue;
            String taskId = task.getSpec()
                .getId();
            currentlyBlocked.add(taskId);
            if (announcedBlockedTasks.add(taskId)) {
                String next = reason.getRequiredUserAction()
                    .isEmpty() ? "Open H > Tasks for details." : reason.getRequiredUserAction();
                MinecraftRuntimeAccess.addChatMessage(
                    minecraft.thePlayer,
                    new ChatComponentText(
                        EnumChatFormatting.RED + "Horizonwright blocked "
                            + taskId
                            + ": "
                            + reason.getDetail()
                            + EnumChatFormatting.GRAY
                            + " Next: "
                            + next));
            }
        }
        announcedBlockedTasks.retainAll(currentlyBlocked);
    }

    private void retireConnection() {
        retireProfile();
        if (runtimeSessions != null) {
            runtimeSessions.unbindProfile();
        }
    }

    private void retireProfile() {
        announcedBlockedTasks.clear();
        if (attachedRuntime != null && liveExcavationBackend != null) {
            attachedRuntime.getTaskServices()
                .unbindExcavationBackend(liveExcavationBackend);
        }
        if (attachedRuntime != null && liveFarmBackend != null) {
            attachedRuntime.getTaskServices()
                .unbindFarmBackend(liveFarmBackend);
        }
        if (attachedRuntime != null && liveUnloadBackend != null) {
            attachedRuntime.getTaskServices()
                .unbindUnloadBackend(liveUnloadBackend);
        }
        if (attachedRuntime != null && liveRepairBackend != null) {
            attachedRuntime.getTaskServices()
                .unbindRepairBackend(liveRepairBackend);
        }
        if (attachedRuntime != null && liveSleepBackend != null) {
            attachedRuntime.getTaskServices()
                .unbindSleepBackend(liveSleepBackend);
        }
        if (runtimeSessions != null && activeIdentity != null && connectionToken != null) {
            runtimeSessions.worldUnavailable(activeIdentity, connectionToken);
            runtimeSessions.unbindProfile();
        }
        if (attachedRuntime != null) {
            attachedRuntime.getActionSessionGuard()
                .markTransportClosed();
        }
        activeIdentity = null;
        attachedRuntime = null;
        packetFirewall = null;
        containerTransactions = null;
        containerTransactionExecutor = null;
        liveExcavationBackend = null;
        liveFarmBackend = null;
        liveUnloadBackend = null;
        liveRepairBackend = null;
        liveSleepBackend = null;
    }

    private void preemptForPhysicalInput(int keyCode) {
        if (runtimeSessions == null || !isPlayerActionBinding(keyCode)) {
            return;
        }
        Optional<HorizonwrightRuntime> current = runtimeSessions.getCurrentRuntime();
        if (!current.isPresent() || current.get()
            .getActionSessionGuard()
            .getMode() != ActionSessionGuard.Mode.ACTIVE) {
            return;
        }
        try {
            current.get()
                .getActionBroker()
                .revokeAll();
            HorizonwrightMod.LOG.info("Physical player input preempted Horizonwright navigation");
        } catch (RuntimeException failure) {
            HorizonwrightMod.LOG
                .error("Physical input revocation listener failed after the action epoch advanced", failure);
        }
    }

    private static String currentSingleplayerDisplayName() {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.getIntegratedServer() == null) {
            return "Singleplayer world";
        }
        String worldName = MinecraftRuntimeAccess.folderName(minecraft.getIntegratedServer());
        return worldName == null || worldName.trim()
            .isEmpty() ? "Singleplayer world" : worldName.trim();
    }

    private static String randomStableId() {
        return UUID.randomUUID()
            .toString();
    }

    private static boolean isPlayerActionBinding(int keyCode) {
        net.minecraft.client.settings.GameSettings settings = Minecraft.getMinecraft().gameSettings;
        KeyBinding[] bindings = { settings.keyBindForward, settings.keyBindBack, settings.keyBindLeft,
            settings.keyBindRight, settings.keyBindJump, settings.keyBindSneak, settings.keyBindSprint,
            settings.keyBindAttack, settings.keyBindUseItem, settings.keyBindPickBlock, settings.keyBindDrop };
        int[] gameplayKeyCodes = new int[bindings.length + settings.keyBindsHotbar.length];
        for (int index = 0; index < bindings.length; index++) {
            gameplayKeyCodes[index] = bindings[index].getKeyCode();
        }
        for (int index = 0; index < settings.keyBindsHotbar.length; index++) {
            gameplayKeyCodes[bindings.length + index] = settings.keyBindsHotbar[index].getKeyCode();
        }
        return PhysicalInputPreemptionPolicy
            .shouldPreempt(keyCode, settings.keyBindInventory.getKeyCode(), gameplayKeyCodes);
    }
}
