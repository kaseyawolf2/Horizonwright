package io.github.kaseyawolf2.horizonwright;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import io.github.kaseyawolf2.horizonwright.core.action.ActionBrokerSnapshot;
import io.github.kaseyawolf2.horizonwright.core.action.ActionRevocationListener;
import io.github.kaseyawolf2.horizonwright.core.action.ActionSessionGuard;
import io.github.kaseyawolf2.horizonwright.core.action.InMemoryActionBroker;
import io.github.kaseyawolf2.horizonwright.core.navigation.BackendAvailability;
import io.github.kaseyawolf2.horizonwright.core.navigation.NavigationBackend;
import io.github.kaseyawolf2.horizonwright.core.navigation.NavigationProgress;
import io.github.kaseyawolf2.horizonwright.core.task.ControllerSnapshot;
import io.github.kaseyawolf2.horizonwright.core.task.IHorizonwrightController;
import io.github.kaseyawolf2.horizonwright.core.task.MonotonicClock;
import io.github.kaseyawolf2.horizonwright.core.task.ScheduleEnvironment;
import io.github.kaseyawolf2.horizonwright.core.task.ScheduleRule;
import io.github.kaseyawolf2.horizonwright.core.task.ScheduleSnapshot;
import io.github.kaseyawolf2.horizonwright.core.task.TaskControllerState;
import io.github.kaseyawolf2.horizonwright.core.task.TaskOrchestrator;
import io.github.kaseyawolf2.horizonwright.core.task.TaskSnapshot;
import io.github.kaseyawolf2.horizonwright.core.task.TaskSpec;
import io.github.kaseyawolf2.horizonwright.core.task.TaskState;
import io.github.kaseyawolf2.horizonwright.runtime.task.ExcavationServiceCoordinator;
import io.github.kaseyawolf2.horizonwright.runtime.task.ExcavationTask;
import io.github.kaseyawolf2.horizonwright.runtime.task.FarmTask;
import io.github.kaseyawolf2.horizonwright.runtime.task.GoToTask;
import io.github.kaseyawolf2.horizonwright.runtime.task.NavigationRuntimeAccess;
import io.github.kaseyawolf2.horizonwright.runtime.task.RuntimeTaskRunnerFactory;
import io.github.kaseyawolf2.horizonwright.runtime.task.RuntimeTaskServices;

/** Session-scoped composition root for action authority, tasks, and optional navigation. */
public final class HorizonwrightRuntime implements AutoCloseable {

    private static final HorizonwrightRuntime INSTANCE = new HorizonwrightRuntime();

    private final InMemoryActionBroker actionBroker;
    private final ActionSessionGuard actionSessionGuard;
    private final RuntimeTaskServices taskServices;
    private final TaskOrchestrator controller;
    private final ExcavationServiceCoordinator excavationServiceCoordinator;
    private final long startedAtNanos = System.nanoTime();

    private volatile NavigationBackend navigationBackend;
    private volatile NavigationProgress lastNavigationProgress;
    private volatile String navigationDiagnostic = "No navigation backend configured";
    private volatile ScheduleEnvironment scheduleEnvironment = ScheduleEnvironment.disconnected();
    private volatile boolean dryRun;
    private volatile boolean closed;
    private long nextNavigationTaskId = 1L;

    private HorizonwrightRuntime() {
        this(new InMemoryActionBroker(), new ActionSessionGuard(), new SystemMonotonicClock());
    }

    HorizonwrightRuntime(InMemoryActionBroker actionBroker, ActionSessionGuard actionSessionGuard,
        MonotonicClock clock) {
        if (actionBroker == null || actionSessionGuard == null || clock == null) {
            throw new IllegalArgumentException("actionBroker, actionSessionGuard, and clock are required");
        }
        this.actionBroker = actionBroker;
        this.actionSessionGuard = actionSessionGuard;
        actionBroker.addRevocationListener(actionSessionGuard);
        NavigationRuntimeAccess navigationAccess = new NavigationRuntimeAccess() {

            @Override
            public NavigationBackend getNavigationBackend() {
                return navigationBackend;
            }

            @Override
            public boolean isDryRun() {
                return dryRun || closed;
            }

            @Override
            public void publishNavigationProgress(NavigationProgress progress) {
                if (progress == null) {
                    throw new IllegalArgumentException("progress must not be null");
                }
                lastNavigationProgress = progress;
            }
        };
        taskServices = new RuntimeTaskServices(() -> dryRun || closed);
        controller = new TaskOrchestrator(
            clock,
            new RuntimeTaskRunnerFactory(navigationAccess, taskServices, taskServices, taskServices, taskServices),
            actionBroker);
        excavationServiceCoordinator = new ExcavationServiceCoordinator(controller);
    }

    public static HorizonwrightRuntime getInstance() {
        return INSTANCE;
    }

    /** Creates a fresh runtime for one explicitly managed profile/world session. */
    public static HorizonwrightRuntime createSession() {
        return new HorizonwrightRuntime();
    }

    public InMemoryActionBroker getActionBroker() {
        return actionBroker;
    }

    public ActionSessionGuard getActionSessionGuard() {
        return actionSessionGuard;
    }

    public RuntimeTaskServices getTaskServices() {
        return taskServices;
    }

    public NavigationBackend getNavigationBackend() {
        return navigationBackend;
    }

    public IHorizonwrightController getController() {
        return controller;
    }

    public ControllerSnapshot controllerSnapshot() {
        return controller.snapshot();
    }

    public TaskControllerState exportControllerState() {
        return controller.exportState();
    }

    /** Persistence integration hook; restoration is intentionally limited to a fresh controller. */
    public synchronized ControllerSnapshot restoreControllerState(TaskControllerState state) {
        ensureOpen();
        ControllerSnapshot restored = controller.restoreState(state);
        advanceNavigationTaskSequencePast(restored);
        return restored;
    }

    public RuntimeSnapshot snapshot() {
        ControllerSnapshot taskSnapshot = controller.snapshot();
        return new RuntimeSnapshot(
            taskSnapshot.getActionAuthority(),
            taskSnapshot,
            navigationDiagnostic,
            lastNavigationProgress,
            dryRun,
            elapsedNanos());
    }

    public void stopAutomation(String reason) {
        String detail = reason == null || reason.trim()
            .isEmpty() ? "unspecified emergency" : reason.trim();
        try {
            actionBroker.enterAutomationLockdown();
        } catch (RuntimeException failure) {
            HorizonwrightMod.LOG.error("Automation-stop revocation listener failed after the stop engaged", failure);
        }
        HorizonwrightMod.LOG.warn("Automation stopped: {}", detail);
    }

    /** Re-arms automation after its producer cleanup has drained; direct player control is never latched by this. */
    public boolean resetAutomationStop() {
        ensureOpen();
        if (!actionBroker.isAutomationLocked()) {
            return false;
        }
        ActionSessionGuard.Mode mode = actionSessionGuard.getMode();
        if (mode == ActionSessionGuard.Mode.ACTIVE || mode == ActionSessionGuard.Mode.QUARANTINED) {
            throw new IllegalStateException("automation cleanup is still draining; try reset again next tick");
        }
        actionBroker.leaveAutomationLockdown();
        HorizonwrightMod.LOG.info("Manual automation stop reset; blocked tasks still require explicit resume");
        return true;
    }

    public void setNavigationDiagnostic(String diagnostic) {
        if (diagnostic == null || diagnostic.trim()
            .isEmpty()) {
            throw new IllegalArgumentException("diagnostic must not be blank");
        }
        navigationDiagnostic = diagnostic.trim();
    }

    public synchronized void installNavigationBackend(NavigationBackend backend) {
        ensureOpen();
        if (backend == null) {
            throw new IllegalArgumentException("backend must not be null");
        }
        if (controller.snapshot()
            .getActiveTaskId()
            .isPresent() || actionSessionGuard.isGuarding()) {
            throw new IllegalStateException("cannot replace the navigation backend while an action session is active");
        }
        NavigationBackend previous = navigationBackend;
        if (previous == backend) {
            navigationDiagnostic = readAvailability(backend).getDiagnostic();
            return;
        }
        if (previous instanceof ActionRevocationListener) {
            actionBroker.removeRevocationListener((ActionRevocationListener) previous);
        }
        navigationBackend = backend;
        if (backend instanceof ActionRevocationListener) {
            actionBroker.addRevocationListener((ActionRevocationListener) backend);
        }
        navigationDiagnostic = readAvailability(backend).getDiagnostic();
    }

    public synchronized TaskSpec createGoToTaskSpec(int dimensionId, int x, int y, int z, int tolerance) {
        ensureOpen();
        if (nextNavigationTaskId == Long.MAX_VALUE) {
            throw new IllegalStateException("navigation task id sequence exhausted");
        }
        return GoToTask.create("goto-" + nextNavigationTaskId++, dimensionId, x, y, z, tolerance);
    }

    public TaskSnapshot submitGoTo(int dimensionId, int x, int y, int z, int tolerance) {
        ensureOpen();
        if (actionBroker.isDeathSafetyLocked()) {
            throw new IllegalStateException("death safety is active; new automation is unavailable");
        }
        if (actionBroker.isAutomationLocked()) {
            throw new IllegalStateException("automation is stopped; use /hw reset before submitting new work");
        }
        TaskSpec spec = createGoToTaskSpec(dimensionId, x, y, z, tolerance);
        return controller.submit(spec);
    }

    public TaskSnapshot submitExcavation(TaskSpec spec) {
        ensureOpen();
        if (spec == null || !ExcavationTask.TYPE.equals(spec.getType())) {
            throw new IllegalArgumentException("an excavation task specification is required");
        }
        if (actionBroker.isDeathSafetyLocked()) {
            throw new IllegalStateException("death safety is active; new automation is unavailable");
        }
        if (actionBroker.isAutomationLocked()) {
            throw new IllegalStateException("automation is stopped; use /hw reset before submitting new work");
        }
        return controller.submit(spec);
    }

    public TaskSnapshot submitFarm(TaskSpec spec) {
        ensureOpen();
        if (spec == null || !FarmTask.TYPE.equals(spec.getType())) {
            throw new IllegalArgumentException("a farm-pass task specification is required");
        }
        if (actionBroker.isDeathSafetyLocked()) {
            throw new IllegalStateException("death safety is active; new automation is unavailable");
        }
        if (actionBroker.isAutomationLocked()) {
            throw new IllegalStateException("automation is stopped; use /hw reset before submitting new work");
        }
        return controller.submit(spec);
    }

    public ScheduleSnapshot scheduleFarm(String scheduleId, String plotId, int minimumSeedReserve,
        long intervalMillis) {
        ensureOpen();
        if (intervalMillis < 1L) throw new IllegalArgumentException("farm schedule interval must be positive");
        if (actionBroker.isDeathSafetyLocked()) {
            throw new IllegalStateException("death safety is active; new automation is unavailable");
        }
        if (actionBroker.isAutomationLocked()) {
            throw new IllegalStateException("automation is stopped; use /hw reset before scheduling new work");
        }
        return controller.submitSchedule(
            ScheduleRule.connectedInterval(
                scheduleId,
                FarmTask.scheduledPass(plotId, minimumSeedReserve),
                intervalMillis,
                intervalMillis,
                java.util.Collections.<String>emptySet(),
                0));
    }

    /** Controller-backed compatibility entry point retained for existing integrations. */
    public String startNavigation(int dimensionId, int x, int y, int z, int tolerance) {
        return submitGoTo(dimensionId, x, y, z, tolerance).getSpec()
            .getId();
    }

    public Optional<TaskSnapshot> cancelNavigationTask(String reason) {
        ensureOpen();
        ControllerSnapshot snapshot = controller.snapshot();
        Optional<String> active = snapshot.getActiveTaskId();
        if (active.isPresent()) {
            TaskSnapshot activeTask = snapshot.findTask(active.get())
                .orElse(null);
            if (isLiveGoTo(activeTask)) {
                HorizonwrightMod.LOG.info("Navigation task cancelled: {}", reason);
                return Optional.of(controller.cancel(active.get()));
            }
        }
        for (TaskSnapshot task : snapshot.getTasks()) {
            if (isLiveGoTo(task)) {
                HorizonwrightMod.LOG.info("Navigation task cancelled: {}", reason);
                return Optional.of(
                    controller.cancel(
                        task.getSpec()
                            .getId()));
            }
        }
        return Optional.empty();
    }

    /** Controller-backed compatibility entry point retained for existing integrations. */
    public boolean cancelNavigation(String reason) {
        return cancelNavigationTask(reason).isPresent();
    }

    public Optional<TaskSnapshot> pauseActiveTask() {
        ensureOpen();
        Optional<String> active = controller.snapshot()
            .getActiveTaskId();
        return active.isPresent() ? Optional.of(controller.pause(active.get())) : Optional.empty();
    }

    public TaskSnapshot pauseTask(String taskId) {
        ensureOpen();
        return controller.pause(taskId);
    }

    public TaskSnapshot resumeTask(String taskId) {
        ensureOpen();
        if (actionBroker.isDeathSafetyLocked()) {
            throw new IllegalStateException("death safety is active; work cannot be resumed");
        }
        if (actionBroker.isAutomationLocked()) {
            throw new IllegalStateException("automation is stopped; use /hw reset before resuming work");
        }
        return controller.resume(taskId);
    }

    public TaskSnapshot cancelTask(String taskId) {
        ensureOpen();
        return controller.cancel(taskId);
    }

    public boolean isDryRun() {
        return dryRun;
    }

    public void setDryRun(boolean enabled) {
        ensureOpen();
        boolean changed = dryRun != enabled;
        dryRun = enabled;
        if (changed && enabled) {
            ControllerSnapshot snapshot = controller.snapshot();
            Optional<String> active = snapshot.getActiveTaskId();
            if (active.isPresent()) {
                TaskSnapshot task = snapshot.findTask(active.get())
                    .orElse(null);
                if (task != null && task.getState() == TaskState.RUNNING) {
                    controller.pause(active.get());
                }
            }
        }
    }

    public void setScheduleEnvironment(ScheduleEnvironment environment) {
        ensureOpen();
        if (environment == null) {
            throw new IllegalArgumentException("environment must not be null");
        }
        scheduleEnvironment = environment;
    }

    public ControllerSnapshot clientTick() {
        return clientTick(scheduleEnvironment);
    }

    public ControllerSnapshot clientTick(ScheduleEnvironment environment) {
        ensureOpen();
        if (environment == null) {
            throw new IllegalArgumentException("environment must not be null");
        }
        scheduleEnvironment = environment;
        NavigationBackend backend = navigationBackend;
        if (backend != null) {
            try {
                backend.clientTick();
                navigationDiagnostic = readAvailability(backend).getDiagnostic();
            } catch (RuntimeException failure) {
                navigationDiagnostic = "Navigation cleanup failed: " + describe(failure);
                HorizonwrightMod.LOG.error("Navigation backend client tick failed", failure);
            }
        }
        try {
            excavationServiceCoordinator.coordinate(controller.snapshot());
        } catch (RuntimeException failure) {
            HorizonwrightMod.LOG.error("Excavation service coordination failed safely", failure);
        }
        return controller.tick(environment);
    }

    @Override
    public void close() {
        NavigationBackend backend;
        synchronized (this) {
            if (closed) {
                return;
            }
            closed = true;
            dryRun = true;
            taskServices.clear();
            backend = navigationBackend;
        }

        RuntimeException failure = null;
        try {
            ControllerSnapshot snapshot = controller.snapshot();
            if (snapshot.getActiveTaskId()
                .isPresent()) {
                controller.cancel(
                    snapshot.getActiveTaskId()
                        .get());
                controller.tick(ScheduleEnvironment.disconnected());
            }
        } catch (RuntimeException cancellationFailure) {
            failure = cancellationFailure;
        }
        try {
            // Ordinary runtime retirement is automation-owner cleanup, not evidence of player death.
            // A true death transition is the only authority allowed to engage the stronger packet latch.
            actionBroker.enterAutomationLockdown();
        } catch (RuntimeException revocationFailure) {
            failure = append(failure, revocationFailure);
        }
        if (backend != null) {
            try {
                backend.clientTick();
            } catch (RuntimeException backendFailure) {
                failure = append(failure, backendFailure);
            }
        }
        if (backend instanceof ActionRevocationListener) {
            actionBroker.removeRevocationListener((ActionRevocationListener) backend);
        }
        controller.close();
        actionBroker.removeRevocationListener(actionSessionGuard);
        if (failure != null) {
            throw new IllegalStateException("Horizonwright runtime closed with safety cleanup failures", failure);
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("Horizonwright runtime is closed");
        }
    }

    private long elapsedNanos() {
        long elapsed = System.nanoTime() - startedAtNanos;
        return elapsed < 0L ? Long.MAX_VALUE : elapsed;
    }

    private static boolean isLiveGoTo(TaskSnapshot task) {
        return task != null && GoToTask.TYPE.equals(
            task.getSpec()
                .getType())
            && !task.getState()
                .isTerminal();
    }

    private void advanceNavigationTaskSequencePast(ControllerSnapshot restored) {
        for (TaskSnapshot task : restored.getTasks()) {
            String taskId = task.getSpec()
                .getId();
            if (!taskId.startsWith("goto-") || taskId.length() == "goto-".length()) {
                continue;
            }
            final long restoredSequence;
            try {
                restoredSequence = Long.parseLong(taskId.substring("goto-".length()));
            } catch (NumberFormatException ignored) {
                continue;
            }
            if (restoredSequence < nextNavigationTaskId) {
                continue;
            }
            nextNavigationTaskId = restoredSequence == Long.MAX_VALUE ? Long.MAX_VALUE : restoredSequence + 1L;
        }
    }

    private static BackendAvailability readAvailability(NavigationBackend backend) {
        try {
            BackendAvailability availability = backend.availability();
            return availability == null ? BackendAvailability.unavailable("Navigation backend returned no status")
                : availability;
        } catch (RuntimeException failure) {
            return BackendAvailability.unavailable("Navigation backend status failed: " + describe(failure));
        }
    }

    private static RuntimeException append(RuntimeException first, RuntimeException next) {
        if (first == null) {
            return next;
        }
        first.addSuppressed(next);
        return first;
    }

    private static String describe(RuntimeException failure) {
        String message = failure.getMessage();
        return failure.getClass()
            .getSimpleName() + (message == null || message.isEmpty() ? "" : ": " + message);
    }

    private static final class SystemMonotonicClock implements MonotonicClock {

        private final long originNanos = System.nanoTime();

        @Override
        public long nowMillis() {
            long elapsed = System.nanoTime() - originNanos;
            return elapsed < 0L ? Long.MAX_VALUE : TimeUnit.NANOSECONDS.toMillis(elapsed);
        }
    }

    public static final class RuntimeSnapshot {

        private final ActionBrokerSnapshot actionBroker;
        private final ControllerSnapshot controller;
        private final String navigationDiagnostic;
        private final NavigationProgress navigationProgress;
        private final boolean dryRun;
        private final long uptimeNanos;

        private RuntimeSnapshot(ActionBrokerSnapshot actionBroker, ControllerSnapshot controller,
            String navigationDiagnostic, NavigationProgress navigationProgress, boolean dryRun, long uptimeNanos) {
            this.actionBroker = actionBroker;
            this.controller = controller;
            this.navigationDiagnostic = navigationDiagnostic;
            this.navigationProgress = navigationProgress;
            this.dryRun = dryRun;
            this.uptimeNanos = uptimeNanos;
        }

        public ActionBrokerSnapshot getActionBroker() {
            return actionBroker;
        }

        public ControllerSnapshot getController() {
            return controller;
        }

        public String getNavigationDiagnostic() {
            return navigationDiagnostic;
        }

        public NavigationProgress getNavigationProgress() {
            return navigationProgress;
        }

        public boolean isDryRun() {
            return dryRun;
        }

        public long getUptimeNanos() {
            return uptimeNanos;
        }
    }
}
