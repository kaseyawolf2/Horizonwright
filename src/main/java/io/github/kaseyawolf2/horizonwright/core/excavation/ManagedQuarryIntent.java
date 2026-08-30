package io.github.kaseyawolf2.horizonwright.core.excavation;

import java.util.Objects;

/** Idempotent infrastructure intent emitted once at each deterministic layer boundary. */
public final class ManagedQuarryIntent {

    private final ManagedQuarryIntentKind kind;
    private final BlockPosition position;
    private final String approvedMaterial;

    ManagedQuarryIntent(ManagedQuarryIntentKind kind, BlockPosition position, String approvedMaterial) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.position = Objects.requireNonNull(position, "position");
        if (approvedMaterial == null || approvedMaterial.trim()
            .isEmpty()) {
            throw new IllegalArgumentException("approvedMaterial must not be blank");
        }
        this.approvedMaterial = approvedMaterial.trim();
    }

    public ManagedQuarryIntentKind getKind() {
        return kind;
    }

    public BlockPosition getPosition() {
        return position;
    }

    public String getApprovedMaterial() {
        return approvedMaterial;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ManagedQuarryIntent)) {
            return false;
        }
        ManagedQuarryIntent that = (ManagedQuarryIntent) other;
        return kind == that.kind && position.equals(that.position) && approvedMaterial.equals(that.approvedMaterial);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, position, approvedMaterial);
    }
}
