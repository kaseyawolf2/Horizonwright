package io.github.kaseyawolf2.horizonwright.core.safety.death;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/** Result of one deterministic state-machine input. */
public final class DeathSafetyUpdate {

    private final DeathSafetyEventDisposition disposition;
    private final Set<DeathSafetyDirective> directives;
    private final DeathSafetySnapshot snapshot;

    DeathSafetyUpdate(DeathSafetyEventDisposition disposition, Set<DeathSafetyDirective> directives,
        DeathSafetySnapshot snapshot) {
        this.disposition = disposition;
        this.directives = directives.isEmpty() ? Collections.<DeathSafetyDirective>emptySet()
            : Collections.unmodifiableSet(EnumSet.copyOf(directives));
        this.snapshot = snapshot;
    }

    public DeathSafetyEventDisposition getDisposition() {
        return disposition;
    }

    public Set<DeathSafetyDirective> getDirectives() {
        return directives;
    }

    public DeathSafetySnapshot getSnapshot() {
        return snapshot;
    }

    public boolean isAccepted() {
        return disposition == DeathSafetyEventDisposition.ACCEPTED
            || disposition == DeathSafetyEventDisposition.IGNORED_IN_CURRENT_STATE;
    }

    public boolean hasDirective(DeathSafetyDirective directive) {
        return directives.contains(directive);
    }
}
