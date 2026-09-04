package io.github.kaseyawolf2.horizonwright.core.excavation;

/** Client-thread observations reduced to game-independent excavation semantics. */
public enum ExcavationBlockClassification {
    AIR,
    IGNORED_FOLIAGE,
    BREAKABLE,
    PROTECTED_GRAVE,
    PROTECTED_INFRASTRUCTURE,
    FLUID_SOURCE_REACHABLE,
    FLUID_SOURCE_UNREACHABLE,
    FLUID_FLOWING,
    UNREACHABLE,
    FAILED
}
