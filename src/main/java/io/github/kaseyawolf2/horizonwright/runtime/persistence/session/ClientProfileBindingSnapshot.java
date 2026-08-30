package io.github.kaseyawolf2.horizonwright.runtime.persistence.session;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import io.github.kaseyawolf2.horizonwright.core.persistence.PersistenceLoadStatus;
import io.github.kaseyawolf2.horizonwright.core.persistence.WorldProfileIdentity;

/** Immutable typed result of profile binding observation or confirmation. */
public final class ClientProfileBindingSnapshot {

    private final ClientProfileBindingState state;
    private final String diagnostic;
    private final WorldProfileIdentity selectedIdentity;
    private final List<String> reassociationCandidateProfileIds;
    private final PersistenceLoadStatus loadStatus;
    private final boolean interruptedUpdateRecoverable;

    ClientProfileBindingSnapshot(ClientProfileBindingState state, String diagnostic,
        WorldProfileIdentity selectedIdentity, List<String> reassociationCandidateProfileIds,
        PersistenceLoadStatus loadStatus, boolean interruptedUpdateRecoverable) {
        if (state == null || diagnostic == null
            || diagnostic.trim()
                .isEmpty()
            || reassociationCandidateProfileIds == null) {
            throw new IllegalArgumentException("state, diagnostic, and candidates are required");
        }
        if ((state == ClientProfileBindingState.READY) != (selectedIdentity != null)) {
            throw new IllegalArgumentException("a selected identity is available only in READY");
        }
        this.state = state;
        this.diagnostic = diagnostic.trim();
        this.selectedIdentity = selectedIdentity;
        this.reassociationCandidateProfileIds = Collections
            .unmodifiableList(new ArrayList<>(reassociationCandidateProfileIds));
        this.loadStatus = loadStatus;
        this.interruptedUpdateRecoverable = interruptedUpdateRecoverable;
    }

    public ClientProfileBindingState getState() {
        return state;
    }

    public String getDiagnostic() {
        return diagnostic;
    }

    /** The stable identity is intentionally unavailable outside READY. */
    public Optional<WorldProfileIdentity> getSelectedIdentity() {
        return Optional.ofNullable(selectedIdentity);
    }

    public List<String> getReassociationCandidateProfileIds() {
        return reassociationCandidateProfileIds;
    }

    public Optional<PersistenceLoadStatus> getLoadStatus() {
        return Optional.ofNullable(loadStatus);
    }

    public boolean isInterruptedUpdateRecoverable() {
        return interruptedUpdateRecoverable;
    }
}
