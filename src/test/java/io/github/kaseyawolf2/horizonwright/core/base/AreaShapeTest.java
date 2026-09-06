package io.github.kaseyawolf2.horizonwright.core.base;

import static org.junit.Assert.*;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import com.google.gson.Gson;

public class AreaShapeTest {

    @Test
    public void circleUsesHorizontalRadiusAndIndependentInclusiveHeight() {
        NamedArea area = NamedArea.circle("a", "A", new BasePosition(0, -10, 80, -20), 5, 60, 70);
        assertTrue(area.contains(new BasePosition(0, -7, 60, -16)));
        assertTrue(area.contains(new BasePosition(0, -15, 70, -20)));
        assertFalse(area.contains(new BasePosition(0, -5, 65, -15)));
        assertFalse(area.contains(new BasePosition(0, -10, 71, -20)));
        assertFalse(area.contains(new BasePosition(1, -10, 65, -20)));
        assertEquals(
            80,
            area.getCenter()
                .getY());
    }

    @Test
    public void settingsJsonAndCheckpointKeepCircle() {
        NamedArea area = NamedArea.circle("a", "A", new BasePosition(0, -10, 80, -20), 5, 60, 70)
            .withSettings(AreaKind.FARM, "chest");
        Gson gson = new Gson();
        assertEquals(area, gson.fromJson(gson.toJson(area), NamedArea.class));
        Map<String, String> values = new HashMap<>();
        AreaShapeCheckpoint.write(values, "shape.", area);
        NamedArea box = new NamedArea("a", "A", area.getMinimum(), area.getMaximum(), AreaKind.FARM, "chest");
        assertEquals(area, AreaShapeCheckpoint.read(values, "shape.", box));
        assertEquals(box, AreaShapeCheckpoint.read(new HashMap<String, String>(), "shape.", box));
    }

    @Test
    public void edgeTreesMayExtendOutsideButMustBeRootedInside() {
        NamedArea area = new NamedArea("a", "A", new BasePosition(0, 0, 60, 0), new BasePosition(0, 5, 70, 5));
        BasePosition edge = new BasePosition(0, 5, 60, 5);
        assertTrue(TreeHarvestBoundary.rootSelected(area, edge, "oak"));
        assertTrue(TreeHarvestBoundary.logWithinReach(edge, new BasePosition(0, 12, 90, 12)));
        assertFalse(TreeHarvestBoundary.logWithinReach(edge, new BasePosition(0, 38, 90, 5)));
        assertFalse(TreeHarvestBoundary.rootSelected(area, new BasePosition(0, -1, 60, -1), "oak"));
        assertTrue(TreeHarvestBoundary.rootSelected(area, new BasePosition(0, -1, 60, -1), "jungle|2x2"));
    }
}
