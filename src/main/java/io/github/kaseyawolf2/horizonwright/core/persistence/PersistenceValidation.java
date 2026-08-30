package io.github.kaseyawolf2.horizonwright.core.persistence;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

final class PersistenceValidation {

    private static final Pattern STABLE_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,127}");

    private PersistenceValidation() {}

    static String requireText(String value, String field) {
        if (value == null || value.trim()
            .isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    static String requireStableId(String value, String field) {
        String normalized = requireText(value, field);
        if (!STABLE_ID.matcher(normalized)
            .matches()) {
            throw new IllegalArgumentException(
                field + " must start with an alphanumeric character and contain only alphanumerics, '.', '_', or '-'");
        }
        return normalized;
    }

    static String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    static void requireNonNegative(long value, String field) {
        if (value < 0L) {
            throw new IllegalArgumentException(field + " must not be negative");
        }
    }

    static <T> List<T> requireList(List<T> values, String field) {
        if (values == null) {
            throw new IllegalArgumentException(field + " must not be null");
        }
        for (int index = 0; index < values.size(); index++) {
            if (values.get(index) == null) {
                throw new IllegalArgumentException(field + " must not contain null at index " + index);
            }
        }
        return values;
    }

    static void requireUniqueIds(List<? extends IdentifiedPersistenceValue> values, String field) {
        Set<String> ids = new HashSet<>();
        for (IdentifiedPersistenceValue value : requireList(values, field)) {
            if (!ids.add(value.getId())) {
                throw new IllegalArgumentException(field + " contains duplicate id '" + value.getId() + "'");
            }
        }
    }

    interface IdentifiedPersistenceValue {

        String getId();
    }
}
