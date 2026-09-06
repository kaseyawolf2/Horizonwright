package io.github.kaseyawolf2.horizonwright.navigation.baritone;

/** Holds the original preference across a navigation session and its cleanup retries. */
final class NavigationInventorySuppression {

    private Boolean previous;

    boolean begin(boolean current) {
        if (previous == null) previous = current;
        return false;
    }

    boolean end(boolean current) {
        if (previous == null) return current;
        boolean restored = current || previous;
        previous = null;
        return restored;
    }
}
