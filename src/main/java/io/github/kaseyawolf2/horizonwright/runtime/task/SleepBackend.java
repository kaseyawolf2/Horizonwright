package io.github.kaseyawolf2.horizonwright.runtime.task;

import io.github.kaseyawolf2.horizonwright.core.action.ActionLease;
import io.github.kaseyawolf2.horizonwright.core.base.SleepDecision;
import io.github.kaseyawolf2.horizonwright.core.base.SleepObservation;

/** Version-isolated observation and normal-interaction boundary for sleeping. */
public interface SleepBackend {

    Availability availability();

    ObservationSnapshot observe(ObservationRequest request);

    ActionHandle execute(ActionRequest request, ActionLease lease);

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
        private final String bedLocationId;
        private final long actionEpoch;

        public ObservationRequest(String taskId, String bedLocationId, long actionEpoch) {
            this.taskId = required(taskId, "task id");
            this.bedLocationId = required(bedLocationId, "bed location id");
            if (actionEpoch < 1L) throw new IllegalArgumentException("action epoch must be positive");
            this.actionEpoch = actionEpoch;
        }

        public String getTaskId() {
            return taskId;
        }

        public String getBedLocationId() {
            return bedLocationId;
        }

        public long getActionEpoch() {
            return actionEpoch;
        }
    }

    final class ObservationSnapshot {

        private final String taskId;
        private final String bedLocationId;
        private final long actionEpoch;
        private final SleepObservation observation;

        public ObservationSnapshot(String taskId, String bedLocationId, long actionEpoch,
            SleepObservation observation) {
            this.taskId = required(taskId, "task id");
            this.bedLocationId = required(bedLocationId, "bed location id");
            if (actionEpoch < 1L || observation == null) {
                throw new IllegalArgumentException("positive epoch and sleep observation are required");
            }
            this.actionEpoch = actionEpoch;
            this.observation = observation;
        }

        public String getTaskId() {
            return taskId;
        }

        public String getBedLocationId() {
            return bedLocationId;
        }

        public long getActionEpoch() {
            return actionEpoch;
        }

        public SleepObservation getObservation() {
            return observation;
        }
    }

    final class ActionRequest {

        private final String requestId;
        private final String taskId;
        private final String bedLocationId;
        private final long actionEpoch;
        private final SleepDecision decision;

        public ActionRequest(String requestId, String taskId, String bedLocationId, long actionEpoch,
            SleepDecision decision) {
            this.requestId = required(requestId, "request id");
            this.taskId = required(taskId, "task id");
            this.bedLocationId = required(bedLocationId, "bed location id");
            if (actionEpoch < 1L || decision == null || !decision.requiresInteraction()) {
                throw new IllegalArgumentException("positive epoch and interactive sleep decision are required");
            }
            this.actionEpoch = actionEpoch;
            this.decision = decision;
        }

        public String getRequestId() {
            return requestId;
        }

        public String getTaskId() {
            return taskId;
        }

        public String getBedLocationId() {
            return bedLocationId;
        }

        public long getActionEpoch() {
            return actionEpoch;
        }

        public SleepDecision getDecision() {
            return decision;
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
            if (state == null) throw new IllegalArgumentException("state is required");
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
