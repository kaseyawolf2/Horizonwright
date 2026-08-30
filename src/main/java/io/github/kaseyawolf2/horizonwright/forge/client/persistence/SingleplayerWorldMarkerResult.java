package io.github.kaseyawolf2.horizonwright.forge.client.persistence;

import java.util.Optional;

/** Non-throwing result of resolving the durable marker for an integrated-server save. */
public final class SingleplayerWorldMarkerResult {

    public enum Status {
        LOADED,
        CREATED,
        CORRUPT,
        UNAVAILABLE
    }

    private final Status status;
    private final SingleplayerWorldBindingEvidence evidence;
    private final String diagnostic;

    private SingleplayerWorldMarkerResult(Status status, SingleplayerWorldBindingEvidence evidence, String diagnostic) {
        this.status = status;
        this.evidence = evidence;
        this.diagnostic = diagnostic;
    }

    static SingleplayerWorldMarkerResult available(Status status, SingleplayerWorldBindingEvidence evidence,
        String diagnostic) {
        if (status != Status.LOADED && status != Status.CREATED) {
            throw new IllegalArgumentException("available marker status must be LOADED or CREATED");
        }
        return new SingleplayerWorldMarkerResult(status, evidence, diagnostic);
    }

    static SingleplayerWorldMarkerResult unavailable(Status status, String diagnostic) {
        if (status != Status.CORRUPT && status != Status.UNAVAILABLE) {
            throw new IllegalArgumentException("unavailable marker status must be CORRUPT or UNAVAILABLE");
        }
        return new SingleplayerWorldMarkerResult(status, null, diagnostic);
    }

    public Status getStatus() {
        return status;
    }

    public Optional<SingleplayerWorldBindingEvidence> getEvidence() {
        return Optional.ofNullable(evidence);
    }

    public String getDiagnostic() {
        return diagnostic;
    }

    public boolean isAvailable() {
        return evidence != null;
    }
}
