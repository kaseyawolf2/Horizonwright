package io.github.kaseyawolf2.horizonwright.forge.client.repair;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import io.github.kaseyawolf2.horizonwright.forge.client.repair.TinkersRepairCompatibilityInspector.ArtifactEvidence;
import io.github.kaseyawolf2.horizonwright.forge.client.repair.TinkersRepairCompatibilityInspector.ArtifactExpectation;

public class TinkersRepairCompatibilityInspectorTest {

    @Test
    public void acceptsOnlyTheThreePinnedReferenceArtifactsInProduction() {
        TinkersRepairCompatibilityStatus status = new TinkersRepairCompatibilityInspector()
            .inspect(referenceEvidence(), false);

        assertTrue(status.isAvailable());
        assertTrue(status.isReferenceBytes());
    }

    @Test
    public void missingDuplicateVersionAndHashChangesEachDisableRepair() {
        List<ArtifactEvidence> missing = referenceEvidence();
        missing.remove(0);
        assertFalse(inspect(missing, false).isAvailable());

        List<ArtifactEvidence> duplicate = referenceEvidence();
        duplicate.add(evidence(TinkersRepairCompatibilityInspector.TCONSTRUCT));
        assertFalse(inspect(duplicate, false).isAvailable());

        List<ArtifactEvidence> version = referenceEvidence();
        version.set(
            0,
            new ArtifactEvidence(
                TinkersRepairCompatibilityInspector.TCONSTRUCT.getModId(),
                "changed",
                TinkersRepairCompatibilityInspector.TCONSTRUCT.getSha256(),
                false));
        assertFalse(inspect(version, false).isAvailable());

        List<ArtifactEvidence> hash = referenceEvidence();
        hash.set(
            1,
            new ArtifactEvidence(
                TinkersRepairCompatibilityInspector.TGREGWORKS.getModId(),
                TinkersRepairCompatibilityInspector.TGREGWORKS.getVersion(),
                "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
                false));
        assertFalse(inspect(hash, false).isAvailable());
    }

    @Test
    public void deobfuscatedDirectoriesAreAvailableButNeverClaimReferenceBytes() {
        List<ArtifactEvidence> development = referenceEvidence();
        ArtifactExpectation mantle = TinkersRepairCompatibilityInspector.MANTLE;
        development.set(2, new ArtifactEvidence(mantle.getModId(), mantle.getVersion(), null, true));

        assertFalse(inspect(development, false).isAvailable());
        TinkersRepairCompatibilityStatus accepted = inspect(development, true);
        assertTrue(accepted.isAvailable());
        assertFalse(accepted.isReferenceBytes());
    }

    private static TinkersRepairCompatibilityStatus inspect(List<ArtifactEvidence> evidence, boolean development) {
        return new TinkersRepairCompatibilityInspector().inspect(evidence, development);
    }

    private static List<ArtifactEvidence> referenceEvidence() {
        List<ArtifactEvidence> result = new ArrayList<>();
        result.add(evidence(TinkersRepairCompatibilityInspector.TCONSTRUCT));
        result.add(evidence(TinkersRepairCompatibilityInspector.TGREGWORKS));
        result.add(evidence(TinkersRepairCompatibilityInspector.MANTLE));
        return result;
    }

    private static ArtifactEvidence evidence(ArtifactExpectation expected) {
        return new ArtifactEvidence(expected.getModId(), expected.getVersion(), expected.getSha256(), false);
    }
}
