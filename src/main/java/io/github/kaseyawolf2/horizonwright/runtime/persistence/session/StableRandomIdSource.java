package io.github.kaseyawolf2.horizonwright.runtime.persistence.session;

/** Injected source for persistence-safe random identifiers. */
@FunctionalInterface
public interface StableRandomIdSource {

    String nextId();
}
