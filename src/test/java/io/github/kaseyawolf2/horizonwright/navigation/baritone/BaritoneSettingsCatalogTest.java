package io.github.kaseyawolf2.horizonwright.navigation.baritone;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

import baritone.api.Settings;

public class BaritoneSettingsCatalogTest {

    @Test
    public void exposesSearchesValidatesAndPersistsInstalledSettings() {
        Settings settings = newSettings();
        Boolean original = settings.allowSprint.value;
        AtomicInteger saves = new AtomicInteger();
        BaritoneSettingsCatalog catalog = new BaritoneSettingsCatalog(settings, saves::incrementAndGet);
        try {
            List<BaritoneSettingsCatalog.Entry> matches = catalog.search("allowSprint");
            assertEquals(1, matches.size());
            assertEquals(
                "allowSprint",
                matches.get(0)
                    .getName());
            assertTrue(
                matches.get(0)
                    .isBooleanValue());

            BaritoneSettingsCatalog.Entry changed = catalog.apply("allowSprint", "false");
            assertEquals("false", changed.getCurrentValue());
            assertFalse(settings.allowSprint.value);
            assertEquals(1, saves.get());

            changed = catalog.toggle("allowSprint");
            assertEquals("true", changed.getCurrentValue());
            assertTrue(settings.allowSprint.value);
            assertEquals(2, saves.get());

            try {
                catalog.apply("allowSprint", "not-a-boolean");
                fail("invalid Boolean text must be rejected");
            } catch (IllegalArgumentException expected) {
                assertTrue(
                    expected.getMessage()
                        .contains("true or false"));
            }
            assertEquals(2, saves.get());
        } finally {
            settings.allowSprint.value = original;
        }
    }

    @Test
    public void resetUsesBaritonesDeclaredDefault() {
        Settings settings = newSettings();
        Integer original = settings.rightClickSpeed.value;
        AtomicInteger saves = new AtomicInteger();
        BaritoneSettingsCatalog catalog = new BaritoneSettingsCatalog(settings, saves::incrementAndGet);
        try {
            catalog.apply("rightClickSpeed", "9");
            assertEquals(Integer.valueOf(9), settings.rightClickSpeed.value);
            BaritoneSettingsCatalog.Entry reset = catalog.reset("rightClickSpeed");
            assertEquals(settings.rightClickSpeed.defaultValue.toString(), reset.getCurrentValue());
            assertEquals(settings.rightClickSpeed.defaultValue, settings.rightClickSpeed.value);
            assertEquals(2, saves.get());
        } finally {
            settings.rightClickSpeed.value = original;
        }
    }

    private static Settings newSettings() {
        try {
            java.lang.reflect.Constructor<Settings> constructor = Settings.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("could not create isolated Baritone settings", failure);
        }
    }
}
