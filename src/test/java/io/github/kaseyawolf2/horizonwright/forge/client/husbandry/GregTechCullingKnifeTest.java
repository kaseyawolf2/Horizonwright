package io.github.kaseyawolf2.horizonwright.forge.client.husbandry;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class GregTechCullingKnifeTest {

    @Test
    public void onlyIntegratedKnifeTypesAndMetadataAreAccepted() {
        assertTrue(GregTechCullingKnife.isKnife("gregtech.common.tools.ToolKnife", 34));
        assertTrue(GregTechCullingKnife.isKnife("gregtech.common.tools.ToolButcheryKnife", 36));
        assertFalse(GregTechCullingKnife.isKnife("gregtech.common.tools.ToolSword", 34));
        assertFalse(GregTechCullingKnife.isKnife("example.Knife", 34));
        assertFalse(GregTechCullingKnife.isKnife("gregtech.common.tools.ToolKnife", 35));
    }

    @Test
    public void lootingOutranksDurabilityAndEqualLootingPrefersRemainingDurability() {
        GregTechCullingKnife.Candidate highLooting = new GregTechCullingKnife.Candidate(20, 5, 1000);
        GregTechCullingKnife.Candidate durable = new GregTechCullingKnife.Candidate(0, 2, 100000);
        assertTrue(highLooting.preferredTo(durable));
        assertFalse(durable.preferredTo(highLooting));
        assertTrue(new GregTechCullingKnife.Candidate(15, 5, 2000).preferredTo(highLooting));
        assertFalse(new GregTechCullingKnife.Candidate(15, 5, 1000).preferredTo(highLooting));
    }
}
