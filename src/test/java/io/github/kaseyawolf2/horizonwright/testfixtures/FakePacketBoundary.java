package io.github.kaseyawolf2.horizonwright.testfixtures;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

public final class FakePacketBoundary {

    private final Deque<Packet> queued = new ArrayDeque<>();
    private final List<Packet> written = new ArrayList<>();
    private final List<Packet> dropped = new ArrayList<>();
    private long nextSequence = 1L;

    public synchronized Packet queueOutbound(String type, long actionEpoch, String payload) {
        if (type == null || type.trim()
            .isEmpty()) {
            throw new IllegalArgumentException("type must not be blank");
        }
        if (actionEpoch < 1L) {
            throw new IllegalArgumentException("actionEpoch must be positive");
        }
        Packet packet = new Packet(nextSequence++, type.trim(), actionEpoch, payload == null ? "" : payload);
        queued.addLast(packet);
        return packet;
    }

    public synchronized void flush(WriteGate gate) {
        if (gate == null) {
            throw new IllegalArgumentException("gate must not be null");
        }
        while (!queued.isEmpty()) {
            Packet packet = queued.removeFirst();
            if (gate.mayWrite(packet)) {
                written.add(packet);
            } else {
                dropped.add(packet);
            }
        }
    }

    public synchronized List<Packet> queuedPackets() {
        return immutableCopy(queued);
    }

    public synchronized List<Packet> writtenPackets() {
        return immutableCopy(written);
    }

    public synchronized List<Packet> droppedPackets() {
        return immutableCopy(dropped);
    }

    private static List<Packet> immutableCopy(Iterable<Packet> packets) {
        List<Packet> copy = new ArrayList<>();
        for (Packet packet : packets) {
            copy.add(packet);
        }
        return Collections.unmodifiableList(copy);
    }

    public interface WriteGate {

        boolean mayWrite(Packet packet);
    }

    public static final class Packet {

        private final long sequence;
        private final String type;
        private final long actionEpoch;
        private final String payload;

        private Packet(long sequence, String type, long actionEpoch, String payload) {
            this.sequence = sequence;
            this.type = type;
            this.actionEpoch = actionEpoch;
            this.payload = payload;
        }

        public long getSequence() {
            return sequence;
        }

        public String getType() {
            return type;
        }

        public long getActionEpoch() {
            return actionEpoch;
        }

        public String getPayload() {
            return payload;
        }
    }
}
