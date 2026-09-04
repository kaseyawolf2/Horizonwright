package io.github.kaseyawolf2.horizonwright.forge.client.farm;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

import io.github.kaseyawolf2.horizonwright.forge.client.farm.PamHarvestCraftCompatibilityInspector.ArtifactEvidence;

public class PamHarvestCraftCompatibilityInspectorTest {

    @Test
    public void acceptsOnlyThePinnedReferenceArtifactInProduction() {
        PamHarvestCraftCompatibilityStatus status = inspect(referenceEvidence(), false);

        assertTrue(status.isAvailable());
        assertTrue(status.isReferenceBytes());
    }

    @Test
    public void missingDuplicateVersionAndHashChangesEachDisablePamAutomation() {
        assertFalse(inspect(Collections.<ArtifactEvidence>emptyList(), false).isAvailable());

        List<ArtifactEvidence> duplicate = referenceEvidence();
        duplicate.add(reference());
        assertFalse(inspect(duplicate, false).isAvailable());

        assertFalse(
            inspect(
                Collections.singletonList(
                    new ArtifactEvidence(
                        PamHarvestCraftCompatibilityInspector.MOD_ID,
                        "changed",
                        PamHarvestCraftCompatibilityInspector.SHA256,
                        false)),
                false).isAvailable());
        assertFalse(
            inspect(
                Collections.singletonList(
                    new ArtifactEvidence(
                        PamHarvestCraftCompatibilityInspector.MOD_ID,
                        PamHarvestCraftCompatibilityInspector.VERSION,
                        "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
                        false)),
                false).isAvailable());
    }

    @Test
    public void developmentSourcesAreNeverClaimedAsReferenceBytes() {
        ArtifactEvidence directory = new ArtifactEvidence(
            PamHarvestCraftCompatibilityInspector.MOD_ID,
            PamHarvestCraftCompatibilityInspector.VERSION,
            null,
            true);
        assertFalse(inspect(Collections.singletonList(directory), false).isAvailable());

        PamHarvestCraftCompatibilityStatus development = inspect(Collections.singletonList(directory), true);
        assertTrue(development.isAvailable());
        assertFalse(development.isReferenceBytes());

        ArtifactEvidence changedBytes = new ArtifactEvidence(
            PamHarvestCraftCompatibilityInspector.MOD_ID,
            PamHarvestCraftCompatibilityInspector.VERSION,
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
            false);
        PamHarvestCraftCompatibilityStatus remapped = inspect(Collections.singletonList(changedBytes), true);
        assertTrue(remapped.isAvailable());
        assertFalse(remapped.isReferenceBytes());
    }

    private static PamHarvestCraftCompatibilityStatus inspect(List<ArtifactEvidence> evidence, boolean development) {
        return new PamHarvestCraftCompatibilityInspector().inspect(evidence, development);
    }

    private static List<ArtifactEvidence> referenceEvidence() {
        List<ArtifactEvidence> result = new ArrayList<>();
        result.add(reference());
        return result;
    }

    private static ArtifactEvidence reference() {
        return new ArtifactEvidence(
            PamHarvestCraftCompatibilityInspector.MOD_ID,
            PamHarvestCraftCompatibilityInspector.VERSION,
            PamHarvestCraftCompatibilityInspector.SHA256,
            false);
    }
}
