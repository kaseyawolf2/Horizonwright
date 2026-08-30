package io.github.kaseyawolf2.horizonwright.forge.client.persistence;

import java.util.Optional;

/** Immutable client-thread view of the integrated-server world marker lifecycle. */
public final class SingleplayerWorldMarkerSnapshot {

    public enum Status {
        NO_WORLD,
        LOADED,
        CREATED,
        CORRUPT,
        UNAVAILABLE
    }

    private final long revision;
    private final Status status;
    private final SingleplayerWorldBindingEvidence evidence;
    private final String diagnostic;

    private SingleplayerWorldMarkerSnapshot(long revision, Status status, SingleplayerWorldBindingEvidence evidence,
        String diagnostic) {
        if (revision < 0L || status == null
            || diagnostic == null
            || diagnostic.trim()
                .isEmpty()) {
            throw new IllegalArgumentException("revision, status, and diagnostic are required");
        }
        if ((status == Status.LOADED || status == Status.CREATED) != (evidence != null)) {
            throw new IllegalArgumentException("marker evidence must agree with marker status");
        }
        this.revision = revision;
        this.status = status;
        this.evidence = evidence;
        this.diagnostic = diagnostic.trim();
    }

    static SingleplayerWorldMarkerSnapshot noWorld(long revision, String diagnostic) {
        return new SingleplayerWorldMarkerSnapshot(revision, Status.NO_WORLD, null, diagnostic);
    }

    static SingleplayerWorldMarkerSnapshot fromResult(long revision, SingleplayerWorldMarkerResult result) {
        if (result == null) {
            return unavailable(revision, "singleplayer world marker resolver returned no result");
        }
        Status snapshotStatus;
        switch (result.getStatus()) {
            case LOADED:
                snapshotStatus = Status.LOADED;
                break;
            case CREATED:
                snapshotStatus = Status.CREATED;
                break;
            case CORRUPT:
                snapshotStatus = Status.CORRUPT;
                break;
            case UNAVAILABLE:
                snapshotStatus = Status.UNAVAILABLE;
                break;
            default:
                return unavailable(revision, "singleplayer world marker resolver returned an unknown status");
        }
        SingleplayerWorldBindingEvidence markerEvidence = result.getEvidence()
            .orElse(null);
        return new SingleplayerWorldMarkerSnapshot(revision, snapshotStatus, markerEvidence, result.getDiagnostic());
    }

    static SingleplayerWorldMarkerSnapshot unavailable(long revision, String diagnostic) {
        return new SingleplayerWorldMarkerSnapshot(revision, Status.UNAVAILABLE, null, diagnostic);
    }

    public long getRevision() {
        return revision;
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
