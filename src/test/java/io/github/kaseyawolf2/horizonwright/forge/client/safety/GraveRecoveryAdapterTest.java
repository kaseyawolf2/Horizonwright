package io.github.kaseyawolf2.horizonwright.forge.client.safety;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Collections;

import org.junit.Test;

import io.github.kaseyawolf2.horizonwright.core.safety.death.DimensionBlockPosition;
import io.github.kaseyawolf2.horizonwright.core.safety.death.GraveCandidate;
import io.github.kaseyawolf2.horizonwright.core.safety.death.GraveIdentity;
import io.github.kaseyawolf2.horizonwright.core.safety.death.GraveResolution;
import io.github.kaseyawolf2.horizonwright.core.safety.death.GraveSearchObservation;
import io.github.kaseyawolf2.horizonwright.core.safety.death.GraveSearchStatus;
import io.github.kaseyawolf2.horizonwright.core.safety.death.InventoryManifest;
import io.github.kaseyawolf2.horizonwright.core.safety.death.RecoveryVerificationObservation;

public class GraveRecoveryAdapterTest {

    @Test
    public void openBlocksGraveIsPermanentlyDeniedToEveryGenericActionFamily() {
        GraveActionProtection protection = new OpenBlocksGraveActionProtection();

        for (GraveActionKind action : GraveActionKind.values()) {
            assertFalse(protection.allowsGenericAction("OpenBlocks:grave", action));
            assertTrue(protection.allowsGenericAction("minecraft:stone", action));
        }
    }

    @Test
    public void scannerSnapshotsBuildCompleteKernelObservationsWithoutLiveWorldReferences() {
        GraveCandidate candidate = new GraveCandidate(
            new GraveIdentity("grave", new DimensionBlockPosition(0, 1, 64, 1)),
            "owner",
            InventoryManifest.empty(27));
        GraveRegionScan scan = new GraveRegionScan(GraveSearchStatus.COMPLETE, Collections.singletonList(candidate));
        ClientInventorySnapshot inventory = new ClientInventorySnapshot(36, Collections.emptyList());

        GraveSearchObservation search = RecoveryObservationFactory.graveSearch(scan, inventory, true);
        RecoveryVerificationObservation verification = RecoveryObservationFactory
            .verification(new GraveInspection(GraveResolution.PRESENT, candidate), inventory);

        assertEquals(GraveSearchStatus.COMPLETE, search.getStatus());
        assertEquals(
            candidate,
            search.getCandidates()
                .get(0));
        assertTrue(search.isEmptyHotbarHandAvailable());
        assertEquals(GraveResolution.PRESENT, verification.getGraveResolution());
        assertEquals(candidate, verification.getGraveCandidate());
    }
}
