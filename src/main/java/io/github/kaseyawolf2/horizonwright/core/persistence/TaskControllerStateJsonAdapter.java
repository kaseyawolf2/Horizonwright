package io.github.kaseyawolf2.horizonwright.core.persistence;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import io.github.kaseyawolf2.horizonwright.core.task.BlockedCause;
import io.github.kaseyawolf2.horizonwright.core.task.BlockedReason;
import io.github.kaseyawolf2.horizonwright.core.task.RestoredTaskSnapshot;
import io.github.kaseyawolf2.horizonwright.core.task.ScheduleRule;
import io.github.kaseyawolf2.horizonwright.core.task.ScheduleSnapshot;
import io.github.kaseyawolf2.horizonwright.core.task.ScheduleState;
import io.github.kaseyawolf2.horizonwright.core.task.ScheduleTrigger;
import io.github.kaseyawolf2.horizonwright.core.task.ScheduledTaskSpec;
import io.github.kaseyawolf2.horizonwright.core.task.SchedulerSnapshot;
import io.github.kaseyawolf2.horizonwright.core.task.TaskCheckpoint;
import io.github.kaseyawolf2.horizonwright.core.task.TaskControllerState;
import io.github.kaseyawolf2.horizonwright.core.task.TaskLane;
import io.github.kaseyawolf2.horizonwright.core.task.TaskSpec;
import io.github.kaseyawolf2.horizonwright.core.task.TaskState;
import io.github.kaseyawolf2.horizonwright.core.task.TaskSuspensionReason;

/**
 * Explicit schema-v1 adapter that reconstructs every task value through its validating constructor.
 *
 * <p>
 * Gson's reflective allocation bypasses constructors, so using it directly for controller state would admit invalid
 * queue, schedule, checkpoint, and epoch combinations from disk.
 * </p>
 */
final class TaskControllerStateJsonAdapter
    implements JsonSerializer<TaskControllerState>, JsonDeserializer<TaskControllerState> {

    @Override
    public JsonElement serialize(TaskControllerState state, Type type, JsonSerializationContext context) {
        if (state == null) {
            throw new JsonParseException("taskControllerState must not be null");
        }
        return encodeState(state);
    }

    @Override
    public TaskControllerState deserialize(JsonElement json, Type type, JsonDeserializationContext context)
        throws JsonParseException {
        JsonObject root = requireObject(json, "taskControllerState");
        long lastActionEpoch = requireLong(root, "lastActionEpoch");
        List<RestoredTaskSnapshot> tasks = readTasks(requireArray(root, "tasks"));
        SchedulerSnapshot scheduler = readScheduler(requireObject(root, "scheduler"));
        return new TaskControllerState(lastActionEpoch, tasks, scheduler);
    }

    static boolean statesEqual(TaskControllerState left, TaskControllerState right) {
        if (left == right) {
            return true;
        }
        return left != null && right != null && encodeState(left).equals(encodeState(right));
    }

    static int stateHashCode(TaskControllerState state) {
        return state == null ? 0 : encodeState(state).hashCode();
    }

    private static JsonObject encodeState(TaskControllerState state) {
        JsonObject root = new JsonObject();
        root.addProperty("lastActionEpoch", state.getLastActionEpoch());
        JsonArray tasks = new JsonArray();
        for (RestoredTaskSnapshot task : state.getTasks()) {
            tasks.add(encodeTask(task));
        }
        root.add("tasks", tasks);
        root.add("scheduler", encodeScheduler(state.getScheduler()));
        return root;
    }

    private static JsonObject encodeTask(RestoredTaskSnapshot task) {
        JsonObject object = new JsonObject();
        object.add("spec", encodeTaskSpec(task.getSpec()));
        object.addProperty(
            "state",
            task.getState()
                .name());
        object.add("checkpoint", encodeCheckpoint(task.getCheckpoint()));
        object.addProperty("retryCount", task.getRetryCount());
        object.addProperty("remainingDelayMillis", task.getRemainingDelayMillis());
        object.addProperty(
            "suspensionReason",
            task.getSuspensionReason()
                .name());
        object.add(
            "blockedReason",
            task.getBlockedReason()
                .isPresent()
                    ? encodeBlockedReason(
                        task.getBlockedReason()
                            .get())
                    : JsonNull.INSTANCE);
        object.addProperty("queuePosition", task.getQueuePosition());
        object.addProperty("rejectedStaleResults", task.getRejectedStaleResults());
        object.addProperty("detail", task.getDetail());
        if (task.getSourceScheduleId()
            .isPresent()) {
            object.addProperty(
                "sourceScheduleId",
                task.getSourceScheduleId()
                    .get());
        } else {
            object.add("sourceScheduleId", JsonNull.INSTANCE);
        }
        return object;
    }

    private static JsonObject encodeTaskSpec(TaskSpec spec) {
        JsonObject object = new JsonObject();
        object.addProperty("id", spec.getId());
        object.addProperty("type", spec.getType());
        object.addProperty("displayName", spec.getDisplayName());
        object.addProperty(
            "lane",
            spec.getLane()
                .name());
        object.add("parameters", encodeStringMap(spec.getParameters()));
        return object;
    }

    private static JsonObject encodeCheckpoint(TaskCheckpoint checkpoint) {
        JsonObject object = new JsonObject();
        object.addProperty("revision", checkpoint.getRevision());
        object.add("values", encodeStringMap(checkpoint.getValues()));
        return object;
    }

    private static JsonObject encodeBlockedReason(BlockedReason reason) {
        JsonObject object = new JsonObject();
        object.addProperty(
            "cause",
            reason.getCause()
                .name());
        object.addProperty("detail", reason.getDetail());
        object.addProperty("location", reason.getLocation());
        object.addProperty("retryCount", reason.getRetryCount());
        object.addProperty("missingRequirement", reason.getMissingRequirement());
        object.addProperty("requiredUserAction", reason.getRequiredUserAction());
        return object;
    }

    private static JsonObject encodeScheduler(SchedulerSnapshot scheduler) {
        JsonObject object = new JsonObject();
        object.addProperty("connectedElapsedMillis", scheduler.getConnectedElapsedMillis());
        object.addProperty("lastWorldTimeTicks", scheduler.getLastWorldTimeTicks());
        object.addProperty("connectedAtSnapshot", scheduler.wasConnectedAtSnapshot());
        JsonArray schedules = new JsonArray();
        for (ScheduleSnapshot schedule : scheduler.getSchedules()) {
            schedules.add(encodeSchedule(schedule));
        }
        object.add("schedules", schedules);
        return object;
    }

    private static JsonObject encodeSchedule(ScheduleSnapshot schedule) {
        JsonObject object = new JsonObject();
        object.add("rule", encodeScheduleRule(schedule.getRule()));
        object.addProperty(
            "state",
            schedule.getState()
                .name());
        object.addProperty("sequence", schedule.getSequence());
        object.addProperty("nextConnectedDueMillis", schedule.getNextConnectedDueMillis());
        object.addProperty("lastWorldOccurrence", schedule.getLastWorldOccurrence());
        object.addProperty("idleLatched", schedule.isIdleLatched());
        if (schedule.getLastTaskId()
            .isPresent()) {
            object.addProperty(
                "lastTaskId",
                schedule.getLastTaskId()
                    .get());
        } else {
            object.add("lastTaskId", JsonNull.INSTANCE);
        }
        object.addProperty("totalRuns", schedule.getTotalRuns());
        object.addProperty("catchUpRuns", schedule.getCatchUpRuns());
        return object;
    }

    private static JsonObject encodeScheduleRule(ScheduleRule rule) {
        JsonObject object = new JsonObject();
        object.addProperty("id", rule.getId());
        object.add("task", encodeScheduledTaskSpec(rule.getTask()));
        object.addProperty(
            "trigger",
            rule.getTrigger()
                .name());
        object.addProperty("intervalMillis", rule.getIntervalMillis());
        object.addProperty("initialDelayMillis", rule.getInitialDelayMillis());
        object.addProperty("windowStartTick", rule.getWindowStartTick());
        object.addProperty("windowEndTick", rule.getWindowEndTick());
        JsonArray conditions = new JsonArray();
        for (String condition : rule.getRequiredConditions()) {
            conditions.add(new JsonPrimitive(condition));
        }
        object.add("requiredConditions", conditions);
        object.addProperty("relativeOrder", rule.getRelativeOrder());
        object.addProperty("catchUpAfterReconnect", rule.isCatchUpAfterReconnect());
        return object;
    }

    private static JsonObject encodeScheduledTaskSpec(ScheduledTaskSpec task) {
        JsonObject object = new JsonObject();
        object.addProperty("type", task.getType());
        object.addProperty("displayName", task.getDisplayName());
        object.addProperty(
            "lane",
            task.getLane()
                .name());
        object.add("parameters", encodeStringMap(task.getParameters()));
        return object;
    }

    private static JsonObject encodeStringMap(Map<String, String> values) {
        JsonObject object = new JsonObject();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            object.addProperty(entry.getKey(), entry.getValue());
        }
        return object;
    }

    private static List<RestoredTaskSnapshot> readTasks(JsonArray array) {
        List<RestoredTaskSnapshot> tasks = new ArrayList<>(array.size());
        for (int index = 0; index < array.size(); index++) {
            JsonObject object = requireObject(array.get(index), "tasks[" + index + "]");
            BlockedReason blockedReason = null;
            JsonElement blocked = requireField(object, "blockedReason");
            if (!blocked.isJsonNull()) {
                blockedReason = readBlockedReason(requireObject(blocked, "blockedReason"));
            }
            tasks.add(
                new RestoredTaskSnapshot(
                    readTaskSpec(requireObject(object, "spec")),
                    requireEnum(object, "state", TaskState.class),
                    readCheckpoint(requireObject(object, "checkpoint")),
                    requireInt(object, "retryCount"),
                    requireLong(object, "remainingDelayMillis"),
                    requireEnum(object, "suspensionReason", TaskSuspensionReason.class),
                    blockedReason,
                    requireInt(object, "queuePosition"),
                    requireLong(object, "rejectedStaleResults"),
                    requireString(object, "detail"),
                    requireNullableString(object, "sourceScheduleId")));
        }
        return tasks;
    }

    private static TaskSpec readTaskSpec(JsonObject object) {
        return new TaskSpec(
            requireString(object, "id"),
            requireString(object, "type"),
            requireString(object, "displayName"),
            requireEnum(object, "lane", TaskLane.class),
            requireStringMap(requireObject(object, "parameters"), "parameters"));
    }

    private static TaskCheckpoint readCheckpoint(JsonObject object) {
        return new TaskCheckpoint(
            requireLong(object, "revision"),
            requireStringMap(requireObject(object, "values"), "values"));
    }

    private static BlockedReason readBlockedReason(JsonObject object) {
        return new BlockedReason(
            requireEnum(object, "cause", BlockedCause.class),
            requireString(object, "detail"),
            requireString(object, "location"),
            requireInt(object, "retryCount"),
            requireString(object, "missingRequirement"),
            requireString(object, "requiredUserAction"));
    }

    private static SchedulerSnapshot readScheduler(JsonObject object) {
        JsonArray schedules = requireArray(object, "schedules");
        List<ScheduleSnapshot> decoded = new ArrayList<>(schedules.size());
        for (int index = 0; index < schedules.size(); index++) {
            decoded.add(readSchedule(requireObject(schedules.get(index), "schedules[" + index + "]")));
        }
        return new SchedulerSnapshot(
            requireLong(object, "connectedElapsedMillis"),
            requireLong(object, "lastWorldTimeTicks"),
            requireBoolean(object, "connectedAtSnapshot"),
            decoded);
    }

    private static ScheduleSnapshot readSchedule(JsonObject object) {
        return new ScheduleSnapshot(
            readScheduleRule(requireObject(object, "rule")),
            requireEnum(object, "state", ScheduleState.class),
            requireLong(object, "sequence"),
            requireLong(object, "nextConnectedDueMillis"),
            requireLong(object, "lastWorldOccurrence"),
            requireBoolean(object, "idleLatched"),
            requireNullableString(object, "lastTaskId"),
            requireLong(object, "totalRuns"),
            requireLong(object, "catchUpRuns"));
    }

    private static ScheduleRule readScheduleRule(JsonObject object) {
        String id = requireString(object, "id");
        ScheduledTaskSpec task = readScheduledTaskSpec(requireObject(object, "task"));
        ScheduleTrigger trigger = requireEnum(object, "trigger", ScheduleTrigger.class);
        long intervalMillis = requireLong(object, "intervalMillis");
        long initialDelayMillis = requireLong(object, "initialDelayMillis");
        int windowStartTick = requireInt(object, "windowStartTick");
        int windowEndTick = requireInt(object, "windowEndTick");
        Set<String> requiredConditions = requireStringSet(requireArray(object, "requiredConditions"));
        int relativeOrder = requireInt(object, "relativeOrder");
        boolean catchUp = requireBoolean(object, "catchUpAfterReconnect");
        switch (trigger) {
            case CONNECTED_INTERVAL:
                requireZero(windowStartTick, "connected interval windowStartTick");
                requireZero(windowEndTick, "connected interval windowEndTick");
                requireFalse(catchUp, "connected interval catchUpAfterReconnect");
                return ScheduleRule
                    .connectedInterval(id, task, intervalMillis, initialDelayMillis, requiredConditions, relativeOrder);
            case WORLD_TIME_WINDOW:
                requireZero(intervalMillis, "world window intervalMillis");
                requireZero(initialDelayMillis, "world window initialDelayMillis");
                return ScheduleRule.worldTimeWindow(
                    id,
                    task,
                    windowStartTick,
                    windowEndTick,
                    requiredConditions,
                    relativeOrder,
                    catchUp);
            case IDLE:
                requireZero(intervalMillis, "idle intervalMillis");
                requireZero(initialDelayMillis, "idle initialDelayMillis");
                requireZero(windowStartTick, "idle windowStartTick");
                requireZero(windowEndTick, "idle windowEndTick");
                requireFalse(catchUp, "idle catchUpAfterReconnect");
                return ScheduleRule.idle(id, task, requiredConditions, relativeOrder);
            default:
                throw new JsonParseException("unsupported schedule trigger " + trigger);
        }
    }

    private static ScheduledTaskSpec readScheduledTaskSpec(JsonObject object) {
        return new ScheduledTaskSpec(
            requireString(object, "type"),
            requireString(object, "displayName"),
            requireEnum(object, "lane", TaskLane.class),
            requireStringMap(requireObject(object, "parameters"), "parameters"));
    }

    private static Map<String, String> requireStringMap(JsonObject object, String field) {
        Map<String, String> values = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            values.put(entry.getKey(), requireString(entry.getValue(), field + "." + entry.getKey()));
        }
        return values;
    }

    private static Set<String> requireStringSet(JsonArray array) {
        Set<String> values = new LinkedHashSet<>();
        for (int index = 0; index < array.size(); index++) {
            String value = requireString(array.get(index), "requiredConditions[" + index + "]");
            if (!values.add(value)) {
                throw new JsonParseException("requiredConditions contains duplicate '" + value + "'");
            }
        }
        return values;
    }

    private static JsonObject requireObject(JsonObject parent, String field) {
        return requireObject(requireField(parent, field), field);
    }

    private static JsonObject requireObject(JsonElement element, String field) {
        if (element == null || !element.isJsonObject()) {
            throw new JsonParseException(field + " must be a JSON object");
        }
        return element.getAsJsonObject();
    }

    private static JsonArray requireArray(JsonObject parent, String field) {
        JsonElement element = requireField(parent, field);
        if (!element.isJsonArray()) {
            throw new JsonParseException(field + " must be a JSON array");
        }
        return element.getAsJsonArray();
    }

    private static String requireString(JsonObject parent, String field) {
        return requireString(requireField(parent, field), field);
    }

    private static String requireString(JsonElement element, String field) {
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive()
            .isString()) {
            throw new JsonParseException(field + " must be a JSON string");
        }
        return element.getAsString();
    }

    private static String requireNullableString(JsonObject parent, String field) {
        JsonElement element = requireField(parent, field);
        return element.isJsonNull() ? null : requireString(element, field);
    }

    private static boolean requireBoolean(JsonObject parent, String field) {
        JsonElement element = requireField(parent, field);
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive()
            .isBoolean()) {
            throw new JsonParseException(field + " must be a JSON boolean");
        }
        return element.getAsBoolean();
    }

    private static int requireInt(JsonObject parent, String field) {
        try {
            return requireNumber(parent, field).intValueExact();
        } catch (ArithmeticException invalid) {
            throw new JsonParseException(field + " must be an exact JSON integer", invalid);
        }
    }

    private static long requireLong(JsonObject parent, String field) {
        try {
            return requireNumber(parent, field).longValueExact();
        } catch (ArithmeticException invalid) {
            throw new JsonParseException(field + " must be an exact JSON integer", invalid);
        }
    }

    private static BigDecimal requireNumber(JsonObject parent, String field) {
        JsonElement element = requireField(parent, field);
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive()
            .isNumber()) {
            throw new JsonParseException(field + " must be a JSON number");
        }
        try {
            return new BigDecimal(element.getAsString());
        } catch (NumberFormatException invalid) {
            throw new JsonParseException(field + " is not a valid JSON number", invalid);
        }
    }

    private static <E extends Enum<E>> E requireEnum(JsonObject parent, String field, Class<E> type) {
        String value = requireString(parent, field);
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException invalid) {
            throw new JsonParseException(field + " has unknown value '" + value + "'", invalid);
        }
    }

    private static JsonElement requireField(JsonObject parent, String field) {
        if (!parent.has(field)) {
            throw new JsonParseException(field + " is required");
        }
        return parent.get(field);
    }

    private static void requireZero(long value, String field) {
        if (value != 0L) {
            throw new JsonParseException(field + " must be zero");
        }
    }

    private static void requireFalse(boolean value, String field) {
        if (value) {
            throw new JsonParseException(field + " must be false");
        }
    }
}
