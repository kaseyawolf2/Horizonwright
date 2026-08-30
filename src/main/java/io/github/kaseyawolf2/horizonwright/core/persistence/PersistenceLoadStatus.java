package io.github.kaseyawolf2.horizonwright.core.persistence;

public enum PersistenceLoadStatus {

    LOADED,
    MISSING,
    CORRUPT,
    NEWER_SCHEMA,
    UNSUPPORTED_SCHEMA,
    WRONG_DOCUMENT_KIND,
    PROFILE_MISMATCH,
    IO_ERROR
}
