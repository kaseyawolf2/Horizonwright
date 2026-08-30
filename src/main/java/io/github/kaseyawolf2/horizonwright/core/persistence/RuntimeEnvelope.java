package io.github.kaseyawolf2.horizonwright.core.persistence;

import java.util.Objects;

import io.github.kaseyawolf2.horizonwright.core.task.TaskControllerState;

public final class RuntimeEnvelope {

    private final int schemaVersion;
    private final String documentKind;
    private final long writtenAtEpochMillis;
    private final String profileId;
    private final String serverAddress;
    private final String worldFingerprint;
    private final long lastConnectionEpoch;
    private final UnresolvedDeathState unresolvedDeathState;
    private final TaskControllerState taskControllerState;

    public RuntimeEnvelope(long writtenAtEpochMillis, String profileId, String serverAddress, String worldFingerprint,
        UnresolvedDeathState unresolvedDeathState) {
        this(
            writtenAtEpochMillis,
            profileId,
            serverAddress,
            worldFingerprint,
            0L,
            unresolvedDeathState,
            TaskControllerState.empty());
    }

    public RuntimeEnvelope(long writtenAtEpochMillis, String profileId, String serverAddress, String worldFingerprint,
        long lastConnectionEpoch, UnresolvedDeathState unresolvedDeathState, TaskControllerState taskControllerState) {
        this(
            PersistenceSchema.CURRENT_VERSION,
            PersistenceSchema.RUNTIME_DOCUMENT_KIND,
            writtenAtEpochMillis,
            profileId,
            serverAddress,
            worldFingerprint,
            lastConnectionEpoch,
            unresolvedDeathState,
            taskControllerState);
    }

    private RuntimeEnvelope(int schemaVersion, String documentKind, long writtenAtEpochMillis, String profileId,
        String serverAddress, String worldFingerprint, long lastConnectionEpoch,
        UnresolvedDeathState unresolvedDeathState, TaskControllerState taskControllerState) {
        this.schemaVersion = schemaVersion;
        this.documentKind = documentKind;
        this.writtenAtEpochMillis = writtenAtEpochMillis;
        this.profileId = profileId;
        this.serverAddress = serverAddress;
        this.worldFingerprint = worldFingerprint;
        this.lastConnectionEpoch = lastConnectionEpoch;
        this.unresolvedDeathState = unresolvedDeathState;
        this.taskControllerState = taskControllerState;
        validate();
    }

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public String getDocumentKind() {
        return documentKind;
    }

    public long getWrittenAtEpochMillis() {
        return writtenAtEpochMillis;
    }

    public String getProfileId() {
        return profileId;
    }

    public String getServerAddress() {
        return serverAddress;
    }

    public String getWorldFingerprint() {
        return worldFingerprint;
    }

    public long getLastConnectionEpoch() {
        return lastConnectionEpoch;
    }

    public UnresolvedDeathState getUnresolvedDeathState() {
        return unresolvedDeathState;
    }

    public TaskControllerState getTaskControllerState() {
        return taskControllerState;
    }

    /** Returns the first connection epoch that cannot alias any persisted connection. */
    public long minimumNextConnectionEpoch() {
        long floor = lastConnectionEpoch;
        if (unresolvedDeathState != null) {
            floor = Math.max(floor, unresolvedDeathState.getLastObservedConnectionEpoch());
        }
        if (floor == Long.MAX_VALUE) {
            throw new IllegalStateException("persisted connection epoch is exhausted");
        }
        return floor + 1L;
    }

    void validate() {
        if (schemaVersion != PersistenceSchema.CURRENT_VERSION) {
            throw new IllegalArgumentException("runtime schemaVersion must be " + PersistenceSchema.CURRENT_VERSION);
        }
        if (!PersistenceSchema.RUNTIME_DOCUMENT_KIND.equals(documentKind)) {
            throw new IllegalArgumentException(
                "documentKind must be '" + PersistenceSchema.RUNTIME_DOCUMENT_KIND + "'");
        }
        PersistenceValidation.requireNonNegative(writtenAtEpochMillis, "runtime writtenAtEpochMillis");
        PersistenceValidation.requireStableId(profileId, "runtime profileId");
        PersistenceValidation.requireText(serverAddress, "runtime serverAddress");
        PersistenceValidation.requireText(worldFingerprint, "runtime worldFingerprint");
        if (lastConnectionEpoch < 0L || lastConnectionEpoch == Long.MAX_VALUE) {
            throw new IllegalArgumentException("runtime lastConnectionEpoch must be non-negative and advanceable");
        }
        if (taskControllerState == null) {
            throw new IllegalArgumentException("runtime taskControllerState must not be null");
        }
        if (unresolvedDeathState != null) {
            unresolvedDeathState.validate();
            if (!serverAddress.equals(unresolvedDeathState.getServerIdentity())
                || !worldFingerprint.equals(unresolvedDeathState.getWorldIdentity())) {
                throw new IllegalArgumentException(
                    "unresolved death state must belong to the runtime server and world fingerprint");
            }
            if (unresolvedDeathState.getRecordedAtEpochMillis() > writtenAtEpochMillis) {
                throw new IllegalArgumentException("unresolved death state occurs after runtime writtenAtEpochMillis");
            }
        }
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof RuntimeEnvelope)) {
            return false;
        }
        RuntimeEnvelope that = (RuntimeEnvelope) other;
        return schemaVersion == that.schemaVersion && writtenAtEpochMillis == that.writtenAtEpochMillis
            && lastConnectionEpoch == that.lastConnectionEpoch
            && Objects.equals(documentKind, that.documentKind)
            && Objects.equals(profileId, that.profileId)
            && Objects.equals(serverAddress, that.serverAddress)
            && Objects.equals(worldFingerprint, that.worldFingerprint)
            && Objects.equals(unresolvedDeathState, that.unresolvedDeathState)
            && TaskControllerStateJsonAdapter.statesEqual(taskControllerState, that.taskControllerState);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
            schemaVersion,
            documentKind,
            writtenAtEpochMillis,
            profileId,
            serverAddress,
            worldFingerprint,
            lastConnectionEpoch,
            unresolvedDeathState,
            TaskControllerStateJsonAdapter.stateHashCode(taskControllerState));
    }
}
