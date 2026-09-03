package io.github.kaseyawolf2.horizonwright.runtime.task;

import io.github.kaseyawolf2.horizonwright.core.action.ActionLease;

/** Pinned-version repair container adapter boundary. */
public interface RepairBackend {

    RepairBackendAvailability availability();

    RepairObservationResult observe(RepairObservationRequest request);

    RepairActionHandle execute(RepairActionRequest request, ActionLease lease);

    default StationAccessHandle accessStation(StationAccessRequest request, ActionLease lease) {
        return null;
    }

    final class StationAccessRequest {

        private final String requestId;
        private final String taskId;
        private final String stationId;
        private final long actionEpoch;

        public StationAccessRequest(String requestId, String taskId, String stationId, long actionEpoch) {
            this.requestId = required(requestId, "request id");
            this.taskId = required(taskId, "task id");
            this.stationId = required(stationId, "station id");
            if (actionEpoch < 1L) throw new IllegalArgumentException("action epoch must be positive");
            this.actionEpoch = actionEpoch;
        }

        public String getRequestId() {
            return requestId;
        }

        public String getTaskId() {
            return taskId;
        }

        public String getStationId() {
            return stationId;
        }

        public long getActionEpoch() {
            return actionEpoch;
        }
    }

    interface StationAccessHandle {

        String getRequestId();

        StationAccessProgress progress();

        void cancel();
    }

    enum StationAccessState {
        SUBMITTED,
        APPROACHING,
        INTERACTING,
        CONFIRMED,
        CANCELLED,
        FAILED
    }

    final class StationAccessProgress {

        private final String requestId;
        private final StationAccessState state;
        private final String detail;

        public StationAccessProgress(String requestId, StationAccessState state, String detail) {
            this.requestId = required(requestId, "request id");
            if (state == null) throw new IllegalArgumentException("station access state is required");
            this.state = state;
            this.detail = required(detail, "station access detail");
        }

        public String getRequestId() {
            return requestId;
        }

        public StationAccessState getState() {
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
