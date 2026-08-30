package io.github.kaseyawolf2.horizonwright.testfixtures;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

import io.github.kaseyawolf2.horizonwright.core.action.ActionCapability;
import io.github.kaseyawolf2.horizonwright.core.action.ActionLease;

public final class FakeActionExecutor {

    private final FakeClock clock;
    private final Deque<PendingAction> pending = new ArrayDeque<>();
    private final List<ActionResult> history = new ArrayList<>();

    public FakeActionExecutor(FakeClock clock) {
        if (clock == null) {
            throw new IllegalArgumentException("clock must not be null");
        }
        this.clock = clock;
    }

    public synchronized void submit(String actionId, ActionCapability capability, ActionLease lease) {
        if (actionId == null || actionId.trim()
            .isEmpty()) {
            throw new IllegalArgumentException("actionId must not be blank");
        }
        if (capability == null || lease == null) {
            throw new IllegalArgumentException("capability and lease are required");
        }
        if (!lease.getCapabilities()
            .contains(capability)) {
            throw new IllegalArgumentException("lease does not own " + capability);
        }
        pending.addLast(new PendingAction(actionId.trim(), capability, lease, clock.wallTicks()));
    }

    public synchronized List<ActionResult> drain() {
        List<ActionResult> results = new ArrayList<>();
        while (!pending.isEmpty()) {
            PendingAction action = pending.removeFirst();
            ActionState state = action.lease.isValid() ? ActionState.EXECUTED : ActionState.CANCELLED_STALE_LEASE;
            ActionResult result = new ActionResult(
                action.actionId,
                action.capability,
                action.lease.getEpoch(),
                action.submittedAtTick,
                clock.wallTicks(),
                state);
            results.add(result);
            history.add(result);
        }
        return Collections.unmodifiableList(results);
    }

    public synchronized int pendingCount() {
        return pending.size();
    }

    public synchronized List<ActionResult> history() {
        return Collections.unmodifiableList(new ArrayList<>(history));
    }

    public enum ActionState {
        EXECUTED,
        CANCELLED_STALE_LEASE
    }

    public static final class ActionResult {

        private final String actionId;
        private final ActionCapability capability;
        private final long actionEpoch;
        private final long submittedAtTick;
        private final long observedAtTick;
        private final ActionState state;

        private ActionResult(String actionId, ActionCapability capability, long actionEpoch, long submittedAtTick,
            long observedAtTick, ActionState state) {
            this.actionId = actionId;
            this.capability = capability;
            this.actionEpoch = actionEpoch;
            this.submittedAtTick = submittedAtTick;
            this.observedAtTick = observedAtTick;
            this.state = state;
        }

        public String getActionId() {
            return actionId;
        }

        public ActionCapability getCapability() {
            return capability;
        }

        public long getActionEpoch() {
            return actionEpoch;
        }

        public long getSubmittedAtTick() {
            return submittedAtTick;
        }

        public long getObservedAtTick() {
            return observedAtTick;
        }

        public ActionState getState() {
            return state;
        }
    }

    private static final class PendingAction {

        private final String actionId;
        private final ActionCapability capability;
        private final ActionLease lease;
        private final long submittedAtTick;

        private PendingAction(String actionId, ActionCapability capability, ActionLease lease, long submittedAtTick) {
            this.actionId = actionId;
            this.capability = capability;
            this.lease = lease;
            this.submittedAtTick = submittedAtTick;
        }
    }
}
