package io.github.kaseyawolf2.horizonwright.runtime.task;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import io.github.kaseyawolf2.horizonwright.core.base.HusbandryActionKind;

public class HusbandryActionAuthorizationTest {

    @Test
    public void permitsOnlyNonDestructiveLiveActions() {
        assertTrue(HusbandryActionAuthorization.isAuthorized(HusbandryActionKind.FEED_ADULT));
        assertTrue(HusbandryActionAuthorization.isAuthorized(HusbandryActionKind.COLLECT_DROPS));
        assertFalse(HusbandryActionAuthorization.isAuthorized(HusbandryActionKind.CULL_EXCESS_ADULT));
        assertTrue(HusbandryActionAuthorization.isAuthorized(HusbandryActionKind.CULL_EXCESS_ADULT, true));
    }

    @Test
    public void cullingDiagnosticRequiresExplicitOperatorAuthorization() {
        assertTrue(
            HusbandryActionAuthorization.diagnostic(HusbandryActionKind.CULL_EXCESS_ADULT)
                .contains("explicitly authorizes"));
    }
}
