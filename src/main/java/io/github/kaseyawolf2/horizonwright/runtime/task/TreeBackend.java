package io.github.kaseyawolf2.horizonwright.runtime.task;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import io.github.kaseyawolf2.horizonwright.core.action.ActionLease;
import io.github.kaseyawolf2.horizonwright.core.base.NamedArea;
import io.github.kaseyawolf2.horizonwright.core.base.SaplingReserveEvidence;
import io.github.kaseyawolf2.horizonwright.core.base.TreeDecision;
import io.github.kaseyawolf2.horizonwright.core.base.TreeObservation;
import io.github.kaseyawolf2.horizonwright.core.base.TreeWorkCheckpoint;

/** Version-isolated observation and confirmed-action boundary for ordinary tree farms. */
public interface TreeBackend {

    FarmBackend.Availability availability();

    PassSnapshot scan(ScanRequest request);

    TargetSnapshot observe(TargetRequest request);

    ActionHandle execute(ActionRequest request, ActionLease lease);

    final class ScanRequest {

        private final String taskId;
        private final String areaId;
        private final long actionEpoch;

        public ScanRequest(String taskId, String areaId, long actionEpoch) {
            this.taskId = required(taskId, "task id");
            this.areaId = required(areaId, "area id");
            if (actionEpoch < 1L) throw new IllegalArgumentException("action epoch must be positive");
            this.actionEpoch = actionEpoch;
        }

        public String getTaskId() {
            return taskId;
        }

        public String getAreaId() {
            return areaId;
        }

        public long getActionEpoch() {
            return actionEpoch;
        }
    }

    final class PassSnapshot {

        private final String taskId;
        private final long actionEpoch;
        private final NamedArea area;
        private final List<TreeObservation> observations;

        public PassSnapshot(String taskId, long actionEpoch, NamedArea area, List<TreeObservation> observations) {
            this.taskId = required(taskId, "task id");
            if (actionEpoch < 1L || area == null || observations == null || observations.contains(null)) {
                throw new IllegalArgumentException("epoch, area, and complete tree observations are required");
            }
            this.actionEpoch = actionEpoch;
            this.area = area;
            this.observations = Collections.unmodifiableList(new ArrayList<>(observations));
        }

        public String getTaskId() {
            return taskId;
        }

        public long getActionEpoch() {
            return actionEpoch;
        }

        public NamedArea getArea() {
            return area;
        }

        public List<TreeObservation> getObservations() {
            return observations;
        }
    }

    final class TargetRequest {

        private final String taskId;
        private final long passRevision;
        private final long actionEpoch;
        private final int index;
        private final TreeWorkCheckpoint work;
        private final int minimumSaplingReserve;

        public TargetRequest(String taskId, long passRevision, long actionEpoch, int index, TreeWorkCheckpoint work,
            int minimumSaplingReserve) {
            this.taskId = required(taskId, "task id");
            if (passRevision < 1L || actionEpoch < 1L
                || index < 0
                || work == null
                || work.getWorkRevision() != passRevision
                || minimumSaplingReserve < 0) {
                throw new IllegalArgumentException("valid pass, epoch, index, and sapling reserve are required");
            }
            this.passRevision = passRevision;
            this.actionEpoch = actionEpoch;
            this.index = index;
            this.work = work;
            this.minimumSaplingReserve = minimumSaplingReserve;
        }

        public String getTaskId() {
            return taskId;
        }

        public long getPassRevision() {
            return passRevision;
        }

        public long getActionEpoch() {
            return actionEpoch;
        }

        public int getIndex() {
            return index;
        }

        public TreeWorkCheckpoint getWork() {
            return work;
        }

        public int getMinimumSaplingReserve() {
            return minimumSaplingReserve;
        }
    }

    final class TargetSnapshot {

        private final String taskId;
        private final long passRevision;
        private final long actionEpoch;
        private final int index;
        private final TreeObservation observation;
        private final SaplingReserveEvidence reserveEvidence;

        public TargetSnapshot(String taskId, long passRevision, long actionEpoch, int index,
            TreeObservation observation, SaplingReserveEvidence reserveEvidence) {
            this.taskId = required(taskId, "task id");
            if (passRevision < 1L || actionEpoch < 1L || index < 0 || observation == null || reserveEvidence == null) {
                throw new IllegalArgumentException("complete tree target evidence is required");
            }
            this.passRevision = passRevision;
            this.actionEpoch = actionEpoch;
            this.index = index;
            this.observation = observation;
            this.reserveEvidence = reserveEvidence;
        }

        public String getTaskId() {
            return taskId;
        }

        public long getPassRevision() {
            return passRevision;
        }

        public long getActionEpoch() {
            return actionEpoch;
        }

        public int getIndex() {
            return index;
        }

        public TreeObservation getObservation() {
            return observation;
        }

        public SaplingReserveEvidence getReserveEvidence() {
            return reserveEvidence;
        }
    }

    final class ActionRequest {

        private final String requestId;
        private final String taskId;
        private final long actionEpoch;
        private final TreeDecision decision;

        public ActionRequest(String requestId, String taskId, long actionEpoch, TreeDecision decision) {
            this.requestId = required(requestId, "request id");
            this.taskId = required(taskId, "task id");
            if (actionEpoch < 1L || decision == null) {
                throw new IllegalArgumentException("complete tree action authority is required");
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

        public long getActionEpoch() {
            return actionEpoch;
        }

        public TreeDecision getDecision() {
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
        private final TreeObservation confirmedAfter;

        public ActionProgress(String requestId, ActionState state, String detail, TreeObservation confirmedAfter) {
            this.requestId = required(requestId, "request id");
            if (state == null || detail == null
                || detail.trim()
                    .isEmpty()) {
                throw new IllegalArgumentException("tree action state and detail are required");
            }
            if ((state == ActionState.CONFIRMED) != (confirmedAfter != null)) {
                throw new IllegalArgumentException("only confirmed tree actions carry post-action evidence");
            }
            this.state = state;
            this.detail = detail.trim();
            this.confirmedAfter = confirmedAfter;
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

        public Optional<TreeObservation> getConfirmedAfter() {
            return Optional.ofNullable(confirmedAfter);
        }
    }

    static String required(String value, String field) {
        if (value == null || value.trim()
            .isEmpty()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }
}
