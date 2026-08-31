package io.github.kaseyawolf2.horizonwright.runtime.task;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

import io.github.kaseyawolf2.horizonwright.core.logistics.NamedLoadout;
import io.github.kaseyawolf2.horizonwright.core.logistics.StorageItemFilter;
import io.github.kaseyawolf2.horizonwright.core.persistence.NamedLocation;
import io.github.kaseyawolf2.horizonwright.core.persistence.NamedRepairStation;
import io.github.kaseyawolf2.horizonwright.core.persistence.NamedStorageEndpoint;
import io.github.kaseyawolf2.horizonwright.core.persistence.ProfileEnvelope;
import io.github.kaseyawolf2.horizonwright.core.persistence.WorldProfileIdentity;
import io.github.kaseyawolf2.horizonwright.core.task.TaskSpec;

public class ExcavationTaskSubmissionTest {

    @Test
    public void exactNamedDependenciesBecomeOneServiceEnabledTask() {
        ProfileEnvelope profile = profile("mining");

        TaskSpec spec = ExcavationTaskSubmission
            .withServices(profile, "quarry", -1, 10, 20, 4, 30, 40, "mining", "ore-chest", "tool-forge", 4, 25);

        assertEquals(
            "mining",
            spec.getParameters()
                .get(ExcavationTask.LOADOUT_ID));
        assertEquals(
            "ore-chest",
            spec.getParameters()
                .get(ExcavationTask.STORAGE_ID));
        assertEquals(
            "tool-forge",
            spec.getParameters()
                .get(ExcavationTask.REPAIR_STATION_ID));
        assertEquals(
            "4",
            spec.getParameters()
                .get(ExcavationTask.RESERVED_TOOL_SLOT));
        assertEquals(
            "25",
            spec.getParameters()
                .get(ExcavationTask.PREDICTED_WORK_DAMAGE));
        assertEquals(
            "-1",
            spec.getParameters()
                .get(ExcavationTask.DIMENSION));
    }

    @Test
    public void missingOrMismatchedProfileDependenciesAreRejectedBeforeSubmission() {
        ProfileEnvelope profile = profile("other-loadout");
        assertThrows(
            IllegalArgumentException.class,
            () -> ExcavationTaskSubmission
                .withServices(profile, "quarry", 0, 0, 0, 1, 10, 10, "mining", "ore-chest", "tool-forge", 4, 10));
        assertThrows(
            IllegalArgumentException.class,
            () -> ExcavationTaskSubmission
                .withServices(profile, "quarry", 0, 0, 0, 1, 10, 10, "missing", "ore-chest", "tool-forge", 4, 10));
    }

    @Test
    public void geometryBoundsRemainEnforcedForCommandAndGuiCallers() {
        assertThrows(
            IllegalArgumentException.class,
            () -> ExcavationTaskSubmission.withoutServices("too-wide", 0, 0, 0, 251, 0, 10));
        assertThrows(
            IllegalArgumentException.class,
            () -> ExcavationTaskSubmission.withoutServices("bad-y", 0, 0, 0, 1, 20, 10));
    }

    private static ProfileEnvelope profile(String stationLoadout) {
        NamedLocation chestLocation = new NamedLocation("chest-location", "Chest", 0, 1, 2, 3);
        NamedLocation stationLocation = new NamedLocation("station-location", "Station", 0, 4, 5, 6);
        NamedLoadout mining = new NamedLoadout("mining", "Mining", Collections.emptyList());
        NamedStorageEndpoint chest = new NamedStorageEndpoint(
            "ore-chest",
            "Ore chest",
            chestLocation.getId(),
            StorageItemFilter.acceptAll());
        NamedRepairStation station = new NamedRepairStation(
            "tool-forge",
            "Tool forge",
            stationLocation.getId(),
            stationLoadout);
        NamedLoadout other = new NamedLoadout("other-loadout", "Other", Collections.emptyList());
        return new ProfileEnvelope(
            20L,
            new WorldProfileIdentity("profile", "Test", "singleplayer", "world", 10L),
            Collections.emptyList(),
            Arrays.asList(chestLocation, stationLocation),
            Collections.emptyList(),
            Arrays.asList(mining, other),
            Collections.singletonList(chest),
            Collections.singletonList(station));
    }
}
