package io.github.kaseyawolf2.horizonwright.forge.client.network;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.block.BlockAir;
import net.minecraft.network.play.server.S21PacketChunkData;
import net.minecraft.network.play.server.S22PacketMultiBlockChange;
import net.minecraft.network.play.server.S23PacketBlockChange;

import io.github.kaseyawolf2.horizonwright.core.excavation.BlockPosition;

/** Watches only active dig targets; client-predicted air never counts as server evidence. */
public final class ServerBlockConfirmation implements AutoCloseable {

    private static final Set<ServerBlockConfirmation> ACTIVE = new HashSet<>();
    private final Object connection;
    private final BlockPosition position;
    private final boolean hasSky;
    private Boolean air;
    private boolean closed;

    private ServerBlockConfirmation(Object connection, BlockPosition position, boolean hasSky) {
        this.connection = connection;
        this.position = position;
        this.hasSky = hasSky;
    }

    public static ServerBlockConfirmation watch(Object connection, BlockPosition position, boolean hasSky) {
        if (connection == null || position == null)
            throw new IllegalArgumentException("connection and target required");
        ServerBlockConfirmation watch = new ServerBlockConfirmation(connection, position, hasSky);
        synchronized (ACTIVE) {
            ACTIVE.add(watch);
        }
        return watch;
    }

    public boolean isConnection(Object current) {
        synchronized (ACTIVE) {
            return !closed && connection == current;
        }
    }

    public boolean confirmedAir() {
        synchronized (ACTIVE) {
            return !closed && Boolean.TRUE.equals(air);
        }
    }

    @Override
    public void close() {
        synchronized (ACTIVE) {
            closed = true;
            ACTIVE.remove(this);
        }
    }

    static void observe(Object connection, Object packet) {
        synchronized (ACTIVE) {
            if (ACTIVE.isEmpty()) return;
            if (packet instanceof S23PacketBlockChange) {
                S23PacketBlockChange change = (S23PacketBlockChange) packet;
                record(
                    connection,
                    change.func_148879_d(),
                    change.func_148878_e(),
                    change.func_148877_f(),
                    change.func_148880_c() instanceof BlockAir);
            } else if (packet instanceof S22PacketMultiBlockChange) {
                S22PacketMultiBlockChange changes = (S22PacketMultiBlockChange) packet;
                byte[] bytes = changes.func_148921_d();
                if (bytes == null || changes.func_148920_c() == null) return;
                for (int i = 0; i < changes.func_148922_e() && i * 4 + 3 < bytes.length; i++) {
                    int packed = unsignedShort(bytes, i * 4);
                    int block = unsignedShort(bytes, i * 4 + 2) >>> 4;
                    record(
                        connection,
                        changes.func_148920_c().chunkXPos * 16 + (packed >>> 12 & 15),
                        packed & 255,
                        changes.func_148920_c().chunkZPos * 16 + (packed >>> 8 & 15),
                        block == 0);
                }
            } else if (packet instanceof S21PacketChunkData) {
                S21PacketChunkData chunk = (S21PacketChunkData) packet;
                for (ServerBlockConfirmation watch : ACTIVE) {
                    if (watch.connection != connection || watch.position.getX() >> 4 != chunk.func_149273_e()
                        || watch.position.getZ() >> 4 != chunk.func_149271_f()) continue;
                    // A partial update to another section says nothing about this target.
                    if (!chunk.func_149274_i() && (chunk.func_149276_g() & 1 << (watch.position.getY() >> 4)) == 0)
                        continue;
                    watch.air = chunkAir(
                        chunk.func_149272_d(),
                        chunk.func_149276_g(),
                        chunk.func_149270_h(),
                        chunk.func_149274_i(),
                        watch.position,
                        watch.hasSky);
                }
            }
        }
    }

    static Boolean chunkAir(byte[] data, int primary, int add, boolean full, BlockPosition position, boolean hasSky) {
        // A full packet with no sections unloads the chunk, rather than confirming air.
        if (data == null || full && primary == 0 || position.getY() < 0 || position.getY() >= 256) return null;
        int section = position.getY() >> 4;
        int bit = 1 << section;
        if ((primary & bit) == 0) return full ? Boolean.TRUE : null;
        int sectionIndex = Integer.bitCount(primary & (bit - 1));
        int blockIndex = (position.getY() & 15) * 256 + (position.getZ() & 15) * 16 + (position.getX() & 15);
        int offset = sectionIndex * 4096 + blockIndex;
        if (offset >= data.length) return null;
        if (data[offset] != 0) return false;
        if ((add & bit) != 0) {
            int count = Integer.bitCount(primary & 65535);
            int highOffset = count * (hasSky ? 10240 : 8192) + Integer.bitCount(add & (bit - 1)) * 2048
                + blockIndex / 2;
            if (highOffset >= data.length) return null;
            int high = data[highOffset] >>> (4 * (blockIndex & 1)) & 15;
            if (high != 0) return false;
        }
        return true;
    }

    static void record(Object connection, int x, int y, int z, boolean air) {
        synchronized (ACTIVE) {
            for (ServerBlockConfirmation watch : ACTIVE) {
                if (watch.connection == connection && watch.position.getX() == x
                    && watch.position.getY() == y
                    && watch.position.getZ() == z) watch.air = air;
            }
        }
    }

    private static int unsignedShort(byte[] bytes, int index) {
        return (bytes[index] & 255) << 8 | bytes[index + 1] & 255;
    }
}
