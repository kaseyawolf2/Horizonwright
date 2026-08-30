package io.github.kaseyawolf2.horizonwright.core.task;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Test;

public class TaskValueObjectsTest {

    @Test
    public void taskSpecsAndCheckpointsDefensivelyCopyTheirMaps() {
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("dimension", "0");
        TaskSpec spec = new TaskSpec("go-home", "go-to", "Go home", TaskLane.MANUAL, parameters);
        parameters.put("dimension", "7");

        assertEquals(
            "0",
            spec.getParameters()
                .get("dimension"));
        assertUnsupported(
            () -> spec.getParameters()
                .put("x", "12"));

        Map<String, String> progress = new LinkedHashMap<>();
        progress.put("phase", "pathing");
        TaskCheckpoint checkpoint = new TaskCheckpoint(3L, progress);
        progress.put("phase", "done");

        assertEquals(
            "pathing",
            checkpoint.getValues()
                .get("phase"));
        assertUnsupported(
            () -> checkpoint.getValues()
                .clear());
        assertEquals(new TaskCheckpoint(3L, singleton("phase", "pathing")), checkpoint);
        assertNotEquals(TaskCheckpoint.empty(), checkpoint);
    }

    @Test
    public void defaultRetryPolicyIsExactlyOneFiveAndThirtySeconds() {
        RetryPolicy policy = RetryPolicy.defaultPolicy();

        assertEquals(Arrays.asList(1_000L, 5_000L, 30_000L), policy.getBackoffMillis());
        assertEquals(3, policy.getMaximumRetries());
        assertUnsupported(
            () -> policy.getBackoffMillis()
                .add(60_000L));
    }

    @Test
    public void lanePriorityIsFixedAndSafetyFirst() {
        assertTrue(TaskLane.SAFETY.hasPriorityOver(TaskLane.MANUAL));
        assertTrue(TaskLane.MANUAL.hasPriorityOver(TaskLane.CHORE));
        assertTrue(TaskLane.CHORE.hasPriorityOver(TaskLane.FALLBACK));
    }

    private static Map<String, String> singleton(String key, String value) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put(key, value);
        return values;
    }

    private static void assertUnsupported(Runnable mutation) {
        try {
            mutation.run();
            fail("expected immutable collection");
        } catch (UnsupportedOperationException expected) {
            // Expected.
        }
    }
}
