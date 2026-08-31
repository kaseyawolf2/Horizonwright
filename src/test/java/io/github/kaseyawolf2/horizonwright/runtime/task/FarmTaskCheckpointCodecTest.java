package io.github.kaseyawolf2.horizonwright.runtime.task;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import java.util.Arrays;

import org.junit.Test;

import io.github.kaseyawolf2.horizonwright.core.base.BasePosition;
import io.github.kaseyawolf2.horizonwright.core.base.CropFamily;
import io.github.kaseyawolf2.horizonwright.core.base.CropObservation;
import io.github.kaseyawolf2.horizonwright.core.base.FarmPassCheckpoint;
import io.github.kaseyawolf2.horizonwright.core.base.NamedArea;
import io.github.kaseyawolf2.horizonwright.core.task.TaskCheckpoint;
import io.github.kaseyawolf2.horizonwright.core.task.TaskLane;
import io.github.kaseyawolf2.horizonwright.core.task.TaskSpec;

public class FarmTaskCheckpointCodecTest {

    private static final NamedArea PLOT = new NamedArea(
        "north-field",
        "North field",
        new BasePosition(0, -2, 60, 3),
        new BasePosition(0, 8, 70, 12));
    private static final CropObservation WHEAT = crop(1, 64, 4, "wheat-7", "minecraft:wheat_seeds", true, true);
    private static final CropObservation CARROT = crop(2, 64, 4, "carrot-3", "minecraft:carrot", true, false);

    @Test
    public void exactFrozenPassRoundTripsAcrossRestart() {
        TaskSpec spec = FarmTask.finitePass("farm-1", "north-field", 8);
        FarmPassCheckpoint original = FarmPassCheckpoint.start(PLOT, 7L, Arrays.asList(WHEAT, CARROT));

        TaskCheckpoint encoded = FarmTaskCheckpointCodec.encode(spec, original, Arrays.asList(WHEAT, CARROT));
        FarmPassCheckpoint restored = FarmTaskCheckpointCodec.decode(spec, encoded);

        assertEquals(original, restored);
        assertEquals(7L, encoded.getRevision());
        assertEquals(
            "2",
            encoded.getValues()
                .get("observationCount"));
    }

    @Test
    public void emptyCheckpointMeansNoPassHasBeenFrozenYet() {
        assertNull(
            FarmTaskCheckpointCodec.decode(FarmTask.finitePass("farm", "north-field", 0), TaskCheckpoint.empty()));
    }

    @Test
    public void wrongPlotAlteredEvidenceAndRevisionAreRejected() {
        TaskSpec spec = FarmTask.finitePass("farm", "north-field", 4);
        FarmPassCheckpoint checkpoint = FarmPassCheckpoint.start(PLOT, 3L, Arrays.asList(WHEAT));
        assertThrows(
            IllegalArgumentException.class,
            () -> FarmTaskCheckpointCodec.encode(
                spec,
                checkpoint,
                Arrays.asList(crop(1, 64, 4, "changed", "minecraft:wheat_seeds", true, true))));
        assertThrows(
            IllegalArgumentException.class,
            () -> FarmTaskCheckpointCodec
                .encode(FarmTask.finitePass("other", "south-field", 4), checkpoint, Arrays.asList(WHEAT)));

        TaskCheckpoint encoded = FarmTaskCheckpointCodec.encode(spec, checkpoint, Arrays.asList(WHEAT));
        assertThrows(
            IllegalArgumentException.class,
            () -> FarmTaskCheckpointCodec
                .decode(spec, new TaskCheckpoint(encoded.getRevision() + 1L, encoded.getValues())));
    }

    @Test
    public void farmTaskIsAlwaysChoreLaneAndValidatesSeedReserve() {
        TaskSpec spec = FarmTask.finitePass("farm", "north-field", 12);
        assertEquals(TaskLane.CHORE, spec.getLane());
        assertEquals("north-field", FarmTask.plotId(spec));
        assertEquals(12, FarmTask.minimumSeedReserve(spec));
        assertThrows(IllegalArgumentException.class, () -> FarmTask.finitePass("farm", "north-field", -1));
        assertThrows(
            IllegalArgumentException.class,
            () -> FarmTask.minimumSeedReserve(
                new TaskSpec(
                    "bad",
                    FarmTask.TYPE,
                    "Bad",
                    TaskLane.CHORE,
                    java.util.Collections.singletonMap(FarmTask.PLOT_ID, "north-field"))));
    }

    private static CropObservation crop(int x, int y, int z, String fingerprint, String seed, boolean known,
        boolean mature) {
        return new CropObservation(
            new BasePosition(0, x, y, z),
            CropFamily.VANILLA,
            fingerprint,
            seed,
            known,
            mature,
            false);
    }
}
