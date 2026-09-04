package io.github.kaseyawolf2.horizonwright.navigation.baritone;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class CropsNhTravelPolicyTest {

    private final CropsNhTravelPolicy policy = new CropsNhTravelPolicy();

    @Test
    public void heavilyPenalizesOnlyVerticalLandingsIntoCropSticks() {
        assertEquals(CropsNhTravelPolicy.CROP_LANDING_PENALTY, policy.landingPenalty(65, 64, true), 0D);
        assertEquals(CropsNhTravelPolicy.CROP_LANDING_PENALTY, policy.landingPenalty(63, 64, true), 0D);
        assertEquals(0D, policy.landingPenalty(64, 64, true), 0D);
        assertEquals(0D, policy.landingPenalty(64, 65, false), 0D);
    }

    @Test
    public void recognizesOnlyTheExactCropsNhRegistryIdentity() {
        assertEquals(true, CropsNhTravelPolicy.isCropSticksId("cropsnh:cropSticks"));
        assertEquals(false, CropsNhTravelPolicy.isCropSticksId("minecraft:wheat"));
        assertEquals(false, CropsNhTravelPolicy.isCropSticksId("othermod:cropSticks"));
    }
}
