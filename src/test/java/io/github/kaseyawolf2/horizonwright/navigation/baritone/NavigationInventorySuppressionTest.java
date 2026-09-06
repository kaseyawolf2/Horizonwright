package io.github.kaseyawolf2.horizonwright.navigation.baritone;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class NavigationInventorySuppressionTest {

    @Test
    public void preferenceRemainsDisabledUntilCleanupAndRestoresOnce() {
        NavigationInventorySuppression scope = new NavigationInventorySuppression();
        assertFalse(scope.begin(true));
        assertFalse(scope.begin(false));
        assertTrue(scope.end(false));
        assertFalse(scope.end(false));
    }

    @Test
    public void disabledPreferenceAndExplicitEnableArePreserved() {
        NavigationInventorySuppression scope = new NavigationInventorySuppression();
        assertFalse(scope.begin(false));
        assertFalse(scope.end(false));
        scope.begin(false);
        assertTrue(scope.end(true));
    }
}
