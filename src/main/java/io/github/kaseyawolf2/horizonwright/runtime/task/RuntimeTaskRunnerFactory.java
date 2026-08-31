package io.github.kaseyawolf2.horizonwright.runtime.task;

import io.github.kaseyawolf2.horizonwright.core.task.TaskCheckpoint;
import io.github.kaseyawolf2.horizonwright.core.task.TaskRunner;
import io.github.kaseyawolf2.horizonwright.core.task.TaskRunnerFactory;
import io.github.kaseyawolf2.horizonwright.core.task.TaskSpec;

/** Reconstructs runtime runners without exposing optional implementation libraries. */
public final class RuntimeTaskRunnerFactory implements TaskRunnerFactory {

    private final NavigationRuntimeAccess navigation;
    private final ExcavationRuntimeAccess excavation;
    private final UnloadRuntimeAccess unload;
    private final RepairRuntimeAccess repair;

    public RuntimeTaskRunnerFactory(NavigationRuntimeAccess navigation) {
        this(
            navigation,
            DisabledExcavationRuntimeAccess.INSTANCE,
            DisabledUnloadRuntimeAccess.INSTANCE,
            DisabledRepairRuntimeAccess.INSTANCE);
    }

    public RuntimeTaskRunnerFactory(NavigationRuntimeAccess navigation, ExcavationRuntimeAccess excavation) {
        this(navigation, excavation, DisabledUnloadRuntimeAccess.INSTANCE, DisabledRepairRuntimeAccess.INSTANCE);
    }

    public RuntimeTaskRunnerFactory(NavigationRuntimeAccess navigation, ExcavationRuntimeAccess excavation,
        UnloadRuntimeAccess unload) {
        this(navigation, excavation, unload, DisabledRepairRuntimeAccess.INSTANCE);
    }

    public RuntimeTaskRunnerFactory(NavigationRuntimeAccess navigation, ExcavationRuntimeAccess excavation,
        UnloadRuntimeAccess unload, RepairRuntimeAccess repair) {
        if (navigation == null || excavation == null || unload == null || repair == null) {
            throw new IllegalArgumentException("navigation, excavation, unload, and repair must not be null");
        }
        this.navigation = navigation;
        this.excavation = excavation;
        this.unload = unload;
        this.repair = repair;
    }

    @Override
    public TaskRunner create(TaskSpec spec, TaskCheckpoint checkpoint) {
        if (spec == null || checkpoint == null) {
            throw new IllegalArgumentException("spec and checkpoint must not be null");
        }
        if (GoToTask.TYPE.equals(spec.getType())) {
            return new GoToTaskRunner(spec, checkpoint, navigation);
        }
        if (ExcavationTask.TYPE.equals(spec.getType())) {
            return new ExcavationTaskRunner(spec, checkpoint, excavation);
        }
        if (UnloadTask.TYPE.equals(spec.getType())) {
            return new UnloadTaskRunner(spec, checkpoint, unload);
        }
        if (RepairTask.TYPE.equals(spec.getType())) {
            return new RepairTaskRunner(spec, checkpoint, repair);
        }
        throw new IllegalArgumentException("unsupported runtime task type: " + spec.getType());
    }

    private enum DisabledExcavationRuntimeAccess implements ExcavationRuntimeAccess {

        INSTANCE;

        @Override
        public ExcavationBackend getExcavationBackend() {
            return null;
        }

        @Override
        public boolean isDryRun() {
            return false;
        }
    }

    private enum DisabledUnloadRuntimeAccess implements UnloadRuntimeAccess {

        INSTANCE;

        @Override
        public UnloadBackend getUnloadBackend() {
            return null;
        }

        @Override
        public boolean isDryRun() {
            return false;
        }
    }

    private enum DisabledRepairRuntimeAccess implements RepairRuntimeAccess {

        INSTANCE;

        @Override
        public RepairBackend getRepairBackend() {
            return null;
        }

        @Override
        public boolean isDryRun() {
            return false;
        }
    }
}
