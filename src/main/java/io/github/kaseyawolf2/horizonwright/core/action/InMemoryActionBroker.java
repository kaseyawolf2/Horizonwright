package io.github.kaseyawolf2.horizonwright.core.action;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class InMemoryActionBroker implements ActionBroker {

    private final Map<ActionCapability, Lease> leasesByCapability = new EnumMap<>(ActionCapability.class);
    private long epoch = 1L;
    private long nextLeaseId = 1L;
    private boolean safetyLocked;

    @Override
    public synchronized Optional<ActionLease> tryAcquire(String owner, Set<ActionCapability> requestedCapabilities) {
        String normalizedOwner = normalizeOwner(owner);
        EnumSet<ActionCapability> capabilities = copyCapabilities(requestedCapabilities);

        if (safetyLocked) {
            return Optional.empty();
        }
        for (ActionCapability capability : capabilities) {
            if (leasesByCapability.containsKey(capability)) {
                return Optional.empty();
            }
        }

        Lease lease = new Lease(this, nextLeaseId++, normalizedOwner, epoch, capabilities);
        for (ActionCapability capability : capabilities) {
            leasesByCapability.put(capability, lease);
        }
        return Optional.of(lease);
    }

    @Override
    public synchronized long currentEpoch() {
        return epoch;
    }

    @Override
    public synchronized boolean isSafetyLocked() {
        return safetyLocked;
    }

    @Override
    public synchronized ActionBrokerSnapshot snapshot() {
        Map<ActionCapability, String> owners = new EnumMap<>(ActionCapability.class);
        for (Map.Entry<ActionCapability, Lease> entry : leasesByCapability.entrySet()) {
            owners.put(entry.getKey(), entry.getValue().owner);
        }
        return new ActionBrokerSnapshot(epoch, safetyLocked, owners);
    }

    @Override
    public synchronized void revokeAll() {
        advanceEpochAndClear();
    }

    @Override
    public synchronized void enterSafetyLockdown() {
        if (!safetyLocked) {
            safetyLocked = true;
            advanceEpochAndClear();
        }
    }

    @Override
    public synchronized void leaveSafetyLockdown() {
        if (safetyLocked) {
            advanceEpochAndClear();
            safetyLocked = false;
        }
    }

    private synchronized boolean isValid(Lease lease) {
        if (lease.closed || safetyLocked || lease.epoch != epoch) {
            return false;
        }
        for (ActionCapability capability : lease.capabilities) {
            if (leasesByCapability.get(capability) != lease) {
                return false;
            }
        }
        return true;
    }

    private synchronized void release(Lease lease) {
        if (lease.closed) {
            return;
        }
        for (ActionCapability capability : lease.capabilities) {
            if (leasesByCapability.get(capability) == lease) {
                leasesByCapability.remove(capability);
            }
        }
        lease.closed = true;
    }

    private void advanceEpochAndClear() {
        if (epoch == Long.MAX_VALUE) {
            throw new IllegalStateException("action epoch exhausted");
        }
        for (Lease lease : new HashSet<>(leasesByCapability.values())) {
            lease.closed = true;
        }
        leasesByCapability.clear();
        epoch++;
    }

    private static String normalizeOwner(String owner) {
        if (owner == null || owner.trim()
            .isEmpty()) {
            throw new IllegalArgumentException("owner must not be blank");
        }
        return owner.trim();
    }

    private static EnumSet<ActionCapability> copyCapabilities(Set<ActionCapability> requestedCapabilities) {
        if (requestedCapabilities == null || requestedCapabilities.isEmpty()) {
            throw new IllegalArgumentException("at least one action capability is required");
        }
        if (requestedCapabilities.contains(null)) {
            throw new IllegalArgumentException("action capabilities must not contain null");
        }
        return EnumSet.copyOf(requestedCapabilities);
    }

    private static final class Lease implements ActionLease {

        private final InMemoryActionBroker broker;
        private final long leaseId;
        private final String owner;
        private final long epoch;
        private final Set<ActionCapability> capabilities;
        private volatile boolean closed;

        private Lease(InMemoryActionBroker broker, long leaseId, String owner, long epoch,
            EnumSet<ActionCapability> capabilities) {
            this.broker = broker;
            this.leaseId = leaseId;
            this.owner = owner;
            this.epoch = epoch;
            this.capabilities = Collections.unmodifiableSet(EnumSet.copyOf(capabilities));
        }

        @Override
        public String getOwner() {
            return owner;
        }

        @Override
        public long getEpoch() {
            return epoch;
        }

        @Override
        public Set<ActionCapability> getCapabilities() {
            return capabilities;
        }

        @Override
        public boolean isValid() {
            return broker.isValid(this);
        }

        @Override
        public void close() {
            broker.release(this);
        }

        @Override
        public String toString() {
            return "ActionLease{" + "id=" + leaseId + ", owner='" + owner + '\'' + ", epoch=" + epoch + '}';
        }
    }
}
