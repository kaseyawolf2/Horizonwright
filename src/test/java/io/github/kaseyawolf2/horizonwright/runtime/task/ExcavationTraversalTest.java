package io.github.kaseyawolf2.horizonwright.runtime.task;

import static org.junit.Assert.*;

import org.junit.Test;

import io.github.kaseyawolf2.horizonwright.core.base.BasePosition;
import io.github.kaseyawolf2.horizonwright.core.base.NamedArea;
import io.github.kaseyawolf2.horizonwright.core.task.TaskSpec;

public class ExcavationTraversalTest {

    @Test
    public void areaSubmissionsPersistSpiralWithoutChangingLegacyTasks() {
        TaskSpec legacy = ExcavationTask.cleanVolumeCylinder("mine", 0, 0, 0, 2, 60, 62);
        NamedArea area = new NamedArea("plot", "Plot", new BasePosition(0, -2, 60, -2), new BasePosition(0, 2, 62, 2));
        TaskSpec next = ExcavationTask.forArea(legacy, area);
        assertFalse(
            ExcavationTask.parse(legacy)
                .isSpiral());
        assertTrue(
            ExcavationTask.parse(next)
                .isSpiral());
        assertTrue(
            ExcavationTask.parse(next)
                .isRectangle());
        assertEquals(
            "spiral-v1",
            next.getParameters()
                .get("traversal"));
        TaskSpec restored = new TaskSpec(
            next.getId(),
            next.getType(),
            next.getDisplayName(),
            next.getLane(),
            next.getParameters());
        assertEquals(ExcavationTask.parse(next), ExcavationTask.parse(restored));
    }
}
