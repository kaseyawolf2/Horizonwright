package io.github.kaseyawolf2.horizonwright.forge.client.safety;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import io.github.kaseyawolf2.horizonwright.core.safety.death.DeathSafetyController;
import io.github.kaseyawolf2.horizonwright.core.safety.death.DeathSafetyDirective;
import io.github.kaseyawolf2.horizonwright.core.safety.death.DeathSafetySnapshot;
import io.github.kaseyawolf2.horizonwright.core.safety.death.DeathSafetyUpdate;
import io.github.kaseyawolf2.horizonwright.core.safety.death.DeathSignal;
import io.github.kaseyawolf2.horizonwright.core.safety.death.SafetyEventStamp;

public class DeathSafetyDirectiveProcessorTest {

    @Test
    public void firstLatchPersistsBeforeExecutingEveryOrderedSafetyEffect() {
        final List<String> order = new ArrayList<>();
        DeathSafetyDirectiveProcessor processor = processor(order);

        processor.process(firstLatchUpdate(), (directive, snapshot) -> order.add(directive.name()));

        assertEquals(
            Arrays.asList(
                "PERSIST",
                "FORCE_CHECKPOINT_ACTIVE_TASK",
                "CANCEL_ALL_NAVIGATION_AND_PENDING_WORK",
                "REVOKE_ALL_ACTION_LEASES",
                "CLEAR_ALL_INPUT_AND_KEYBINDINGS",
                "CLEAR_NAVIGATION_PRIVATE_INPUT",
                "RELEASE_ALL_HELD_USE",
                "INVALIDATE_ACTION_AND_CONTAINER_EPOCHS",
                "ENGAGE_DEATH_LOCKDOWN"),
            order);
    }

    @Test
    public void effectFailureIsVisibleAndDoesNotPretendLaterFirstLatchWorkCompleted() {
        final List<String> order = new ArrayList<>();
        DeathSafetyDirectiveProcessor processor = processor(order);

        try {
            processor.process(firstLatchUpdate(), (directive, snapshot) -> {
                order.add(directive.name());
                if (directive == DeathSafetyDirective.CANCEL_ALL_NAVIGATION_AND_PENDING_WORK) {
                    throw new IllegalStateException("cancel failed");
                }
            });
            fail("expected explicit first-latch effect failure");
        } catch (IllegalStateException expected) {
            assertEquals("cancel failed", expected.getMessage());
        }

        assertEquals(
            Arrays.asList("PERSIST", "FORCE_CHECKPOINT_ACTIVE_TASK", "CANCEL_ALL_NAVIGATION_AND_PENDING_WORK"),
            order);
        assertFalse(order.contains(DeathSafetyDirective.RELEASE_ALL_HELD_USE.name()));
        assertFalse(order.contains(DeathSafetyDirective.INVALIDATE_ACTION_AND_CONTAINER_EPOCHS.name()));
    }

    private static DeathSafetyUpdate firstLatchUpdate() {
        DeathSafetyController controller = new DeathSafetyController(
            BrokerBackedDeathSafetyInterlockTest.testPolicy(),
            new PacketWriteGateTest.NoOpInterlock(),
            BrokerBackedDeathSafetyInterlockTest.connection(3L, "old-player"));
        DeathSafetyUpdate update = controller.onDeathSignal(
            new SafetyEventStamp(3L, 1L, 1L),
            DeathSignal.LOCAL_DEATH_CALLBACK,
            BrokerBackedDeathSafetyInterlockTest.deathContext());
        assertTrue(update.hasDirective(DeathSafetyDirective.PERSIST_UNRESOLVED_DEATH));
        return update;
    }

    private static DeathSafetyDirectiveProcessor processor(final List<String> order) {
        return new DeathSafetyDirectiveProcessor(new DeathSafetyDurableState() {

            @Override
            public void persistUnresolvedDeath(DeathSafetySnapshot snapshot) {
                order.add("PERSIST");
            }

            @Override
            public void clearResolvedDeath() {
                order.add("CLEAR");
            }
        });
    }
}
