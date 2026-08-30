package io.github.kaseyawolf2.horizonwright.forge.client.safety;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Constructor;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;

import org.junit.Test;

import io.github.kaseyawolf2.horizonwright.core.safety.death.DimensionBlockPosition;
import io.github.kaseyawolf2.horizonwright.core.safety.death.GraveActivationAttempt;
import io.github.kaseyawolf2.horizonwright.core.safety.death.GraveActivationPermit;
import io.github.kaseyawolf2.horizonwright.core.safety.death.GraveIdentity;

public class GraveActivationPacketMatcherTest {

    private static final DimensionBlockPosition POSITION = new DimensionBlockPosition(7, 12, 64, -3);
    private static final GraveIdentity IDENTITY = new GraveIdentity("openblocks-grave-v1:5:Kasey", POSITION);
    private final GraveActivationPacketMatcher matcher = new GraveActivationPacketMatcher();

    @Test
    public void exactEmptyHandSneakingUseBuildsPermitBoundAttempt() {
        GraveActivationPermit permit = permit(IDENTITY);
        GraveActivationPacketSnapshot snapshot = new GraveActivationPacketSnapshot(permit, IDENTITY, 7, true, true);

        GraveActivationAttempt attempt = matcher.match(packet(12, 64, -3, null), snapshot)
            .get();

        assertEquals(91L, attempt.getPermitId());
        assertEquals(4L, attempt.getDeathEpoch());
        assertEquals(IDENTITY, attempt.getTarget());
        assertTrue(attempt.isEmptyHand());
        assertTrue(attempt.isSneaking());
    }

    @Test
    public void wrongCoordinateDimensionOrReplacedTileNeverMatches() {
        GraveActivationPermit permit = permit(IDENTITY);
        assertFalse(
            matcher.match(packet(13, 64, -3, null), snapshot(permit, IDENTITY, 7, true, true))
                .isPresent());
        assertFalse(
            matcher.match(packet(12, 64, -3, null), snapshot(permit, IDENTITY, 0, true, true))
                .isPresent());
        GraveIdentity replacement = new GraveIdentity("openblocks-grave-v1:4:Alex", POSITION);
        assertFalse(
            matcher.match(packet(12, 64, -3, null), snapshot(permit, replacement, 7, true, true))
                .isPresent());
    }

    @Test
    public void heldItemOrMissingInteractionPostureNeverMatches() {
        GraveActivationPermit permit = permit(IDENTITY);
        assertFalse(
            matcher.match(packet(12, 64, -3, new ItemStack(Items.stick)), snapshot(permit, IDENTITY, 7, true, true))
                .isPresent());
        assertFalse(
            matcher.match(packet(12, 64, -3, null), snapshot(permit, IDENTITY, 7, false, true))
                .isPresent());
        assertFalse(
            matcher.match(packet(12, 64, -3, null), snapshot(permit, IDENTITY, 7, true, false))
                .isPresent());
    }

    @Test
    public void absentPublishedSnapshotLeavesOrdinaryUseUnclassified() {
        assertFalse(
            matcher.match(packet(12, 64, -3, null), null)
                .isPresent());
    }

    private static GraveActivationPermit permit(GraveIdentity identity) {
        try {
            Constructor<GraveActivationPermit> constructor = GraveActivationPermit.class
                .getDeclaredConstructor(long.class, long.class, long.class, GraveIdentity.class);
            constructor.setAccessible(true);
            return constructor.newInstance(91L, 8L, 4L, identity);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }

    private static GraveActivationPacketSnapshot snapshot(GraveActivationPermit permit, GraveIdentity target,
        int dimension, boolean emptyHand, boolean sneaking) {
        return new GraveActivationPacketSnapshot(permit, target, dimension, emptyHand, sneaking);
    }

    private static C08PacketPlayerBlockPlacement packet(int x, int y, int z, ItemStack stack) {
        return new C08PacketPlayerBlockPlacement(x, y, z, 1, stack, 0.5F, 0.5F, 0.5F);
    }
}
