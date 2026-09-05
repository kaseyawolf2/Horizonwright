package io.github.kaseyawolf2.horizonwright.core.persistence;

import java.util.Objects;
import java.util.UUID;

/** One operator-designated livestock identity protected within a named pen. */
public final class ProtectedLivestock {

    private final String penId;
    private final String entityIdentity;

    public ProtectedLivestock(String penId, String entityIdentity) {
        this.penId = penId;
        this.entityIdentity = entityIdentity;
        validate();
    }

    public String getPenId() {
        return penId;
    }

    public String getEntityIdentity() {
        return entityIdentity;
    }

    void validate() {
        requireNonBlank(penId, "protected livestock penId");
        requireNonBlank(entityIdentity, "protected livestock entityIdentity");
        try {
            UUID parsed = UUID.fromString(entityIdentity);
            if (!parsed.toString()
                .equals(entityIdentity)) {
                throw new IllegalArgumentException("protected livestock entityIdentity must be a canonical UUID");
            }
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException("protected livestock entityIdentity must be a canonical UUID", failure);
        }
    }

    private static void requireNonBlank(String value, String field) {
        if (value == null || value.trim()
            .isEmpty()) throw new IllegalArgumentException(field + " must not be blank");
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ProtectedLivestock)) return false;
        ProtectedLivestock that = (ProtectedLivestock) other;
        return Objects.equals(penId, that.penId) && Objects.equals(entityIdentity, that.entityIdentity);
    }

    @Override
    public int hashCode() {
        return Objects.hash(penId, entityIdentity);
    }
}
