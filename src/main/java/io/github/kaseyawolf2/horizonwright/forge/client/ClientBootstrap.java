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
import io.github.kaseyawolf2.horizonwright.core.task.TaskState;
import io.github.kaseyawolf2.horizonwright.forge.client.container.LiveContainerTransactionExecutor;
import io.github.kaseyawolf2.horizonwright.forge.client.container.LiveVanillaChestUnloadBackend;
import io.github.kaseyawolf2.horizonwright.forge.client.container.ProfileVanillaChestUnloadConfiguration;
import io.github.kaseyawolf2.horizonwright.forge.client.excavation.ExcavationTargetOverlay;
import io.github.kaseyawolf2.horizonwright.forge.client.excavation.LiveExcavationBackend;
import io.github.kaseyawolf2.horizonwright.forge.client.farm.LiveVanillaFarmBackend;
import io.github.kaseyawolf2.horizonwright.forge.client.farm.LiveVanillaTreeBackend;
import io.github.kaseyawolf2.horizonwright.forge.client.farm.ProfileFarmConfiguration;
import io.github.kaseyawolf2.horizonwright.forge.client.husbandry.LiveVanillaHusbandryBackend;
import io.github.kaseyawolf2.horizonwright.forge.client.husbandry.ProfileHusbandryConfiguration;
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
    private long nextSleepEstimate;
    private java.util.Map<String, Integer> sleepEstimates = java.util.Collections.emptyMap();

    private java.util.Map<String, Integer> sleepTravelEstimates() {
        if (attachedRuntime == null || liveSleepBackend == null) return java.util.Collections.emptyMap();
        if (System.nanoTime() < nextSleepEstimate) return sleepEstimates;
        nextSleepEstimate = System.nanoTime() + 1_000_000_000L;
        java.util.Map<String, Integer> estimates = new java.util.LinkedHashMap<>();
        for (io.github.kaseyawolf2.horizonwright.core.task.ScheduleSnapshot schedule : attachedRuntime
            .controllerSnapshot()
            .getScheduler()
            .getSchedules()) {
            io.github.kaseyawolf2.horizonwright.core.task.ScheduledTaskSpec spec = schedule.getRule()
                .getTask();
            if (!io.github.kaseyawolf2.horizonwright.runtime.task.SleepTask.TYPE.equals(spec.getType())) continue;
            try {
                estimates.put(
                    schedule.getRule()
                        .getId(),
                    liveSleepBackend.preparationLeadTicks(
                        io.github.kaseyawolf2.horizonwright.runtime.task.SleepTask.bedLocationId(spec)));
            } catch (RuntimeException unavailable) {
                // The sleep task reports missing/changed bed configuration when its normal window arrives.
            }
        }
        sleepEstimates = estimates;
        return estimates;
    }

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
    private final BackgroundClientControl backgroundControl = new BackgroundClientControl();
    private ClientPacketFirewallInstaller packetFirewall;
    private ContainerTransactionPacketCoordinator containerTransactions;
    private LiveContainerTransactionExecutor containerTransactionExecutor;
    private io.github.kaseyawolf2.horizonwright.forge.client.inventory.LiveExtendedInventoryService extendedInventory;
    private LiveExcavationBackend liveExcavationBackend;
    private LiveVanillaFarmBackend liveFarmBackend;
    private LiveVanillaTreeBackend liveTreeBackend;
    private LiveVanillaHusbandryBackend liveHusbandryBackend;
    private LiveVanillaChestUnloadBackend liveUnloadBackend;
    private LiveTinkersRepairBackend liveRepairBackend;
    private LiveVanillaSleepBackend liveSleepBackend;
    private final Set<String> announcedBlockedTasks = new HashSet<>();
    private final Set<String> announcedRetryAttempts = new HashSet<>();
    private final Set<String> announcedFailedTasks = new HashSet<>();
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
            return scheduleEnvironment.observe(connected, worldTime, Collections.<String>emptySet())
                .withWindowLeadTicks(connected ? sleepTravelEstimates() : Collections.emptyMap());
        }, (runtime, connection) -> new DisabledDeathSafetyBoundary()),
            identity -> new TaskControllerRuntimeSessionPersistence(persistenceStore, identity),
            System::currentTimeMillis);
        ClientRegistry.registerKeyBinding(dashboardKey);
        FMLCommonHandler.instance()
            .bus()
            .register(this);
        MinecraftForge.EVENT_BUS.register(new ExcavationTargetOverlay());
        MinecraftForge.EVENT_BUS.register(
            new io.github.kaseyawolf2.horizonwright.forge.client.excavation.ExcavationStatisticsOverlay(
                runtimeSessions));
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
        if (!org.lwjgl.opengl.Display.isActive()) return;
        if (Minecraft.getMinecraft().currentScreen == null && Keyboard.getEventKeyState()) {
            preemptForPhysicalInput(Keyboard.getEventKey());
        }
        if (dashboardKey.isPressed()) {
            openDashboard();
        }
    }

    @SubscribeEvent
    public void onMouseInput(InputEvent.MouseInputEvent event) {
        if (!org.lwjgl.opengl.Display.isActive()) return;
        if (Minecraft.getMinecraft().currentScreen == null && Mouse.getEventButtonState()
            && Mouse.getEventButton() >= 0) {
            preemptForPhysicalInput(Mouse.getEventButton() - 100);
        }
    }

    @SubscribeEvent
    public synchronized void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            backgroundControl.update(Minecraft.getMinecraft(), attachedRuntime != null);
            blockDamageShield.beforeVanillaInput();
            return;
        }
        blockDamageShield.afterVanillaInput();
        if (hudEditorRequested) {
            hudEditorRequested = false;
            CurrentRuntimeProvider provider = runtimeSessions;
            if (provider != null) Minecraft.getMinecraft()
                .displayGuiScreen(new GuiHudEditor(null, provider));
        }
        if (runtimeSessions == null) {
            return;
        }
        try {
            synchronizeSingleplayerProfile();
            activateReadyProfile();
            backgroundControl.update(Minecraft.getMinecraft(), attachedRuntime != null);
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
            .displayGuiScreen(
                new GuiHorizonwrightDashboard(provider, INSTANCE.profileEditorProvider(), INSTANCE.profileBindings));
    }

    private static volatile boolean hudEditorRequested;

    public static void requestHudEditor() {
        // Chat closes its screen after command dispatch; open on the next client tick.
        hudEditorRequested = true;
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
            if (attachedRuntime.getNavigationBackend() != null) attachedRuntime.getNavigationBackend()
                .configureScaffoldJournal(
                    persistenceStore.pathsForProfile(identity.getProfileId())
                        .getProfileDirectory());
            containerTransactions = new ContainerTransactionPacketCoordinator();
            containerTransactionExecutor = new LiveContainerTransactionExecutor(
                minecraft,
                attachedRuntime.getActionSessionGuard(),
                containerTransactions);
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
                new ProfileFarmConfiguration(profileEditorProvider()),
                containerTransactionExecutor);
            attachedRuntime.getTaskServices()
                .bindFarmBackend(liveFarmBackend);
            liveTreeBackend = new LiveVanillaTreeBackend(
                minecraft,
                attachedRuntime.getActionSessionGuard(),
                attachedRuntime::getNavigationBackend,
                new ProfileFarmConfiguration(profileEditorProvider()),
                containerTransactionExecutor);
            attachedRuntime.getTaskServices()
                .bindTreeBackend(liveTreeBackend);
            liveHusbandryBackend = new LiveVanillaHusbandryBackend(
                minecraft,
                attachedRuntime.getActionSessionGuard(),
                attachedRuntime::getNavigationBackend,
                new ProfileHusbandryConfiguration(profileEditorProvider()));
            attachedRuntime.getTaskServices()
                .bindHusbandryBackend(liveHusbandryBackend);
            liveSleepBackend = new LiveVanillaSleepBackend(
                minecraft,
                attachedRuntime.getActionSessionGuard(),
                attachedRuntime::getNavigationBackend,
                new ProfileSleepConfiguration(persistenceStore, identity));
            attachedRuntime.getTaskServices()
                .bindSleepBackend(liveSleepBackend);
            extendedInventory = new io.github.kaseyawolf2.horizonwright.forge.client.inventory.LiveExtendedInventoryService(
                minecraft,
                attachedRuntime.getActionSessionGuard(),
                containerTransactions,
                () -> {
                    io.github.kaseyawolf2.horizonwright.core.persistence.PersistenceLoadResult<io.github.kaseyawolf2.horizonwright.core.persistence.ProfileEnvelope> loaded = persistenceStore
                        .loadProfile(persistenceStore.pathsForProfile(identity.getProfileId()));
                    if (!loaded.isLoaded() || !identity.equals(
                        loaded.getValue()
                            .getIdentity()))
                        throw new IllegalStateException("Active inventory profile is unavailable or changed");
                    return loaded.getValue();
                });
            attachedRuntime.getTaskServices()
                .bindInventoryService(extendedInventory);
            liveUnloadBackend = new LiveVanillaChestUnloadBackend(
                minecraft,
                new ProfileVanillaChestUnloadConfiguration(minecraft, persistenceStore, identity, () -> {
                    io.github.kaseyawolf2.horizonwright.core.task.ControllerSnapshot unloadSnapshot = attachedRuntime
                        .controllerSnapshot();
                    return unloadSnapshot.getActiveTaskId()
                        .flatMap(unloadSnapshot::findTask)
                        .map(
                            task -> io.github.kaseyawolf2.horizonwright.runtime.task.UnloadTask
                                .isExcavationUnload(task.getSpec()))
                        .orElse(false);
                }),
                containerTransactionExecutor,
                attachedRuntime.getActionSessionGuard(),
                attachedRuntime::getNavigationBackend);
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
            extendedInventory.tick();
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
            String taskId = task.getSpec()
                .getId();
            if (task.getState() == TaskState.QUEUED && task.getRetryCount() > 0) {
                String retryKey = taskId + ":" + task.getRetryCount();
                if (announcedRetryAttempts.add(retryKey)) {
                    MinecraftRuntimeAccess.addChatMessage(
                        minecraft.thePlayer,
                        new ChatComponentText(
                            EnumChatFormatting.YELLOW + "Horizonwright will retry "
                                + taskId
                                + " (attempt "
                                + (task.getRetryCount() + 1)
                                + ") in "
                                + ((Math.max(
                                    0L,
                                    task.getNextEligibleAtMillis() - attachedRuntime.controllerSnapshot()
                                        .getObservedAtMillis())
                                    + 999L) / 1000L)
                                + "s: "
                                + task.getDetail()
                                + " Open H > Tasks for the live countdown and Retry now."));
                }
            }
            if (task.getState() == TaskState.FAILED && announcedFailedTasks.add(taskId)) {
                MinecraftRuntimeAccess.addChatMessage(
                    minecraft.thePlayer,
                    new ChatComponentText(
                        EnumChatFormatting.RED + "Horizonwright failed "
                            + taskId
                            + ": "
                            + task.getDetail()
                            + EnumChatFormatting.GRAY
                            + " Open H > Tasks for details."));
            }
            if (reason == null) continue;
            currentlyBlocked.add(taskId);
            if (announcedBlockedTasks.add(taskId)) {
                String next = reason.getRequiredUserAction()
                    .isEmpty() ? "Open H > Tasks for details." : reason.getRequiredUserAction();
                MinecraftRuntimeAccess.addChatMessage(
                    minecraft.thePlayer,
                    new ChatComponentText(
                        (TaskNoticeFormatting.color(reason) + "Horizonwright paused ") + taskId
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
        announcedRetryAttempts.clear();
        announcedFailedTasks.clear();
        if (extendedInventory != null) {
            if (attachedRuntime != null) attachedRuntime.getTaskServices()
                .unbindInventoryService(extendedInventory);
            extendedInventory.close();
            extendedInventory = null;
        }
        if (attachedRuntime != null && liveExcavationBackend != null) {
            attachedRuntime.getTaskServices()
                .unbindExcavationBackend(liveExcavationBackend);
        }
        if (attachedRuntime != null && liveFarmBackend != null) {
            attachedRuntime.getTaskServices()
                .unbindFarmBackend(liveFarmBackend);
        }
        if (attachedRuntime != null && liveTreeBackend != null) {
            attachedRuntime.getTaskServices()
                .unbindTreeBackend(liveTreeBackend);
        }
        if (attachedRuntime != null && liveHusbandryBackend != null) {
            attachedRuntime.getTaskServices()
                .unbindHusbandryBackend(liveHusbandryBackend);
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
        backgroundControl.update(Minecraft.getMinecraft(), false);
        packetFirewall = null;
        containerTransactions = null;
        containerTransactionExecutor = null;
        liveExcavationBackend = null;
        liveFarmBackend = null;
        liveTreeBackend = null;
        liveHusbandryBackend = null;
        liveUnloadBackend = null;
        liveRepairBackend = null;
        liveSleepBackend = null;
    }

    private void preemptForPhysicalInput(int keyCode) {
        if (runtimeSessions == null || !isPlayerActionBinding(keyCode)) {
            return;
        }
        if (FreecamCompatibility.controlsCamera() && isCameraMovementBinding(keyCode)) return;
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

    private static boolean isCameraMovementBinding(int keyCode) {
        net.minecraft.client.settings.GameSettings settings = Minecraft.getMinecraft().gameSettings;
        KeyBinding[] interactions = { settings.keyBindAttack, settings.keyBindUseItem, settings.keyBindPickBlock,
            settings.keyBindDrop };
        for (KeyBinding binding : interactions) if (binding.getKeyCode() == keyCode) return false;
        for (KeyBinding binding : settings.keyBindsHotbar) if (binding.getKeyCode() == keyCode) return false;
        KeyBinding[] movement = { settings.keyBindForward, settings.keyBindBack, settings.keyBindLeft,
            settings.keyBindRight, settings.keyBindJump, settings.keyBindSneak, settings.keyBindSprint };
        for (KeyBinding binding : movement) if (binding.getKeyCode() == keyCode) return true;
        return false;
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
