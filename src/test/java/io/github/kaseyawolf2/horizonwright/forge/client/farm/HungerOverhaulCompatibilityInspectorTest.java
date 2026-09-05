package io.github.kaseyawolf2.horizonwright.forge.client.farm;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

import io.github.kaseyawolf2.horizonwright.forge.client.farm.HungerOverhaulCompatibilityInspector.ArtifactEvidence;

public class HungerOverhaulCompatibilityInspectorTest {

    @Test
    public void acceptsOnlyThePinnedReferenceArtifactInProduction() {
        HungerOverhaulCompatibilityStatus status = inspect(referenceEvidence(), false);
        assertTrue(status.isAvailable());
        assertTrue(status.isReferenceBytes());
    }

    @Test
    public void missingDuplicateVersionAndHashChangesEachDisableRightClickIntegration() {
        assertFalse(inspect(Collections.<ArtifactEvidence>emptyList(), false).isAvailable());
        List<ArtifactEvidence> duplicate = referenceEvidence();
        duplicate.add(reference());
        assertFalse(inspect(duplicate, false).isAvailable());
        assertFalse(
            inspect(
                Collections.singletonList(
                    new ArtifactEvidence(
                        HungerOverhaulCompatibilityInspector.MOD_ID,
                        "changed",
                        HungerOverhaulCompatibilityInspector.SHA256,
                        false)),
                false).isAvailable());
        assertFalse(
            inspect(
                Collections.singletonList(
                    new ArtifactEvidence(
                        HungerOverhaulCompatibilityInspector.MOD_ID,
                        HungerOverhaulCompatibilityInspector.VERSION,
                        "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
                        false)),
                false).isAvailable());
    }

    @Test
    public void developmentSourcesAreNeverClaimedAsReferenceBytes() {
        ArtifactEvidence directory = new ArtifactEvidence(
            HungerOverhaulCompatibilityInspector.MOD_ID,
            HungerOverhaulCompatibilityInspector.VERSION,
            null,
            true);
        assertFalse(inspect(Collections.singletonList(directory), false).isAvailable());
        HungerOverhaulCompatibilityStatus development = inspect(Collections.singletonList(directory), true);
        assertTrue(development.isAvailable());
        assertFalse(development.isReferenceBytes());
    }

    private static HungerOverhaulCompatibilityStatus inspect(List<ArtifactEvidence> evidence, boolean development) {
        return new HungerOverhaulCompatibilityInspector().inspect(evidence, development);
    }

    private static List<ArtifactEvidence> referenceEvidence() {
        List<ArtifactEvidence> result = new ArrayList<>();
        result.add(reference());
        return result;
    }

    private static ArtifactEvidence reference() {
        return new ArtifactEvidence(
            HungerOverhaulCompatibilityInspector.MOD_ID,
            HungerOverhaulCompatibilityInspector.VERSION,
            HungerOverhaulCompatibilityInspector.SHA256,
            false);
    }
}
