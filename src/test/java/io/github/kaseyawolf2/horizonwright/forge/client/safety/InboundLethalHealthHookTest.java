package io.github.kaseyawolf2.horizonwright.forge.client.safety;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

import io.github.kaseyawolf2.horizonwright.core.action.InMemoryActionBroker;
import io.github.kaseyawolf2.horizonwright.core.safety.death.DeathLatchRecord;
import io.github.kaseyawolf2.horizonwright.core.safety.death.DeathSafetyController;
import io.github.kaseyawolf2.horizonwright.core.safety.death.DeathSafetyDirective;
import io.github.kaseyawolf2.horizonwright.core.safety.death.DeathSafetySnapshot;
import io.github.kaseyawolf2.horizonwright.core.safety.death.DeathSafetyState;
import io.github.kaseyawolf2.horizonwright.core.safety.death.DimensionBlockPosition;
import io.github.kaseyawolf2.horizonwright.core.safety.death.ManualHoldReason;

public class InboundLethalHealthHookTest {

    @Test
    public void lethalHealthLatchesBeforePersistenceAndBeforeCallerCanQueueS06() {
        final List<String> order = new ArrayList<>();
        final InMemoryActionBroker broker = new InMemoryActionBroker();
        final AutomationInputGate inputGate = new AutomationInputGate();
        ClientDeathInterlockDelegate delegate = new BrokerBackedDeathSafetyInterlockTest.NoOpInterlockDelegate() {

            @Override
            public void performSynchronousEmergencyStop(DeathLatchRecord record) {
                assertTrue(broker.isSafetyLocked());
                assertTrue(inputGate.blocksAllAutomationInputOwners());
                order.add("synchronous-lockdown");
            }

            @Override
            public void scheduleClientThreadCleanup(DeathLatchRecord record) {
                order.add("client-cleanup-scheduled");
            }
        };
        BrokerBackedDeathSafetyInterlock interlock = new BrokerBackedDeathSafetyInterlock(broker, inputGate, delegate);
        DeathSafetyController controller = new DeathSafetyController(
            BrokerBackedDeathSafetyInterlockTest.testPolicy(),
            interlock,
            BrokerBackedDeathSafetyInterlockTest.connection(4L, "old-player"));
        ClientDeathContextPublisher publisher = new ClientDeathContextPublisher(() -> true);
        publisher.captureAndPublish(4L, 3L, source());
        DeathSafetyDirectiveProcessor processor = new DeathSafetyDirectiveProcessor(new DeathSafetyDurableState() {

            @Override
            public void persistUnresolvedDeath(DeathSafetySnapshot snapshot) {
                assertTrue(broker.isSafetyLocked());
                assertTrue(inputGate.blocksAllAutomationInputOwners());
                order.add("persist");
            }

            @Override
            public void clearResolvedDeath() {
                order.add("clear");
            }
        });
        InboundLethalHealthHook hook = new InboundLethalHealthHook(
            controller,
            new ConnectionSafetyEventStampSource(4L),
            publisher,
            processor,
            (directive, snapshot) -> {});

        assertEquals(
            DeathSafetyState.DEATH_LATCHED,
            hook.beforeS06HealthPacketQueued(0.0D, 20.0D, 3L)
                .getSnapshot()
                .getState());
        order.add("vanilla-queues-s06");

        assertEquals(
            java.util.Arrays
                .asList("synchronous-lockdown", "client-cleanup-scheduled", "persist", "vanilla-queues-s06"),
            order);
    }

    @Test
    public void missingBaselinePersistsManualHoldAndReturnsToTheInboundPacketPath() {
        final List<String> order = new ArrayList<>();
        final InMemoryActionBroker broker = new InMemoryActionBroker();
        final AutomationInputGate inputGate = new AutomationInputGate();
        BrokerBackedDeathSafetyInterlock interlock = new BrokerBackedDeathSafetyInterlock(
            broker,
            inputGate,
            new BrokerBackedDeathSafetyInterlockTest.NoOpInterlockDelegate() {

                @Override
                public void performSynchronousEmergencyStop(DeathLatchRecord record) {
                    order.add("synchronous-lockdown");
                }
            });
        DeathSafetyController controller = new DeathSafetyController(
            BrokerBackedDeathSafetyInterlockTest.testPolicy(),
            interlock,
            BrokerBackedDeathSafetyInterlockTest.connection(9L, "old-player"));
        DeathSafetyDirectiveProcessor processor = new DeathSafetyDirectiveProcessor(new DeathSafetyDurableState() {

            @Override
            public void persistUnresolvedDeath(DeathSafetySnapshot snapshot) {
                order.add("persist-manual-hold");
            }

            @Override
            public void clearResolvedDeath() {
                order.add("clear");
            }
        });
        InboundLethalHealthHook hook = new InboundLethalHealthHook(
            controller,
            new ConnectionSafetyEventStampSource(9L),
            new ClientDeathContextPublisher(() -> true),
            processor,
            (directive, snapshot) -> {
                if (directive == DeathSafetyDirective.ENTER_MANUAL_HOLD) {
                    order.add("manual-hold-effect");
                }
            });

        DeathSafetySnapshot snapshot = hook.beforeS06HealthPacketQueued(0.0D, 20.0D, 4L)
            .getSnapshot();
        order.add("vanilla-queues-s06");

        assertEquals(DeathSafetyState.MANUAL_HOLD, snapshot.getState());
        assertEquals(
            ManualHoldReason.PRE_DEATH_CONTEXT_UNAVAILABLE,
            snapshot.getManualHoldReason()
                .orElse(null));
        assertTrue(
            snapshot.getUnresolvedDeathProjection()
                .isPresent());
        assertTrue(broker.isSafetyLocked());
        assertTrue(inputGate.blocksAllAutomationInputOwners());
        assertEquals(
            java.util.Arrays
                .asList("synchronous-lockdown", "persist-manual-hold", "manual-hold-effect", "vanilla-queues-s06"),
            order);
    }

    private static ClientDeathContextSource source() {
        return new ClientDeathContextSource() {

            @Override
            public DimensionBlockPosition getPlayerPosition() {
                return new DimensionBlockPosition(0, 10, 64, 10);
            }

            @Override
            public String getPlayerIdentity() {
                return "old-player";
            }

            @Override
            public String getActiveTaskId() {
                return "task";
            }

            @Override
            public ClientInventorySnapshot getInventorySnapshot() {
                return new ClientInventorySnapshot(36, Collections.emptyList());
            }
        };
    }
}
