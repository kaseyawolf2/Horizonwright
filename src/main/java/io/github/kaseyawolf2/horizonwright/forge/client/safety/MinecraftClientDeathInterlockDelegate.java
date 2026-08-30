package io.github.kaseyawolf2.horizonwright.forge.client.safety;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;

import io.github.kaseyawolf2.horizonwright.HorizonwrightRuntime;
import io.github.kaseyawolf2.horizonwright.core.safety.death.DeathLatchRecord;
import io.github.kaseyawolf2.horizonwright.core.task.TaskControllerState;

/** Concrete emergency-stop bridge; all Minecraft mutations are marshalled onto the client thread. */
public final class MinecraftClientDeathInterlockDelegate implements ClientDeathInterlockDelegate {

    private final HorizonwrightRuntime runtime;
    private volatile TaskControllerState lastEmergencyCheckpoint;

    public MinecraftClientDeathInterlockDelegate(HorizonwrightRuntime runtime) {
        if (runtime == null) {
            throw new IllegalArgumentException("runtime must not be null");
        }
        this.runtime = runtime;
    }

    @Override
    public void onCriticalRestrictionsEntered() {
        runtime.getActionBroker()
            .revokeAll();
        scheduleInputCleanup();
    }

    @Override
    public void onCriticalRestrictionsReleased() {
        // Tasks remain explicitly paused/blocked after critical restrictions; there is no implicit reacquisition.
    }

    @Override
    public void performSynchronousEmergencyStop(DeathLatchRecord record) {
        if (record == null) {
            throw new IllegalArgumentException("record must not be null");
        }
        lastEmergencyCheckpoint = runtime.exportControllerState();
        if (!runtime.getActionBroker()
            .isDeathSafetyLocked()) {
            throw new IllegalStateException("death latch did not synchronously lock action authority");
        }
    }

    @Override
    public void scheduleClientThreadCleanup(DeathLatchRecord record) {
        if (record == null) {
            throw new IllegalArgumentException("record must not be null");
        }
        scheduleInputCleanup();
    }

    @Override
    public void scheduleClientThreadLockdownReaffirmation(long deathEpoch) {
        if (deathEpoch <= 0L) {
            throw new IllegalArgumentException("deathEpoch must be positive");
        }
        scheduleInputCleanup();
    }

    @Override
    public void beforeDeathLockdownReleased(long deathEpoch) {
        if (deathEpoch <= 0L) {
            throw new IllegalArgumentException("deathEpoch must be positive");
        }
        if (!runtime.getActionBroker()
            .snapshot()
            .getActiveOwners()
            .isEmpty()) {
            throw new IllegalStateException("death lockdown cannot release while action leases remain active");
        }
        releaseClientInputs();
    }

    public TaskControllerState getLastEmergencyCheckpoint() {
        return lastEmergencyCheckpoint;
    }

    private static void scheduleInputCleanup() {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.func_152345_ab()) {
            releaseClientInputs();
        } else {
            minecraft.func_152344_a(MinecraftClientDeathInterlockDelegate::releaseClientInputs);
        }
    }

    private static void releaseClientInputs() {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (!minecraft.func_152345_ab()) {
            throw new IllegalStateException("client input cleanup must run on the Minecraft client thread");
        }
        KeyBinding.unPressAllKeys();
        if (minecraft.thePlayer != null) {
            minecraft.thePlayer.clearItemInUse();
        }
        if (minecraft.playerController != null) {
            minecraft.playerController.resetBlockRemoving();
        }
    }
}
