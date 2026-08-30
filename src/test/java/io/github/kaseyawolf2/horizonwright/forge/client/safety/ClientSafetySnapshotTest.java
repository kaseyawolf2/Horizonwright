package io.github.kaseyawolf2.horizonwright.forge.client.safety;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import io.github.kaseyawolf2.horizonwright.core.safety.death.DeathContext;
import io.github.kaseyawolf2.horizonwright.core.safety.death.DimensionBlockPosition;
import io.github.kaseyawolf2.horizonwright.core.safety.death.InventoryStack;
import io.github.kaseyawolf2.horizonwright.core.safety.death.SafetyEventStamp;

public class ClientSafetySnapshotTest {

    @Test
    public void inventoryAndDeathContextAreImmutableClientThreadSnapshots() {
        final boolean[] clientThread = { true };
        ClientDeathContextPublisher publisher = new ClientDeathContextPublisher(() -> clientThread[0]);
        List<InventoryStack> liveStacks = new ArrayList<>();
        liveStacks.add(new InventoryStack("minecraft:diamond|0|none", 3, 64));
        final ClientInventorySnapshot inventory = new ClientInventorySnapshot(36, liveStacks);
        liveStacks.clear();

        ClientDeathContextSnapshot snapshot = publisher.captureAndPublish(4L, 12L, new ClientDeathContextSource() {

            @Override
            public DimensionBlockPosition getPlayerPosition() {
                return new DimensionBlockPosition(0, 10, 64, 11);
            }

            @Override
            public String getPlayerIdentity() {
                return "old-player";
            }

            @Override
            public String getActiveTaskId() {
                return "goto-home";
            }

            @Override
            public ClientInventorySnapshot getInventorySnapshot() {
                return inventory;
            }
        });

        DeathContext context = snapshot.toDeathContext();
        assertEquals(
            1,
            context.getPreDeathInventory()
                .getStacks()
                .size());
        assertEquals(
            "goto-home",
            context.getActiveTaskId()
                .orElse(null));
        assertTrue(
            publisher.latestFor(4L)
                .isPresent());
        assertFalse(
            publisher.latestFor(5L)
                .isPresent());

        clientThread[0] = false;
        try {
            publisher.captureAndPublish(4L, 13L, null);
            fail("expected client-thread enforcement");
        } catch (IllegalStateException expected) {
            assertTrue(
                expected.getMessage()
                    .contains("client thread"));
        }
    }

    @Test
    public void connectionStampSourceIsMonotonicAndCannotOutliveItsConnection() {
        ConnectionSafetyEventStampSource stamps = new ConnectionSafetyEventStampSource(8L);

        SafetyEventStamp first = stamps.next(10L);
        SafetyEventStamp second = stamps.next(10L);

        assertEquals(1L, first.getEventSequence());
        assertEquals(2L, second.getEventSequence());
        try {
            stamps.next(9L);
            fail("expected decreasing tick rejection");
        } catch (IllegalArgumentException expected) {
            assertTrue(
                expected.getMessage()
                    .contains("monotonically"));
        }

        stamps.retire();
        assertFalse(stamps.isOpen());
        final boolean[] ran = { false };
        assertFalse(stamps.runIfOpen(() -> ran[0] = true));
        assertFalse(ran[0]);
        try {
            stamps.next(11L);
            fail("expected retired connection rejection");
        } catch (IllegalStateException expected) {
            assertTrue(
                expected.getMessage()
                    .contains("retired"));
        }
    }
}
