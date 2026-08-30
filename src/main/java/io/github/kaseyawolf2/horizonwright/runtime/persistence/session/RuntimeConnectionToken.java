package io.github.kaseyawolf2.horizonwright.runtime.persistence.session;

import java.util.Objects;

/** Opaque stable key used to deduplicate live connection callbacks. */
public final class RuntimeConnectionToken {

    private final String value;

    public RuntimeConnectionToken(String value) {
        if (value == null || value.trim()
            .isEmpty()) {
            throw new IllegalArgumentException("connection token must not be blank");
        }
        this.value = value.trim();
    }

    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof RuntimeConnectionToken)) {
            return false;
        }
        RuntimeConnectionToken that = (RuntimeConnectionToken) other;
        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
