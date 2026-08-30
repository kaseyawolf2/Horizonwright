package io.github.kaseyawolf2.horizonwright.navigation.baritone;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import baritone.api.BaritoneAPI;
import baritone.api.Settings;
import baritone.api.utils.SettingsUtil;

/** Searchable, validated UI boundary over Baritone's own live settings registry. */
public final class BaritoneSettingsCatalog {

    private final Settings settings;
    private final Runnable persist;

    public static BaritoneSettingsCatalog installed() {
        Settings installed = BaritoneAPI.getSettings();
        return new BaritoneSettingsCatalog(installed, () -> SettingsUtil.save(installed));
    }

    BaritoneSettingsCatalog(Settings settings, Runnable persist) {
        if (settings == null || persist == null) {
            throw new IllegalArgumentException("settings and persist are required");
        }
        this.settings = settings;
        this.persist = persist;
    }

    public List<Entry> search(String query) {
        String needle = query == null ? ""
            : query.trim()
                .toLowerCase(Locale.ROOT);
        List<Entry> matches = new ArrayList<>();
        for (Settings.Setting<?> setting : settings.allSettings) {
            Entry entry = snapshot(setting);
            if (needle.isEmpty() || entry.getName()
                .toLowerCase(Locale.ROOT)
                .contains(needle)
                || entry.getType()
                    .toLowerCase(Locale.ROOT)
                    .contains(needle)
                || entry.getCurrentValue()
                    .toLowerCase(Locale.ROOT)
                    .contains(needle)) {
                matches.add(entry);
            }
        }
        Collections.sort(matches, Comparator.comparing(Entry::getName, String.CASE_INSENSITIVE_ORDER));
        return Collections.unmodifiableList(matches);
    }

    public Entry apply(String name, String rawValue) {
        Settings.Setting<?> setting = requireEditable(name);
        String candidate = rawValue == null ? "" : rawValue.trim();
        if (setting.getValueClass() == Boolean.class
            && !("true".equalsIgnoreCase(candidate) || "false".equalsIgnoreCase(candidate))) {
            throw new IllegalArgumentException("Boolean settings accept only true or false");
        }
        SettingsUtil.parseAndApply(
            settings,
            setting.getName()
                .toLowerCase(Locale.ROOT),
            candidate);
        persist.run();
        return snapshot(setting);
    }

    public Entry reset(String name) {
        Settings.Setting<?> setting = requireEditable(name);
        setting.reset();
        persist.run();
        return snapshot(setting);
    }

    public Entry toggle(String name) {
        Settings.Setting<?> setting = requireEditable(name);
        if (setting.getValueClass() != Boolean.class) {
            throw new IllegalArgumentException(setting.getName() + " is not a Boolean setting");
        }
        return apply(setting.getName(), Boolean.toString(!((Boolean) setting.value)));
    }

    private Settings.Setting<?> requireEditable(String name) {
        if (name == null) {
            throw new IllegalArgumentException("setting name is required");
        }
        Settings.Setting<?> setting = settings.byLowerName.get(
            name.trim()
                .toLowerCase(Locale.ROOT));
        if (setting == null) {
            throw new IllegalArgumentException("Unknown Baritone setting: " + name);
        }
        if (setting.isJavaOnly()) {
            throw new IllegalStateException(setting.getName() + " is a Java-only setting");
        }
        return setting;
    }

    private static Entry snapshot(Settings.Setting<?> setting) {
        String current;
        String defaultValue;
        try {
            current = SettingsUtil.settingValueToString(setting);
            defaultValue = SettingsUtil.settingDefaultToString(setting);
        } catch (RuntimeException failure) {
            current = String.valueOf(setting.value);
            defaultValue = String.valueOf(setting.defaultValue);
        }
        return new Entry(
            setting.getName(),
            SettingsUtil.settingTypeToString(setting),
            current,
            defaultValue,
            setting.isJavaOnly(),
            setting.getValueClass() == Boolean.class);
    }

    public static final class Entry {

        private final String name;
        private final String type;
        private final String currentValue;
        private final String defaultValue;
        private final boolean javaOnly;
        private final boolean booleanValue;

        private Entry(String name, String type, String currentValue, String defaultValue, boolean javaOnly,
            boolean booleanValue) {
            this.name = name;
            this.type = type;
            this.currentValue = currentValue;
            this.defaultValue = defaultValue;
            this.javaOnly = javaOnly;
            this.booleanValue = booleanValue;
        }

        public String getName() {
            return name;
        }

        public String getType() {
            return type;
        }

        public String getCurrentValue() {
            return currentValue;
        }

        public String getDefaultValue() {
            return defaultValue;
        }

        public boolean isJavaOnly() {
            return javaOnly;
        }

        public boolean isBooleanValue() {
            return booleanValue;
        }
    }
}
