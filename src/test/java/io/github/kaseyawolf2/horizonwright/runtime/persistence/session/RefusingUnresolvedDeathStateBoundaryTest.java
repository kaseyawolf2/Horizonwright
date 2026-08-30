package io.github.kaseyawolf2.horizonwright.runtime.persistence.session;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import io.github.kaseyawolf2.horizonwright.core.persistence.DimensionPosition;
import io.github.kaseyawolf2.horizonwright.core.persistence.PersistedGraveState;
import io.github.kaseyawolf2.horizonwright.core.persistence.UnresolvedDeathState;
import io.github.kaseyawolf2.horizonwright.core.safety.death.DeathSafetyState;
import io.github.kaseyawolf2.horizonwright.core.safety.death.DeathSignal;
import io.github.kaseyawolf2.horizonwright.core.safety.death.RecoveryPhase;

public class RefusingUnresolvedDeathStateBoundaryTest {

    @Test
    public void cleanProfileCanRestoreAndSnapshotNullState() {
        RefusingUnresolvedDeathStateBoundary boundary = new RefusingUnresolvedDeathStateBoundary();

        boundary.restore(null);

        assertNull(boundary.snapshot());
        assertThrows(IllegalStateException.class, () -> boundary.restore(null));
    }

    @Test
    public void unresolvedDeathFailsBeforeTheSessionCanExposeARuntime() {
        RefusingUnresolvedDeathStateBoundary boundary = new RefusingUnresolvedDeathStateBoundary();

        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> boundary.restore(unresolved()));

        assertTrue(
            failure.getMessage()
                .contains("live death-safety runtime"));
    }

    private static UnresolvedDeathState unresolved() {
        return new UnresolvedDeathState(
            1L,
            1L,
            1L,
            10L,
            20L,
            "server",
            "world",
            new DimensionPosition(0, 1, 64, 1),
            "player",
            null,
            "inventory",
            DeathSignal.LETHAL_HEALTH_PACKET,
            DeathSafetyState.DEATH_LATCHED,
            RecoveryPhase.AWAITING_RESPAWN,
            false,
            null,
            false,
            0,
            0,
            PersistedGraveState.none());
    }
}
