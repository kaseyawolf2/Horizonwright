package io.github.kaseyawolf2.horizonwright.runtime.persistence.profile;

/** Explicit safe failure from profile asset loading, validation, identity checking, or saving. */
public final class ProfileAssetEditingException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ProfileAssetEditingException(String message) {
        super(message);
    }

    public ProfileAssetEditingException(String message, Throwable cause) {
        super(message, cause);
    }
}
