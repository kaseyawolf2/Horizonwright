package io.github.kaseyawolf2.horizonwright.forge.client.repair;

import java.util.Optional;

/** Explicit result which distinguishes another container from malformed pinned-version evidence. */
public final class TinkersRepairContainerInspection {

    public enum Status {
        RECOGNIZED,
        NOT_TINKERS_REPAIR_CONTAINER,
        INVALID_LAYOUT_OR_EVIDENCE
    }

    private final Status status;
    private final String diagnostic;
    private final TinkersRepairContainerEvidence evidence;

    private TinkersRepairContainerInspection(Status status, String diagnostic,
        TinkersRepairContainerEvidence evidence) {
        this.status = status;
        this.diagnostic = diagnostic;
        this.evidence = evidence;
    }

    static TinkersRepairContainerInspection recognized(TinkersRepairContainerEvidence evidence) {
        return new TinkersRepairContainerInspection(Status.RECOGNIZED, "recognized pinned TConstruct layout", evidence);
    }

    static TinkersRepairContainerInspection rejected(Status status, String diagnostic) {
        return new TinkersRepairContainerInspection(status, diagnostic, null);
    }

    public Status getStatus() {
        return status;
    }

    public String getDiagnostic() {
        return diagnostic;
    }

    public Optional<TinkersRepairContainerEvidence> getEvidence() {
        return Optional.ofNullable(evidence);
    }
}
