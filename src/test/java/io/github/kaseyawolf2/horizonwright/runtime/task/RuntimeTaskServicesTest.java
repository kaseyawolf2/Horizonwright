package io.github.kaseyawolf2.horizonwright.runtime.task;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import io.github.kaseyawolf2.horizonwright.core.action.ActionLease;

public class RuntimeTaskServicesTest {

    @Test
    public void bindsAndIdentityUnbindsSessionAdapters() {
        MutableDryRun dryRun = new MutableDryRun();
        RuntimeTaskServices services = new RuntimeTaskServices(dryRun);
        StubUnload first = new StubUnload();
        StubUnload stale = new StubUnload();

        services.bindUnloadBackend(first);
        assertSame(first, services.getUnloadBackend());
        assertFalse(services.unbindUnloadBackend(stale));
        assertSame(first, services.getUnloadBackend());
        assertTrue(services.unbindUnloadBackend(first));
        assertNull(services.getUnloadBackend());

        dryRun.enabled = true;
        assertTrue(services.isDryRun());
    }

    @Test
    public void refusesReplacementUntilExactOwnerUnbinds() {
        RuntimeTaskServices services = new RuntimeTaskServices(() -> false);
        StubUnload first = new StubUnload();
        services.bindUnloadBackend(first);
        services.bindUnloadBackend(first);
        try {
            services.bindUnloadBackend(new StubUnload());
            fail("replacement should require retirement of the current owner");
        } catch (IllegalStateException expected) {
            assertSame(first, services.getUnloadBackend());
        }
    }

    private static final class MutableDryRun implements RuntimeTaskServices.DryRunSource {

        private boolean enabled;

        @Override
        public boolean isDryRun() {
            return enabled;
        }
    }

    private static final class StubUnload implements UnloadBackend {

        @Override
        public UnloadBackendAvailability availability() {
            return UnloadBackendAvailability.available("test");
        }

        @Override
        public UnloadObservationResult observe(UnloadObservationRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public UnloadActionHandle execute(UnloadActionRequest request, ActionLease lease) {
            throw new UnsupportedOperationException();
        }
    }
}
