package io.github.kaseyawolf2.horizonwright.forge.client.container;

import java.util.concurrent.TimeUnit;

import net.minecraft.client.Minecraft;

import io.github.kaseyawolf2.horizonwright.DevelopmentTrace;
import io.github.kaseyawolf2.horizonwright.core.action.ActionCapability;
import io.github.kaseyawolf2.horizonwright.core.action.ActionLease;
import io.github.kaseyawolf2.horizonwright.core.action.ActionSessionGuard;
import io.github.kaseyawolf2.horizonwright.forge.client.network.ActionPacketDispatch;
import io.github.kaseyawolf2.horizonwright.runtime.task.UnloadActionHandle;
import io.github.kaseyawolf2.horizonwright.runtime.task.UnloadActionProgress;
import io.github.kaseyawolf2.horizonwright.runtime.task.UnloadActionState;

/** Close only the verified storage, with an empty cursor and a dispatched close packet. */
final class LiveStorageClose implements UnloadActionHandle {

    private final Minecraft mc;
    private final ActionSessionGuard guard;
    private final LiveVanillaChestUnloadBackend.ConfigurationSource config;
    private final String id, storage;
    private final ActionLease lease;
    private final long started = System.nanoTime();
    private boolean sent, dispatched, ownsSession;
    private volatile boolean cancelled;
    private UnloadActionState state = UnloadActionState.EXECUTING;
    private String detail = "Waiting to close verified storage";

    LiveStorageClose(Minecraft mc, ActionSessionGuard guard, LiveVanillaChestUnloadBackend.ConfigurationSource config,
        String id, String storage, long epoch, ActionLease lease) {
        if (lease == null || !lease.isValid()
            || lease.getEpoch() != epoch
            || !lease.getCapabilities()
                .contains(ActionCapability.CONTAINER))
            throw new IllegalStateException("Storage close needs container authority");
        this.mc = mc;
        this.guard = guard;
        this.config = config;
        this.id = id;
        this.storage = storage;
        this.lease = lease;
    }

    public String getRequestId() {
        return id;
    }

    public synchronized UnloadActionProgress progress() {
        if (state != UnloadActionState.EXECUTING) return snapshot();
        try {
            if (cancelled || !lease.isValid() || !mc.func_152345_ab() || mc.thePlayer == null || mc.theWorld == null)
                throw new IllegalStateException("Storage close interrupted");
            if (System.nanoTime() - started > TimeUnit.SECONDS.toNanos(10))
                throw new IllegalStateException("Storage close cleanup timed out");
            if (mc.thePlayer.inventory.getItemStack() != null)
                throw new IllegalStateException("Clear the cursor before closing storage");
            if (!guard.isReadyForSession()) return snapshot();
            if (mc.thePlayer.openContainer == mc.thePlayer.inventoryContainer && (!sent || dispatched)) {
                state = UnloadActionState.CONFIRMED;
                detail = "Storage closed; close packet dispatched and cleanup complete";
            } else if (!sent) {
                if (!config.matches(storage, mc.thePlayer.openContainer))
                    throw new IllegalStateException("Refusing to close a different container");
                guard.begin(lease);
                ownsSession = true;
                sent = true;
                mc.thePlayer.closeScreen();
                detail = "Storage closed locally; waiting for close packet dispatch";
                ActionPacketDispatch.afterPendingWrites(mc, () -> {
                    synchronized (LiveStorageClose.this) {
                        endSession();
                        dispatched = true;
                    }
                });
            } else if (dispatched) {
                throw new IllegalStateException("Another container appeared during storage close");
            }
        } catch (RuntimeException failure) {
            endSession();
            state = UnloadActionState.FAILED;
            detail = failure.getMessage();
        }
        return snapshot();
    }

    private synchronized void endSession() {
        if (ownsSession) {
            guard.quarantine(lease);
            guard.end(lease);
            ownsSession = false;
        }
    }

    public void cancel() {
        cancelled = true;
        if (mc.func_152345_ab()) endSession();
        else mc.func_152344_a(this::endSession);
    }

    private UnloadActionProgress snapshot() {
        DevelopmentTrace.event("storage-close", "progress", "request", id, "state", state, "detail", detail);
        return new UnloadActionProgress(id, state, detail == null ? "Storage close failed" : detail);
    }
}
