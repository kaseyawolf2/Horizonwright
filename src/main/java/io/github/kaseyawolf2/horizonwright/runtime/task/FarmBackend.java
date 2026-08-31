package io.github.kaseyawolf2.horizonwright.runtime.task;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import io.github.kaseyawolf2.horizonwright.core.action.ActionLease;
import io.github.kaseyawolf2.horizonwright.core.base.BasePosition;
import io.github.kaseyawolf2.horizonwright.core.base.CropObservation;
import io.github.kaseyawolf2.horizonwright.core.base.FarmDecision;
import io.github.kaseyawolf2.horizonwright.core.base.NamedArea;
import io.github.kaseyawolf2.horizonwright.core.base.SeedReserveEvidence;

/** Version-isolated observation and verified-action boundary for finite farm passes. */
public interface FarmBackend {

    Availability availability();

    PassSnapshot scan(ScanRequest request);

    TargetSnapshot observe(TargetRequest request);

    ActionHandle execute(ActionRequest request, ActionLease lease);

    final class Availability {

        private final boolean available;
        private final String diagnostic;

        private Availability(boolean available, String diagnostic) {
            if (diagnostic == null || diagnostic.trim()
                .isEmpty()) {
                throw new IllegalArgumentException("farm availability diagnostic is required");
            }
            this.available = available;
            this.diagnostic = diagnostic.trim();
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

    final class ScanRequest {

        private final String taskId;
        private final String plotId;
        private final long actionEpoch;

        public ScanRequest(String taskId, String plotId, long actionEpoch) {
            this.taskId = required(taskId, "task id");
            this.plotId = required(plotId, "plot id");
            if (actionEpoch < 1L) throw new IllegalArgumentException("action epoch must be positive");
            this.actionEpoch = actionEpoch;
        }

        public String getTaskId() {
            return taskId;
        }

        public String getPlotId() {
            return plotId;
        }

        public long getActionEpoch() {
            return actionEpoch;
        }
    }

    final class PassSnapshot {

        private final String taskId;
        private final long actionEpoch;
        private final NamedArea plot;
        private final List<CropObservation> observations;

        public PassSnapshot(String taskId, long actionEpoch, NamedArea plot, List<CropObservation> observations) {
            this.taskId = required(taskId, "task id");
            if (actionEpoch < 1L || plot == null || observations == null || observations.contains(null)) {
                throw new IllegalArgumentException("epoch, plot, and complete observations are required");
            }
            this.actionEpoch = actionEpoch;
            this.plot = plot;
            this.observations = Collections.unmodifiableList(new ArrayList<>(observations));
        }

        public String getTaskId() {
            return taskId;
        }

        public long getActionEpoch() {
            return actionEpoch;
        }

        public NamedArea getPlot() {
            return plot;
        }

        public List<CropObservation> getObservations() {
            return observations;
        }
    }

    final class TargetRequest {

        private final String taskId;
        private final long passRevision;
        private final long actionEpoch;
        private final int observationIndex;
        private final BasePosition position;
        private final int minimumSeedReserve;

        public TargetRequest(String taskId, long passRevision, long actionEpoch, int observationIndex,
            BasePosition position, int minimumSeedReserve) {
            this.taskId = required(taskId, "task id");
            if (passRevision < 1L || actionEpoch < 1L
                || observationIndex < 0
                || position == null
                || minimumSeedReserve < 0) {
                throw new IllegalArgumentException("valid pass, epoch, index, position, and reserve are required");
            }
            this.passRevision = passRevision;
            this.actionEpoch = actionEpoch;
            this.observationIndex = observationIndex;
            this.position = position;
            this.minimumSeedReserve = minimumSeedReserve;
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

        public int getObservationIndex() {
            return observationIndex;
        }

        public BasePosition getPosition() {
            return position;
        }

        public int getMinimumSeedReserve() {
            return minimumSeedReserve;
        }
    }

    final class TargetSnapshot {

        private final String taskId;
        private final long passRevision;
        private final long actionEpoch;
        private final int observationIndex;
        private final CropObservation observation;
        private final SeedReserveEvidence reserveEvidence;

        public TargetSnapshot(String taskId, long passRevision, long actionEpoch, int observationIndex,
            CropObservation observation, SeedReserveEvidence reserveEvidence) {
            this.taskId = required(taskId, "task id");
            if (passRevision < 1L || actionEpoch < 1L
                || observationIndex < 0
                || observation == null
                || reserveEvidence == null) {
                throw new IllegalArgumentException("complete target observation evidence is required");
            }
            this.passRevision = passRevision;
            this.actionEpoch = actionEpoch;
            this.observationIndex = observationIndex;
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

        public int getObservationIndex() {
            return observationIndex;
        }

        public CropObservation getObservation() {
            return observation;
        }

        public SeedReserveEvidence getReserveEvidence() {
            return reserveEvidence;
        }
    }

    final class ActionRequest {

        private final String requestId;
        private final String taskId;
        private final long passRevision;
        private final long actionEpoch;
        private final int observationIndex;
        private final FarmDecision decision;

        public ActionRequest(String requestId, String taskId, long passRevision, long actionEpoch, int observationIndex,
            FarmDecision decision) {
            this.requestId = required(requestId, "request id");
            this.taskId = required(taskId, "task id");
            if (passRevision < 1L || actionEpoch < 1L || observationIndex < 0 || decision == null) {
                throw new IllegalArgumentException("complete farm action authority is required");
            }
            this.passRevision = passRevision;
            this.actionEpoch = actionEpoch;
            this.observationIndex = observationIndex;
            this.decision = decision;
        }

        public String getRequestId() {
            return requestId;
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

        public int getObservationIndex() {
            return observationIndex;
        }

        public FarmDecision getDecision() {
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
        private final CropObservation confirmedAfter;

        public ActionProgress(String requestId, ActionState state, String detail, CropObservation confirmedAfter) {
            this.requestId = required(requestId, "request id");
            if (state == null || detail == null
                || detail.trim()
                    .isEmpty()) {
                throw new IllegalArgumentException("farm action state and detail are required");
            }
            if ((state == ActionState.CONFIRMED) != (confirmedAfter != null)) {
                throw new IllegalArgumentException("only confirmed farm actions carry post-action evidence");
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

        public Optional<CropObservation> getConfirmedAfter() {
            return Optional.ofNullable(confirmedAfter);
        }
    }

    static String required(String value, String field) {
        if (value == null || value.trim()
            .isEmpty()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }
}
