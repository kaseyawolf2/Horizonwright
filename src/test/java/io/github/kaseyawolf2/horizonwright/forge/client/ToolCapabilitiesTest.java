package io.github.kaseyawolf2.horizonwright.forge.client;

import static org.junit.Assert.*;

import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

public class ToolCapabilitiesTest {

    @Test
    public void installedHatchetAndLumberAxeAreBothAxesWithoutForgeRegistration() {
        for (String name : new String[] { "Hatchet", "LumberAxe" }) {
            Set<String> classes = new HashSet<>();
            ToolCapabilities.addKnownClasses(classes, "tconstruct.items.tools." + name);
            assertTrue(classes.contains("axe"));
        }
    }

    @Test
    public void unrelatedNamesAndToolPartsDoNotBecomeAxes() {
        Set<String> classes = new HashSet<>();
        ToolCapabilities.addKnownClasses(classes, "another.mod.Hatchet");
        ToolCapabilities.addKnownClasses(classes, "tconstruct.items.tools.HatchetHead");
        assertTrue(classes.isEmpty());
    }
}
