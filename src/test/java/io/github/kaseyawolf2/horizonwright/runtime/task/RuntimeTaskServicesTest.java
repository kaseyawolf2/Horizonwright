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
        StubExcavation excavation = new StubExcavation();
        services.bindExcavationBackend(excavation);
        assertSame(excavation, services.getExcavationBackend());
        assertFalse(services.unbindExcavationBackend(new StubExcavation()));
        assertTrue(services.unbindExcavationBackend(excavation));
        assertNull(services.getExcavationBackend());
        StubUnload first = new StubUnload();
        StubUnload stale = new StubUnload();

        services.bindUnloadBackend(first);
        assertSame(first, services.getUnloadBackend());
        assertFalse(services.unbindUnloadBackend(stale));
        assertSame(first, services.getUnloadBackend());
        assertTrue(services.unbindUnloadBackend(first));
        assertNull(services.getUnloadBackend());

        StubRepair repair = new StubRepair();
        services.bindRepairBackend(repair);
        assertSame(repair, services.getRepairBackend());
        assertFalse(services.unbindRepairBackend(new StubRepair()));
        assertTrue(services.unbindRepairBackend(repair));
        assertNull(services.getRepairBackend());

        StubFarm farm = new StubFarm();
        services.bindFarmBackend(farm);
        assertSame(farm, services.getFarmBackend());
        assertFalse(services.unbindFarmBackend(new StubFarm()));
        assertTrue(services.unbindFarmBackend(farm));
        assertNull(services.getFarmBackend());

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

    private static final class StubExcavation implements ExcavationBackend {

        @Override
        public ExcavationBackendAvailability availability() {
            return ExcavationBackendAvailability.available("test");
        }

        @Override
        public ExcavationObservationResult observe(ExcavationObservationRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ExcavationActionHandle execute(ExcavationActionRequest request, ActionLease lease) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class StubRepair implements RepairBackend {

        @Override
        public RepairBackendAvailability availability() {
            return RepairBackendAvailability.available("test");
        }

        @Override
        public RepairObservationResult observe(RepairObservationRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public RepairActionHandle execute(RepairActionRequest request, ActionLease lease) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class StubFarm implements FarmBackend {

        @Override
        public Availability availability() {
            return Availability.available("test");
        }

        @Override
        public PassSnapshot scan(ScanRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TargetSnapshot observe(TargetRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ActionHandle execute(ActionRequest request, ActionLease lease) {
            throw new UnsupportedOperationException();
        }
    }
}
