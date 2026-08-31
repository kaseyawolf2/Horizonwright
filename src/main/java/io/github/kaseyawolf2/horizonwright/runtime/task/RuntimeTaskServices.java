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
public final class RuntimeTaskServices implements ExcavationRuntimeAccess, UnloadRuntimeAccess, RepairRuntimeAccess,
    FarmRuntimeAccess, SleepRuntimeAccess {

    public interface DryRunSource {

        boolean isDryRun();
    }

    private final DryRunSource dryRun;
    private volatile ExcavationBackend excavationBackend;
    private volatile UnloadBackend unloadBackend;
    private volatile RepairBackend repairBackend;
    private volatile FarmBackend farmBackend;
    private volatile SleepBackend sleepBackend;

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

    public synchronized void bindExcavationBackend(ExcavationBackend backend) {
        if (backend == null) {
            throw new IllegalArgumentException("excavation backend must not be null");
        }
        if (excavationBackend != null && excavationBackend != backend) {
            throw new IllegalStateException("another excavation backend is already bound to this runtime session");
        }
        excavationBackend = backend;
    }

    public synchronized boolean unbindExcavationBackend(ExcavationBackend expected) {
        if (expected == null || excavationBackend != expected) {
            return false;
        }
        excavationBackend = null;
        return true;
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

    public synchronized void bindFarmBackend(FarmBackend backend) {
        if (backend == null) throw new IllegalArgumentException("farm backend must not be null");
        if (farmBackend != null && farmBackend != backend) {
            throw new IllegalStateException("another farm backend is already bound to this runtime session");
        }
        farmBackend = backend;
    }

    public synchronized boolean unbindFarmBackend(FarmBackend expected) {
        if (expected == null || farmBackend != expected) return false;
        farmBackend = null;
        return true;
    }

    public synchronized void bindSleepBackend(SleepBackend backend) {
        if (backend == null) throw new IllegalArgumentException("sleep backend must not be null");
        if (sleepBackend != null && sleepBackend != backend) {
            throw new IllegalStateException("another sleep backend is already bound to this runtime session");
        }
        sleepBackend = backend;
    }

    public synchronized boolean unbindSleepBackend(SleepBackend expected) {
        if (expected == null || sleepBackend != expected) return false;
        sleepBackend = null;
        return true;
    }

    public synchronized void clear() {
        excavationBackend = null;
        unloadBackend = null;
        repairBackend = null;
        farmBackend = null;
        sleepBackend = null;
    }

    @Override
    public ExcavationBackend getExcavationBackend() {
        return excavationBackend;
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
    public FarmBackend getFarmBackend() {
        return farmBackend;
    }

    @Override
    public SleepBackend getSleepBackend() {
        return sleepBackend;
    }

    @Override
    public boolean isDryRun() {
        return dryRun.isDryRun();
    }
}
