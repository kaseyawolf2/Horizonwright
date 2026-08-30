package io.github.kaseyawolf2.horizonwright.core.excavation;

/** Mutually exclusive terminal accounting for one cylinder-volume position. */
public enum ExcavationTargetOutcome {
    COMPLETED,
    PROTECTED,
    UNREACHABLE,
    FLUID_CONTAINED,
    FAILED
}
