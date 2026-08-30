package io.github.kaseyawolf2.horizonwright.forge.client.safety;

/** Generic action families which must never target a protected grave. */
public enum GraveActionKind {
    MINING,
    USE,
    COMBAT_TARGET,
    SCAVENGE
}
