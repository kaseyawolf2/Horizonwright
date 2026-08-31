package io.github.kaseyawolf2.horizonwright.forge.client;

/** Minecraft-independent validation for the guided profile asset form. */
final class ProfileAssetInput {

    private ProfileAssetInput() {}

    static int inventorySlot(String text, String field) {
        int value = integer(text, field);
        if (value < 0 || value > 35) throw new IllegalArgumentException(field + " must be from 0 to 35");
        return value;
    }

    static int positiveInteger(String text, String field) {
        int value = integer(text, field);
        if (value < 1) throw new IllegalArgumentException(field + " must be at least 1");
        return value;
    }

    static int nonNegativeInteger(String text, String field) {
        int value = integer(text, field);
        if (value < 0) throw new IllegalArgumentException(field + " must not be negative");
        return value;
    }

    static String stableId(String value, String field) {
        if (value == null || !value.trim()
            .matches("[A-Za-z0-9][A-Za-z0-9._-]{0,47}")) {
            throw new IllegalArgumentException(field + " must use letters, numbers, dot, dash, or underscore");
        }
        return value.trim();
    }

    private static int integer(String text, String field) {
        try {
            return Integer.parseInt(text.trim());
        } catch (RuntimeException failure) {
            throw new IllegalArgumentException(field + " must be a whole number", failure);
        }
    }
}
