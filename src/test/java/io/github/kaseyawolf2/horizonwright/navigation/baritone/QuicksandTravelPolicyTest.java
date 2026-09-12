package io.github.kaseyawolf2.horizonwright.navigation.baritone;

import static org.junit.Assert.*;

import org.junit.Test;

public class QuicksandTravelPolicyTest {

    @Test
    public void thornsAndQuicksandAvoidanceUsesExactMetadata() {
        assertTrue(QuicksandTravelPolicy.isHazard("BiomesOPlenty:plants", 5));
        assertTrue(QuicksandTravelPolicy.isHazard("BiomesOPlenty:mud", 1));
        assertFalse(QuicksandTravelPolicy.isHazard("BiomesOPlenty:plants", 4));
        assertFalse(QuicksandTravelPolicy.isHazard("BiomesOPlenty:plants", 6));
        assertFalse(QuicksandTravelPolicy.isHazard("BiomesOPlenty:mud", 0));
    }

    @Test
    public void recognizesQuicksandWithoutBlockingOrdinaryMud() {
        assertTrue(QuicksandTravelPolicy.isQuicksand("BiomesOPlenty:mud", 1));
        assertFalse(QuicksandTravelPolicy.isQuicksand("BiomesOPlenty:mud", 0));
        assertFalse(QuicksandTravelPolicy.isQuicksand("minecraft:sand", 1));
        assertFalse(QuicksandTravelPolicy.isQuicksand("other:mud", 1));
    }

    @Test
    public void rejectsFootSupportDiagonalCornersAndJumpedOverQuicksand() {
        assertTrue(crosses(0, 64, 0, 1, 64, 0, 1, 63, 0));
        assertTrue(crosses(0, 64, 0, 1, 64, 0, 1, 64, 0));
        assertTrue(crosses(0, 64, 0, 1, 64, 1, 1, 63, 0));
        assertTrue(crosses(0, 64, 0, 4, 64, 0, 2, 63, 0));
        assertTrue(crosses(0, 68, 0, 1, 64, 0, 1, 65, 0));
    }

    @Test
    public void allowsDryRoutesAndEscapeFromAnExistingQuicksandCell() {
        assertFalse(crosses(0, 64, 0, 1, 64, 0, 0, 63, 0));
        assertFalse(crosses(0, 64, 0, 1, 64, 0, 4, 63, 0));
        assertFalse(crosses(-4, 64, -4, -3, 64, -4, -8, 63, -8));
    }

    private static boolean crosses(int sx, int sy, int sz, int dx, int dy, int dz, int hx, int hy, int hz) {
        return QuicksandTravelPolicy
            .crossesQuicksand(sx, sy, sz, dx, dy, dz, (x, y, z) -> x == hx && y == hy && z == hz);
    }
}
