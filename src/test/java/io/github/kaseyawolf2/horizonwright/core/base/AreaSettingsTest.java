package io.github.kaseyawolf2.horizonwright.core.base;

import static org.junit.Assert.*;

import org.junit.Test;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

public class AreaSettingsTest {

    private NamedArea area() {
        return new NamedArea("ore", "Ore", new BasePosition(0, 10, 20, 30), new BasePosition(0, 20, 40, 50));
    }

    @Test
    public void oldAreasWithoutSettingsUseDefaultChestAndUnassignedType() {
        Gson gson = new Gson();
        JsonObject json = gson.toJsonTree(area())
            .getAsJsonObject();
        json.remove("kind");
        json.remove("storageId");
        NamedArea restored = gson.fromJson(json, NamedArea.class);
        assertEquals(AreaKind.UNASSIGNED, restored.getKind());
        assertEquals("default-chest", restored.resolvedStorageId());
    }

    @Test
    public void areaTypeAndChestSurviveSerialization() {
        NamedArea value = area().withSettings(AreaKind.QUARRY, "ore-chest");
        Gson gson = new Gson();
        NamedArea restored = gson.fromJson(gson.toJson(value), NamedArea.class);
        assertEquals(value, restored);
        assertEquals("ore-chest", restored.resolvedStorageId());
    }

    @Test
    public void changingSettingsPreservesIdentityAndBounds() {
        NamedArea original = area();
        NamedArea updated = original.withSettings(AreaKind.FARM, "crop-chest");
        assertEquals(original.getId(), updated.getId());
        assertEquals(original.getMinimum(), updated.getMinimum());
        assertEquals(original.getMaximum(), updated.getMaximum());
        assertNotEquals(original, updated);
    }

    @Test
    public void clearingOverrideReturnsToDefault() {
        assertEquals(
            "default-chest",
            area().withSettings(AreaKind.FARM, "crop-chest")
                .withSettings(AreaKind.FARM, " ")
                .resolvedStorageId());
    }
}
