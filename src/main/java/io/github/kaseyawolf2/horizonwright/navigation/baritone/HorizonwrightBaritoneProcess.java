package io.github.kaseyawolf2.horizonwright.navigation.baritone;

import baritone.api.IBaritone;
import baritone.api.process.IBaritoneProcess;
import baritone.api.process.PathingCommand;
import baritone.api.process.PathingCommandType;
import io.github.kaseyawolf2.horizonwright.core.navigation.NavigationState;
import io.github.kaseyawolf2.horizonwright.navigation.baritone.BaritoneNavigationBackend.Handle;

final class HorizonwrightBaritoneProcess implements IBaritoneProcess {

    private final BaritoneNavigationBackend backend;
    private final IBaritone baritone;
    private volatile Handle active;
    private volatile boolean firstTick;

    HorizonwrightBaritoneProcess(BaritoneNavigationBackend backend, IBaritone baritone) {
        this.backend = backend;
        this.baritone = baritone;
    }

    void activate(Handle handle) {
        active = handle;
        firstTick = true;
    }

    void deactivate(Handle handle) {
        if (active == handle) {
            active = null;
            firstTick = false;
        }
    }

    @Override
    public boolean isActive() {
        return active != null;
    }

    @Override
    public PathingCommand onTick(boolean calcFailed, boolean isSafeToCancel) {
        Handle handle = active;
        if (handle == null) {
            return new PathingCommand(null, PathingCommandType.DEFER);
        }
        if (!backend.validateActive(handle)) {
            active = null;
            return new PathingCommand(null, PathingCommandType.CANCEL_AND_SET_GOAL);
        }
        if (handle.goal.isInGoal(
            baritone.getPlayerContext()
                .playerFeet())) {
            backend.finishFromProcess(handle, NavigationState.COMPLETED, "Navigation target reached");
            active = null;
            return new PathingCommand(null, PathingCommandType.CANCEL_AND_SET_GOAL);
        }
        if (firstTick) {
            firstTick = false;
            backend.markMoving(handle, "Baritone path calculation requested");
            return backend.movementOnlyCommand(handle, PathingCommandType.FORCE_REVALIDATE_GOAL_AND_PATH);
        }
        if (calcFailed) {
            backend.finishFromProcess(handle, NavigationState.FAILED, "Baritone path calculation failed");
            active = null;
            return new PathingCommand(null, PathingCommandType.CANCEL_AND_SET_GOAL);
        }
        backend.markMoving(handle, "Following bounded Baritone path");
        return backend.movementOnlyCommand(handle, PathingCommandType.SET_GOAL_AND_PATH);
    }

    @Override
    public boolean isTemporary() {
        return false;
    }

    @Override
    public void onLostControl() {
        Handle handle = active;
        active = null;
        firstTick = false;
        if (handle != null) {
            backend.lostControl(handle);
        }
    }

    @Override
    public double priority() {
        return 10.0D;
    }

    @Override
    public String displayName0() {
        Handle handle = backend.activeHandle();
        return handle == null ? "Horizonwright navigation" : "Horizonwright " + handle.getRequestId();
    }
}
