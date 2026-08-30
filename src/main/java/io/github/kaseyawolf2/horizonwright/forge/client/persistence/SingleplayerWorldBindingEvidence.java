package io.github.kaseyawolf2.horizonwright.forge.client.persistence;

import java.util.Objects;

/** Opaque, stable evidence used to look up an explicitly partitioned singleplayer profile. */
public final class SingleplayerWorldBindingEvidence {

    private final String locatorKey;
    private final String worldFingerprint;

    SingleplayerWorldBindingEvidence(String locatorKey, String markerId) {
        if (locatorKey == null || locatorKey.trim()
            .isEmpty()) {
            throw new IllegalArgumentException("locatorKey must not be blank");
        }
        if (markerId == null || markerId.trim()
            .isEmpty()) {
            throw new IllegalArgumentException("markerId must not be blank");
        }
        this.locatorKey = locatorKey;
        this.worldFingerprint = "uuid:" + markerId;
    }

    public String getLocatorKey() {
        return locatorKey;
    }

    public String getWorldFingerprint() {
        return worldFingerprint;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SingleplayerWorldBindingEvidence)) {
            return false;
        }
        SingleplayerWorldBindingEvidence that = (SingleplayerWorldBindingEvidence) other;
        return Objects.equals(locatorKey, that.locatorKey) && Objects.equals(worldFingerprint, that.worldFingerprint);
    }

    @Override
    public int hashCode() {
        return Objects.hash(locatorKey, worldFingerprint);
    }
}
