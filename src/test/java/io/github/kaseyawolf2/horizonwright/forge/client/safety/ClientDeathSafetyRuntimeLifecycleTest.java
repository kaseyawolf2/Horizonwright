package io.github.kaseyawolf2.horizonwright.forge.client.safety;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import org.junit.Test;

import io.github.kaseyawolf2.horizonwright.HorizonwrightRuntime;
import io.github.kaseyawolf2.horizonwright.core.action.ActionSessionGuard;
import io.github.kaseyawolf2.horizonwright.core.action.InMemoryActionBroker;
import io.github.kaseyawolf2.horizonwright.core.persistence.UnresolvedDeathState;
import io.github.kaseyawolf2.horizonwright.core.persistence.death.UnresolvedDeathPersistenceAdapter;
import io.github.kaseyawolf2.horizonwright.core.safety.death.DeathSafetySnapshot;
import io.github.kaseyawolf2.horizonwright.core.task.MonotonicClock;

public class ClientDeathSafetyRuntimeLifecycleTest {

    @Test
    public void tickAndDisconnectAreSerializedOnTheRuntimeLifecycleMonitor() throws Exception {
        Method tick = ClientDeathSafetyRuntime.class.getDeclaredMethod("clientTick", long.class);
        Method disconnect = ClientDeathSafetyRuntime.class.getDeclaredMethod("disconnect", long.class);

        assertTrue(Modifier.isSynchronized(tick.getModifiers()));
        assertTrue(Modifier.isSynchronized(disconnect.getModifiers()));
    }

    @Test
    public void failedDisconnectPersistenceCannotStrandTheRetiredSession() throws Exception {
        HorizonwrightRuntime runtime = newRuntime();
        FailOnSecondPersist durableState = new FailOnSecondPersist();
        ClientDeathSafetyRuntime safety = new ClientDeathSafetyRuntime(
            runtime,
            BrokerBackedDeathSafetyInterlockTest.testPolicy(),
            durableState,
            (directive, snapshot) -> {},
            () -> true,
            new BrokerBackedDeathSafetyInterlockTest.NoOpInterlockDelegate());
        safety.openFresh(BrokerBackedDeathSafetyInterlockTest.connection(1L, "first-player"));
        safety.getInboundHealthHook()
            .beforeS06HealthPacketQueued(0.0D, 20.0D, 1L);

        try {
            safety.disconnect(2L);
            fail("expected disconnect persistence failure");
        } catch (IllegalStateException expected) {
            assertTrue(
                expected.getMessage()
                    .contains("persist failed"));
        }

        assertFalse(safety.hasActiveSession());
        safety
            .restore(durableState.getPersisted(), BrokerBackedDeathSafetyInterlockTest.connection(2L, "second-player"));
        assertTrue(safety.hasActiveSession());
        safety.disconnect(3L);
        assertFalse(safety.hasActiveSession());
        runtime.close();
    }

    private static HorizonwrightRuntime newRuntime() throws Exception {
        Constructor<HorizonwrightRuntime> constructor = HorizonwrightRuntime.class
            .getDeclaredConstructor(InMemoryActionBroker.class, ActionSessionGuard.class, MonotonicClock.class);
        constructor.setAccessible(true);
        return constructor.newInstance(new InMemoryActionBroker(), new ActionSessionGuard(), new MonotonicClock() {

            @Override
            public long nowMillis() {
                return 0L;
            }
        });
    }

    private static final class FailOnSecondPersist implements DeathSafetyDurableState {

        private int persistCount;
        private UnresolvedDeathState persisted;

        @Override
        public void persistUnresolvedDeath(DeathSafetySnapshot snapshot) {
            if (++persistCount == 2) {
                throw new IllegalStateException("persist failed");
            }
            persisted = UnresolvedDeathPersistenceAdapter.captureCheckpoint(snapshot, persisted, persistCount);
        }

        @Override
        public void clearResolvedDeath() {}

        private UnresolvedDeathState getPersisted() {
            return persisted;
        }
    }
}
