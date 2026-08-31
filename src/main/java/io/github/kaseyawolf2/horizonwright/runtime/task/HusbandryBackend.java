package io.github.kaseyawolf2.horizonwright.runtime.task;

import io.github.kaseyawolf2.horizonwright.core.action.ActionLease;
import io.github.kaseyawolf2.horizonwright.core.base.HusbandryObservation;
import io.github.kaseyawolf2.horizonwright.core.base.HusbandryPlan;

/** Version-isolated complete-pen observation and one-action proof boundary. */
public interface HusbandryBackend {

    Availability availability();

    ObservationSnapshot observe(ObservationRequest request);

    ActionReadiness readiness(HusbandryPlan plan);

    ActionHandle execute(ActionRequest request, ActionLease lease);

    final class ActionReadiness {

        private final boolean ready;
        private final String diagnostic;

        private ActionReadiness(boolean ready, String diagnostic) {
            this.ready = ready;
            this.diagnostic = required(diagnostic, "diagnostic");
        }

        public static ActionReadiness ready(String diagnostic) {
            return new ActionReadiness(true, diagnostic);
        }

        public static ActionReadiness unavailable(String diagnostic) {
            return new ActionReadiness(false, diagnostic);
        }

        public boolean isReady() {
            return ready;
        }

        public String getDiagnostic() {
            return diagnostic;
        }
    }

    final class Availability {

        private final boolean available;
        private final String diagnostic;

        private Availability(boolean available, String diagnostic) {
            this.available = available;
            this.diagnostic = required(diagnostic, "diagnostic");
        }

        public static Availability available(String diagnostic) {
            return new Availability(true, diagnostic);
        }

        public static Availability unavailable(String diagnostic) {
            return new Availability(false, diagnostic);
        }

        public boolean isAvailable() {
            return available;
        }

        public String getDiagnostic() {
            return diagnostic;
        }
    }

    final class ObservationRequest {

        private final String taskId;
        private final String penId;
        private final long actionEpoch;
        private final int verifiedActions;

        public ObservationRequest(String taskId, String penId, long actionEpoch, int verifiedActions) {
            this.taskId = required(taskId, "task id");
            this.penId = required(penId, "pen id");
            if (actionEpoch < 1L || verifiedActions < 0)
                throw new IllegalArgumentException("valid epoch and count required");
            this.actionEpoch = actionEpoch;
            this.verifiedActions = verifiedActions;
        }

        public String getTaskId() {
            return taskId;
        }

        public String getPenId() {
            return penId;
        }

        public long getActionEpoch() {
            return actionEpoch;
        }

        public int getVerifiedActions() {
            return verifiedActions;
        }
    }

    final class ObservationSnapshot {

        private final String taskId;
        private final long actionEpoch;
        private final int verifiedActions;
        private final HusbandryObservation observation;

        public ObservationSnapshot(String taskId, long actionEpoch, int verifiedActions,
            HusbandryObservation observation) {
            this.taskId = required(taskId, "task id");
            if (actionEpoch < 1L || verifiedActions < 0 || observation == null) {
                throw new IllegalArgumentException("complete husbandry observation evidence required");
            }
            this.actionEpoch = actionEpoch;
            this.verifiedActions = verifiedActions;
            this.observation = observation;
        }

        public String getTaskId() {
            return taskId;
        }

        public long getActionEpoch() {
            return actionEpoch;
        }

        public int getVerifiedActions() {
            return verifiedActions;
        }

        public HusbandryObservation getObservation() {
            return observation;
        }
    }

    final class ActionRequest {

        private final String requestId;
        private final String taskId;
        private final long actionEpoch;
        private final int verifiedActions;
        private final HusbandryPlan plan;

        public ActionRequest(String requestId, String taskId, long actionEpoch, int verifiedActions,
            HusbandryPlan plan) {
            this.requestId = required(requestId, "request id");
            this.taskId = required(taskId, "task id");
            if (actionEpoch < 1L || verifiedActions < 0 || plan == null || !plan.requiresPostconditionVerification()) {
                throw new IllegalArgumentException("one actionable husbandry plan is required");
            }
            this.actionEpoch = actionEpoch;
            this.verifiedActions = verifiedActions;
            this.plan = plan;
        }

        public String getRequestId() {
            return requestId;
        }

        public String getTaskId() {
            return taskId;
        }

        public long getActionEpoch() {
            return actionEpoch;
        }

        public int getVerifiedActions() {
            return verifiedActions;
        }

        public HusbandryPlan getPlan() {
            return plan;
        }
    }

    interface ActionHandle {

        String getRequestId();

        ActionProgress progress();

        void cancel();
    }

    enum ActionState {
        SUBMITTED,
        EXECUTING,
        CONFIRMED,
        CANCELLED,
        FAILED
    }

    final class ActionProgress {

        private final String requestId;
        private final ActionState state;
        private final String detail;

        public ActionProgress(String requestId, ActionState state, String detail) {
            this.requestId = required(requestId, "request id");
            if (state == null) throw new IllegalArgumentException("state required");
            this.state = state;
            this.detail = required(detail, "detail");
        }

        public String getRequestId() {
            return requestId;
        }

        public ActionState getState() {
            return state;
        }

        public String getDetail() {
            return detail;
        }
    }

    static String required(String value, String field) {
        if (value == null || value.trim()
            .isEmpty()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }
}
