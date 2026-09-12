package io.github.kaseyawolf2.horizonwright.forge.client.network;

import static org.junit.Assert.*;

import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.server.S22PacketMultiBlockChange;

import org.junit.Test;

import io.github.kaseyawolf2.horizonwright.core.excavation.BlockPosition;
import io.netty.buffer.Unpooled;

public class ServerBlockConfirmationTest {

    @Test
    public void unrelatedChunkSectionDoesNotErasePendingServerConfirmation() {
        Object connection = new Object();
        try (ServerBlockConfirmation watch = ServerBlockConfirmation
            .watch(connection, new BlockPosition(1, 75, 2), true)) {
            ServerBlockConfirmation.record(connection, 1, 75, 2, true);
            ServerBlockConfirmation.observe(connection, new net.minecraft.network.play.server.S21PacketChunkData() {

                @Override
                public int func_149273_e() {
                    return 0;
                }

                @Override
                public int func_149271_f() {
                    return 0;
                }

                @Override
                public int func_149276_g() {
                    return 1;
                }

                @Override
                public boolean func_149274_i() {
                    return false;
                }
            });
            assertTrue(watch.confirmedAir());
        }
    }

    @Test
    public void inboundFirewallDeliversExactServerBlockEvidenceWithoutConsumingPacket() {
        io.netty.channel.embedded.EmbeddedChannel channel = new io.netty.channel.embedded.EmbeddedChannel(
            new OutboundPacketFirewall(new io.github.kaseyawolf2.horizonwright.core.action.ActionSessionGuard()));
        BlockPosition target = new BlockPosition(272, 75, 359);
        try (ServerBlockConfirmation watch = ServerBlockConfirmation.watch(channel, target, true)) {
            for (boolean air : new boolean[] { false, true, false }) {
                net.minecraft.network.play.server.S23PacketBlockChange packet = new net.minecraft.network.play.server.S23PacketBlockChange() {

                    @Override
                    public int func_148879_d() {
                        return 272;
                    }

                    @Override
                    public int func_148878_e() {
                        return 75;
                    }

                    @Override
                    public int func_148877_f() {
                        return 359;
                    }

                    @Override
                    public net.minecraft.block.Block func_148880_c() {
                        return air ? new net.minecraft.block.BlockAir() {}
                            : new net.minecraft.block.Block(net.minecraft.block.material.Material.rock) {};
                    }
                };
                channel.writeInbound(packet);
                assertEquals(air, watch.confirmedAir());
                assertSame(packet, channel.readInbound());
            }
        } finally {
            channel.finish();
        }
    }

    @Test
    public void outgoingDrainAndClientAirCannotConfirmBreakButServerAirCan() {
        Object connection = new Object();
        BlockPosition target = new BlockPosition(281, 75, 385);
        try (ServerBlockConfirmation watch = ServerBlockConfirmation.watch(connection, target, true)) {
            assertFalse(watch.confirmedAir());
            ServerBlockConfirmation.record(connection, 281, 75, 385, false);
            assertFalse(watch.confirmedAir());
            ServerBlockConfirmation.record(connection, 281, 75, 385, true);
            assertTrue(watch.confirmedAir());
            ServerBlockConfirmation.record(connection, 281, 75, 385, false);
            assertFalse(watch.confirmedAir());
        }
    }

    @Test
    public void otherConnectionsPositionsAndRetiredWatchesCannotSupplyEvidence() {
        Object connection = new Object();
        ServerBlockConfirmation watch = ServerBlockConfirmation.watch(connection, new BlockPosition(1, 75, 2), true);
        try {
            ServerBlockConfirmation.record(new Object(), 1, 75, 2, true);
            ServerBlockConfirmation.record(connection, 1, 74, 2, true);
            assertFalse(watch.confirmedAir());
            watch.close();
            ServerBlockConfirmation.record(connection, 1, 75, 2, true);
            assertFalse(watch.confirmedAir());
        } finally {
            watch.close();
        }
    }

    @Test
    public void multiBlockUpdateDecodesNegativeChunksAndModdedBlockIds() throws Exception {
        Object connection = new Object();
        try (ServerBlockConfirmation watch = ServerBlockConfirmation
            .watch(connection, new BlockPosition(-15, 75, -14), true)) {
            for (int block : new int[] { 256, 0, 24 }) {
                PacketBuffer buffer = new PacketBuffer(Unpooled.buffer());
                try {
                    buffer.writeInt(-1);
                    buffer.writeInt(-1);
                    buffer.writeShort(1);
                    buffer.writeInt(4);
                    buffer.writeShort(1 << 12 | 2 << 8 | 75);
                    buffer.writeShort(block << 4);
                    S22PacketMultiBlockChange packet = new S22PacketMultiBlockChange();
                    packet.readPacketData(buffer);
                    ServerBlockConfirmation.observe(connection, packet);
                    assertEquals(block == 0, watch.confirmedAir());
                } finally {
                    buffer.release();
                }
            }
        }
    }

    @Test
    public void chunkUpdatesDistinguishAirFromModBlocksAndMissingSections() {
        BlockPosition target = new BlockPosition(1, 75, 2);
        int bit = 1 << 4;
        int index = 11 * 256 + 2 * 16 + 1;
        for (boolean sky : new boolean[] { false, true }) {
            int highStart = sky ? 10240 : 8192;
            byte[] data = new byte[highStart + 2048];
            assertEquals(Boolean.TRUE, ServerBlockConfirmation.chunkAir(data, bit, bit, false, target, sky));
            data[highStart + index / 2] = 16;
            assertEquals(Boolean.FALSE, ServerBlockConfirmation.chunkAir(data, bit, bit, false, target, sky));
            data[index] = 24;
            assertEquals(Boolean.FALSE, ServerBlockConfirmation.chunkAir(data, bit, 0, false, target, sky));
        }
        assertNull(ServerBlockConfirmation.chunkAir(new byte[0], 0, 0, true, target, true));
        assertNull(ServerBlockConfirmation.chunkAir(new byte[0], 1, 0, false, target, true));
        assertEquals(Boolean.TRUE, ServerBlockConfirmation.chunkAir(new byte[4096], 1, 0, true, target, true));
        assertNull(ServerBlockConfirmation.chunkAir(new byte[0], bit, 0, false, target, true));
    }
}
