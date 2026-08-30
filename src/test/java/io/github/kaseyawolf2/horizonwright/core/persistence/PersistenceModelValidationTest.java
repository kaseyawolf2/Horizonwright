package io.github.kaseyawolf2.horizonwright.core.persistence;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

import io.github.kaseyawolf2.horizonwright.core.safety.death.DeathSafetyState;
import io.github.kaseyawolf2.horizonwright.core.safety.death.DeathSignal;
import io.github.kaseyawolf2.horizonwright.core.safety.death.RecoveryPhase;

public class PersistenceModelValidationTest {

    @Test
    public void reassociationMustRepresentAnExplicitUserConfirmedIdentityChange() {
        try {
            new ProfileReassociation(
                "server:25565",
                "world-a",
                "server:25565",
                "world-b",
                "confirmation-1",
                20L,
                false);
            fail("expected unconfirmed reassociation rejection");
        } catch (IllegalArgumentException expected) {
            assertTrue(
                expected.getMessage()
                    .contains("user-confirmed"));
        }

        try {
            new ProfileReassociation("server:25565", "world-a", "server:25565", "world-a", "confirmation-2", 20L, true);
            fail("expected no-op reassociation rejection");
        } catch (IllegalArgumentException expected) {
            assertTrue(
                expected.getMessage()
                    .contains("must change"));
        }
    }

    @Test
    public void profileRejectsAReassociationThatDoesNotEndAtItsCurrentWorldIdentity() {
        WorldProfileIdentity identity = new WorldProfileIdentity(
            "profile-one",
            "Profile One",
            "server:25565",
            "world-current",
            10L);
        ProfileReassociation stale = new ProfileReassociation(
            "server:25565",
            "world-old",
            "server:25565",
            "world-not-current",
            "confirmation-1",
            20L,
            true);

        try {
            new ProfileEnvelope(
                30L,
                identity,
                Collections.singletonList(stale),
                Collections.<NamedLocation>emptyList(),
                Collections.<NamedRoute>emptyList());
            fail("expected stale reassociation rejection");
        } catch (IllegalArgumentException expected) {
            assertTrue(
                expected.getMessage()
                    .contains("current profile world identity"));
        }
    }

    @Test
    public void namedRoutesRequireAtLeastTwoDimensionBearingNodes() {
        try {
            new NamedRoute("route-one", "Route One", Collections.singletonList(new RouteNode(0, 0, 64, 0, null)));
            fail("expected one-node route rejection");
        } catch (IllegalArgumentException expected) {
            assertTrue(
                expected.getMessage()
                    .contains("at least two"));
        }

        new NamedRoute(
            "route-two",
            "Route Two",
            Arrays.asList(new RouteNode(0, 0, 64, 0, null), new RouteNode(-27, 1, 80, 1, null)));
    }

    @Test
    public void unresolvedDeathMustBelongToTheRuntimeWorldBinding() {
        UnresolvedDeathState death = new UnresolvedDeathState(
            1L,
            1L,
            1L,
            10L,
            20L,
            "server:25565",
            "world-old",
            new DimensionPosition(0, 0, 64, 0),
            "player-object-1",
            null,
            "inventory-fingerprint",
            DeathSignal.LETHAL_HEALTH_PACKET,
            DeathSafetyState.DEATH_LATCHED,
            RecoveryPhase.AWAITING_RESPAWN,
            false,
            null,
            true,
            0,
            0,
            PersistedGraveState.none());

        try {
            new RuntimeEnvelope(30L, "profile-one", "server:25565", "world-current", death);
            fail("expected cross-world death state rejection");
        } catch (IllegalArgumentException expected) {
            assertTrue(
                expected.getMessage()
                    .contains("runtime server and world fingerprint"));
        }
    }

    @Test
    public void respawnRequestedStateCannotForgetThatItsOneShotRequestWasConsumed() {
        try {
            new UnresolvedDeathState(
                1L,
                1L,
                1L,
                10L,
                20L,
                "server:25565",
                "world-one",
                new DimensionPosition(0, 0, 64, 0),
                "player-object-1",
                null,
                "inventory-fingerprint",
                DeathSignal.LETHAL_HEALTH_PACKET,
                DeathSafetyState.RESPAWN_REQUESTED,
                RecoveryPhase.AWAITING_RESPAWN,
                false,
                null,
                true,
                0,
                0,
                PersistedGraveState.none());
            fail("expected forgotten respawn consumption rejection");
        } catch (IllegalArgumentException expected) {
            assertTrue(
                expected.getMessage()
                    .contains("exactly-once respawn request"));
        }
    }
}
