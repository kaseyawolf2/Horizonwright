package io.github.kaseyawolf2.horizonwright.runtime.task;

import io.github.kaseyawolf2.horizonwright.core.task.TaskCheckpoint;
import io.github.kaseyawolf2.horizonwright.core.task.TaskRunner;
import io.github.kaseyawolf2.horizonwright.core.task.TaskRunnerFactory;
import io.github.kaseyawolf2.horizonwright.core.task.TaskSpec;

/** Reconstructs runtime runners without exposing optional implementation libraries. */
public final class RuntimeTaskRunnerFactory implements TaskRunnerFactory {

    private final NavigationRuntimeAccess navigation;
    private final ExcavationRuntimeAccess excavation;

    public RuntimeTaskRunnerFactory(NavigationRuntimeAccess navigation) {
        this(navigation, DisabledExcavationRuntimeAccess.INSTANCE);
    }

    public RuntimeTaskRunnerFactory(NavigationRuntimeAccess navigation, ExcavationRuntimeAccess excavation) {
        if (navigation == null || excavation == null) {
            throw new IllegalArgumentException("navigation and excavation must not be null");
        }
        this.navigation = navigation;
        this.excavation = excavation;
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
}
