package io.github.kaseyawolf2.horizonwright.testfixtures;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class FixtureTable {

    private FixtureTable() {}

    public static List<Row> load(String resourcePath) {
        InputStream stream = FixtureTable.class.getResourceAsStream(resourcePath);
        if (stream == null) {
            throw new IllegalArgumentException("fixture resource not found: " + resourcePath);
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            List<String> dataLines = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                    dataLines.add(line);
                }
            }
            if (dataLines.isEmpty()) {
                throw new IllegalArgumentException("fixture resource has no table: " + resourcePath);
            }
            String[] headers = split(dataLines.get(0));
            List<Row> rows = new ArrayList<>();
            for (int lineIndex = 1; lineIndex < dataLines.size(); lineIndex++) {
                String[] values = split(dataLines.get(lineIndex));
                if (values.length != headers.length) {
                    throw new IllegalArgumentException(
                        resourcePath + " row "
                            + (lineIndex + 1)
                            + " has "
                            + values.length
                            + " values, expected "
                            + headers.length);
                }
                Map<String, String> columns = new LinkedHashMap<>();
                for (int column = 0; column < headers.length; column++) {
                    columns.put(headers[column], values[column]);
                }
                rows.add(new Row(columns));
            }
            return Collections.unmodifiableList(rows);
        } catch (IOException exception) {
            throw new IllegalStateException("failed to read fixture resource " + resourcePath, exception);
        }
    }

    private static String[] split(String line) {
        return line.split("\\t", -1);
    }

    public static final class Row {

        private final Map<String, String> columns;

        private Row(Map<String, String> columns) {
            this.columns = Collections.unmodifiableMap(new LinkedHashMap<>(columns));
        }

        public String get(String column) {
            String value = columns.get(column);
            if (value == null) {
                throw new IllegalArgumentException("unknown fixture column " + column);
            }
            return value;
        }

        public int getInt(String column) {
            return Integer.parseInt(get(column));
        }

        public long getLong(String column) {
            return Long.parseLong(get(column));
        }

        public boolean getBoolean(String column) {
            String value = get(column);
            if (!"true".equals(value) && !"false".equals(value)) {
                throw new IllegalArgumentException(column + " is not a lowercase boolean: " + value);
            }
            return Boolean.parseBoolean(value);
        }
    }
}
