package io.github.kaseyawolf2.horizonwright.runtime.persistence.session;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import io.github.kaseyawolf2.horizonwright.core.persistence.DimensionPosition;
import io.github.kaseyawolf2.horizonwright.core.persistence.PersistedGraveState;
import io.github.kaseyawolf2.horizonwright.core.persistence.UnresolvedDeathState;
import io.github.kaseyawolf2.horizonwright.core.safety.death.DeathSafetyState;
import io.github.kaseyawolf2.horizonwright.core.safety.death.DeathSignal;
import io.github.kaseyawolf2.horizonwright.core.safety.death.RecoveryPhase;

public class DisabledDeathSafetyBoundaryTest {

    @Test
    public void retiresLegacyUnresolvedStateWithoutCreatingANewLatch() {
        DisabledDeathSafetyBoundary boundary = new DisabledDeathSafetyBoundary();
        UnresolvedDeathState legacy = new UnresolvedDeathState(
            3L,
            4L,
            5L,
            6L,
            7L,
            "server",
            "world",
            new DimensionPosition(0, 1, 64, 1),
            "old-player",
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

        boundary.restore(legacy);
        boundary.clientTick();
        boundary.disconnect();

        assertNull(boundary.snapshot());
    }

    @Test
    public void stillEnforcesTheSessionRestoreLifecycle() {
        DisabledDeathSafetyBoundary boundary = new DisabledDeathSafetyBoundary();
        try {
            boundary.clientTick();
            fail("expected pre-restore tick refusal");
        } catch (IllegalStateException expected) {
            assertTrue(
                expected.getMessage()
                    .contains("has not been restored"));
        }
        boundary.restore(null);
        try {
            boundary.restore(null);
            fail("expected duplicate restore refusal");
        } catch (IllegalStateException expected) {
            assertTrue(
                expected.getMessage()
                    .contains("already been attempted"));
        }
    }
}
