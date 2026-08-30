package io.github.kaseyawolf2.horizonwright.forge.client.network;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;

import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;

import org.junit.Test;

import io.github.kaseyawolf2.horizonwright.forge.client.network.DeathSafetyPacketBridge.GraveActivationWriteDecision;

public class RetirementAwareDeathSafetyPacketBridgeTest {

    @Test
    public void activeBoundaryDelegatesEveryIntegratedDecision() {
        RecordingBridge delegate = new RecordingBridge();
        RetirementAwareDeathSafetyPacketBridge bridge = new RetirementAwareDeathSafetyPacketBridge(
            delegate,
            () -> true);
        AtomicBoolean written = new AtomicBoolean();

        bridge.beforeLethalHealthPacket(0.0D);
        assertFalse(bridge.tryAuthorizeRespawnPacket(() -> written.set(true)));
        assertEquals(
            GraveActivationWriteDecision.REJECTED,
            bridge.tryAuthorizeGraveActivationPacket(new C08PacketPlayerBlockPlacement(), () -> written.set(true)));

        assertEquals(1, delegate.healthCount);
        assertEquals(1, delegate.respawnCount);
        assertEquals(1, delegate.graveCount);
        assertFalse(written.get());
    }

    @Test
    public void retiredBoundaryPassesIntegratedTrafficWithoutCallingOldAuthority() {
        RecordingBridge delegate = new RecordingBridge();
        RetirementAwareDeathSafetyPacketBridge bridge = new RetirementAwareDeathSafetyPacketBridge(
            delegate,
            () -> false);
        AtomicBoolean written = new AtomicBoolean();

        bridge.beforeLethalHealthPacket(0.0D);
        assertTrue(bridge.tryAuthorizeRespawnPacket(() -> written.set(true)));
        assertEquals(
            GraveActivationWriteDecision.NOT_APPLICABLE,
            bridge.tryAuthorizeGraveActivationPacket(new C08PacketPlayerBlockPlacement(), () -> written.set(true)));

        assertEquals(0, delegate.healthCount);
        assertEquals(0, delegate.respawnCount);
        assertEquals(0, delegate.graveCount);
        assertTrue(written.get());
    }

    @Test
    public void lifecycleNotificationAlwaysReachesDelegate() {
        RecordingBridge delegate = new RecordingBridge();
        RetirementAwareDeathSafetyPacketBridge bridge = new RetirementAwareDeathSafetyPacketBridge(
            delegate,
            () -> false);

        bridge.onBoundaryUnavailable(true);

        assertTrue(delegate.transportClosed);
    }

    private static final class RecordingBridge implements DeathSafetyPacketBridge {

        private int healthCount;
        private int respawnCount;
        private int graveCount;
        private boolean transportClosed;

        @Override
        public void beforeLethalHealthPacket(double health) {
            healthCount++;
        }

        @Override
        public boolean tryAuthorizeRespawnPacket(Runnable finalWriteContinuation) {
            respawnCount++;
            return false;
        }

        @Override
        public GraveActivationWriteDecision tryAuthorizeGraveActivationPacket(C08PacketPlayerBlockPlacement packet,
            Runnable finalWriteContinuation) {
            graveCount++;
            return GraveActivationWriteDecision.REJECTED;
        }

        @Override
        public void onBoundaryUnavailable(boolean closedTransport) {
            transportClosed = closedTransport;
        }
    }
}
