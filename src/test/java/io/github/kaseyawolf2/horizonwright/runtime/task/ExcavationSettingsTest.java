package io.github.kaseyawolf2.horizonwright.runtime.task;

import static org.junit.Assert.*;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Test;

import io.github.kaseyawolf2.horizonwright.core.excavation.ExcavationCheckpoint;
import io.github.kaseyawolf2.horizonwright.core.task.TaskCheckpoint;
import io.github.kaseyawolf2.horizonwright.core.task.TaskSpec;

public class ExcavationSettingsTest {

    private TaskSpec original() {
        return ExcavationTask.cleanVolumeCylinder("edit", 0, 0, 0, 8, 40, 42);
    }

    private TaskSpec setting(TaskSpec spec, String key, String value) {
        Map<String, String> params = new LinkedHashMap<>(spec.getParameters());
        params.put(key, value);
        return new TaskSpec(spec.getId(), spec.getType(), spec.getDisplayName(), spec.getLane(), params);
    }

    private TaskCheckpoint checkpoint(TaskSpec spec) {
        return ExcavationTaskCheckpointCodec
            .encode(ExcavationTask.parse(spec), ExcavationCheckpoint.start(ExcavationTask.parse(spec), 12, 7));
    }

    @Test
    public void walkingAndServicesPreserveCheckpointExactly() {
        TaskSpec spec = original();
        TaskCheckpoint cp = checkpoint(spec);
        assertSame(cp, ExcavationSettings.checkpointFor(spec, setting(spec, "movingMining", "true"), cp));
        TaskSpec service = setting(setting(spec, "service.loadoutId", "mining"), "service.storageId", "ores");
        assertSame(cp, ExcavationSettings.checkpointFor(spec, service, cp));
    }

    @Test
    public void orderChangeStartsNewScanWithMonotonicAuthorityAndValidGeometry() {
        TaskSpec spec = original();
        TaskSpec changed = setting(setting(spec, "traversal", "circle-spiral-v1"), "spiralWidth", "8");
        TaskCheckpoint result = ExcavationSettings.checkpointFor(spec, changed, checkpoint(spec));
        ExcavationCheckpoint decoded = ExcavationTaskCheckpointCodec.decode(ExcavationTask.parse(changed), result);
        assertEquals(13, result.getRevision());
        assertEquals(8, decoded.getActionEpoch());
        assertEquals(
            0,
            decoded.getProgress()
                .getProcessed());
        assertEquals(
            42,
            decoded.getFrontier()
                .getLayerY());
    }

    @Test(expected = IllegalArgumentException.class)
    public void boundsCannotSilentlyChange() {
        TaskSpec spec = original();
        ExcavationSettings.checkpointFor(spec, setting(spec, "topY", "43"), checkpoint(spec));
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidBandWidthIsRejected() {
        TaskSpec spec = original();
        ExcavationSettings.checkpointFor(spec, setting(spec, "spiralWidth", "0"), checkpoint(spec));
    }

    @Test(expected = IllegalStateException.class)
    public void uncertainInventoryIsNotDiscarded() {
        TaskSpec spec = original();
        Map<String, String> values = new LinkedHashMap<>(checkpoint(spec).getValues());
        values.put("horizonwright.inventory.version", "1");
        values.put("horizonwright.inventory.preparing", "true");
        values.put("horizonwright.inventory.delegateRevision", "12");
        ExcavationSettings.checkpointFor(spec, setting(spec, "movingMining", "true"), new TaskCheckpoint(20, values));
    }
}
