package io.github.kaseyawolf2.horizonwright.core.combat;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/** Immutable evidence for one entity in a single observation; never an attack authorization. */
public final class CombatTarget {

    public enum Kind {
        HOSTILE,
        NEUTRAL,
        PASSIVE,
        PLAYER,
        UNKNOWN
    }

    public enum Protection {
        FRIENDLY,
        NAMED,
        TAMED,
        CHILD,
        HUSBANDRY
    }

    private final String identity;
    private final Kind kind;
    private final Set<Protection> protections;
    private final boolean alive;
    private final boolean visible;
    private final double distanceSquared;

    public CombatTarget(String identity, Kind kind, Set<Protection> protections, boolean alive, boolean visible,
        double distanceSquared) {
        if (identity == null || identity.trim()
            .isEmpty()
            || kind == null
            || protections == null
            || !Double.isFinite(distanceSquared)
            || distanceSquared < 0) {
            throw new IllegalArgumentException("valid target identity, kind, protections and distance are required");
        }
        this.identity = identity;
        this.kind = kind;
        EnumSet<Protection> copy = EnumSet.noneOf(Protection.class);
        copy.addAll(protections);
        this.protections = Collections.unmodifiableSet(copy);
        this.alive = alive;
        this.visible = visible;
        this.distanceSquared = distanceSquared;
    }

    public String getIdentity() {
        return identity;
    }

    public Kind getKind() {
        return kind;
    }

    public Set<Protection> getProtections() {
        return protections;
    }

    public boolean isAlive() {
        return alive;
    }

    public boolean isVisible() {
        return visible;
    }

    public double getDistanceSquared() {
        return distanceSquared;
    }
}
