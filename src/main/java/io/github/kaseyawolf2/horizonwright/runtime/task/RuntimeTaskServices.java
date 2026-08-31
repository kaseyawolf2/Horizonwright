package io.github.kaseyawolf2.horizonwright.runtime.task;

/**
 * Session-scoped registry for client-thread task adapters.
 *
 * <p>
 * The runtime owns this registry while the Forge world attachment owns the
 * live adapters. Identity-checked unbinding prevents retirement of an old
 * attachment from clearing a newer one.
 * </p>
 */
public final class RuntimeTaskServices implements UnloadRuntimeAccess, RepairRuntimeAccess {

    public interface DryRunSource {

        boolean isDryRun();
    }

    private final DryRunSource dryRun;
    private volatile UnloadBackend unloadBackend;
    private volatile RepairBackend repairBackend;

    public RuntimeTaskServices(DryRunSource dryRun) {
        if (dryRun == null) {
            throw new IllegalArgumentException("dryRun must not be null");
        }
        this.dryRun = dryRun;
    }

    public synchronized void bindUnloadBackend(UnloadBackend backend) {
        if (backend == null) {
            throw new IllegalArgumentException("unload backend must not be null");
        }
        if (unloadBackend != null && unloadBackend != backend) {
            throw new IllegalStateException("another unload backend is already bound to this runtime session");
        }
        unloadBackend = backend;
    }

    public synchronized boolean unbindUnloadBackend(UnloadBackend expected) {
        if (expected == null || unloadBackend != expected) {
            return false;
        }
        unloadBackend = null;
        return true;
    }

    public synchronized void bindRepairBackend(RepairBackend backend) {
        if (backend == null) {
            throw new IllegalArgumentException("repair backend must not be null");
        }
        if (repairBackend != null && repairBackend != backend) {
            throw new IllegalStateException("another repair backend is already bound to this runtime session");
        }
        repairBackend = backend;
    }

    public synchronized boolean unbindRepairBackend(RepairBackend expected) {
        if (expected == null || repairBackend != expected) {
            return false;
        }
        repairBackend = null;
        return true;
    }

    public synchronized void clear() {
        unloadBackend = null;
        repairBackend = null;
    }

    @Override
    public UnloadBackend getUnloadBackend() {
        return unloadBackend;
    }

    @Override
    public RepairBackend getRepairBackend() {
        return repairBackend;
    }

    @Override
    public boolean isDryRun() {
        return dryRun.isDryRun();
    }
}
