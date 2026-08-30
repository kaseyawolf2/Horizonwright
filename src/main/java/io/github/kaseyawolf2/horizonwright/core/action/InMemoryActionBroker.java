package io.github.kaseyawolf2.horizonwright.core.action;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class InMemoryActionBroker implements ActionBroker {

    private final Map<ActionCapability, Lease> leasesByCapability = new EnumMap<>(ActionCapability.class);
    private final Set<ActionRevocationListener> revocationListeners = new LinkedHashSet<>();
    private long epoch = 1L;
    private long nextLeaseId = 1L;
    private boolean safetyLocked;
    private boolean automationLocked;
    private int revocationTransitionsInProgress;
    private ActionRevocation lastSafetyRevocation;
    private ActionRevocation lastAutomationRevocation;

    @Override
    public synchronized Optional<ActionLease> tryAcquire(String owner, Set<ActionCapability> requestedCapabilities) {
        String normalizedOwner = normalizeOwner(owner);
        EnumSet<ActionCapability> capabilities = copyCapabilities(requestedCapabilities);

        if (safetyLocked || automationLocked || revocationTransitionsInProgress > 0) {
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
        return safetyLocked || automationLocked;
    }

    @Override
    public synchronized boolean isDeathSafetyLocked() {
        return safetyLocked;
    }

    @Override
    public synchronized boolean isAutomationLocked() {
        return automationLocked;
    }

    @Override
    public synchronized ActionBrokerSnapshot snapshot() {
        Map<ActionCapability, String> owners = new EnumMap<>(ActionCapability.class);
        for (Map.Entry<ActionCapability, Lease> entry : leasesByCapability.entrySet()) {
            owners.put(entry.getKey(), entry.getValue().owner);
        }
        return new ActionBrokerSnapshot(epoch, automationLocked, safetyLocked, owners);
    }

    @Override
    public void addRevocationListener(ActionRevocationListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("listener must not be null");
        }
        synchronized (this) {
            revocationListeners.add(listener);
        }
    }

    @Override
    public synchronized void removeRevocationListener(ActionRevocationListener listener) {
        revocationListeners.remove(listener);
    }

    @Override
    public void revokeAll() {
        notifyRevocation(beginRevocation(ActionRevocationReason.EXPLICIT_REVOCATION));
    }

    @Override
    public void advanceEpochPast(long floor) {
        if (floor < 0L || floor >= Long.MAX_VALUE - 1L) {
            throw new IllegalArgumentException("epoch floor must leave a subsequently advanceable epoch");
        }
        RevocationDispatch dispatch;
        synchronized (this) {
            if (epoch > floor) {
                return;
            }
            if (safetyLocked || automationLocked
                || revocationTransitionsInProgress > 0
                || !leasesByCapability.isEmpty()) {
                throw new IllegalStateException("persisted epoch restoration requires a fresh, unlocked broker");
            }
            long revokedEpoch = epoch;
            epoch = floor + 1L;
            dispatch = beginDispatchLocked(
                new ActionRevocation(revokedEpoch, epoch, ActionRevocationReason.RESTORE_EPOCH_ADVANCE));
        }
        notifyRevocation(dispatch);
    }

    @Override
    public void enterAutomationLockdown() {
        RevocationDispatch dispatch;
        synchronized (this) {
            if (automationLocked) {
                dispatch = beginDispatchLocked(lastAutomationRevocation);
            } else {
                automationLocked = true;
                lastAutomationRevocation = advanceEpochAndClearLocked(ActionRevocationReason.AUTOMATION_STOP);
                dispatch = beginDispatchLocked(lastAutomationRevocation);
            }
        }
        notifyRevocation(dispatch);
    }

    @Override
    public void leaveAutomationLockdown() {
        RevocationDispatch dispatch;
        synchronized (this) {
            if (!automationLocked) {
                return;
            }
            ActionRevocation revocation = advanceEpochAndClearLocked(ActionRevocationReason.AUTOMATION_REARMED);
            automationLocked = false;
            lastAutomationRevocation = null;
            dispatch = beginDispatchLocked(revocation);
        }
        notifyRevocation(dispatch);
    }

    @Override
    public void enterSafetyLockdown() {
        RevocationDispatch dispatch;
        synchronized (this) {
            if (safetyLocked) {
                dispatch = beginDispatchLocked(lastSafetyRevocation);
            } else {
                safetyLocked = true;
                lastSafetyRevocation = advanceEpochAndClearLocked(ActionRevocationReason.SAFETY_LOCKDOWN);
                dispatch = beginDispatchLocked(lastSafetyRevocation);
            }
        }
        notifyRevocation(dispatch);
    }

    @Override
    public void leaveSafetyLockdown() {
        RevocationDispatch dispatch;
        synchronized (this) {
            if (!safetyLocked) {
                return;
            }
            ActionRevocation revocation = advanceEpochAndClearLocked(ActionRevocationReason.SAFETY_LOCKDOWN_RELEASED);
            safetyLocked = false;
            lastSafetyRevocation = null;
            dispatch = beginDispatchLocked(revocation);
        }
        notifyRevocation(dispatch);
    }

    private synchronized boolean isValid(Lease lease) {
        if (lease.closed || safetyLocked || automationLocked || lease.epoch != epoch) {
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

    private RevocationDispatch beginRevocation(ActionRevocationReason reason) {
        synchronized (this) {
            return beginDispatchLocked(advanceEpochAndClearLocked(reason));
        }
    }

    private ActionRevocation advanceEpochAndClearLocked(ActionRevocationReason reason) {
        if (epoch == Long.MAX_VALUE) {
            throw new IllegalStateException("action epoch exhausted");
        }
        long revokedEpoch = epoch;
        for (Lease lease : new HashSet<>(leasesByCapability.values())) {
            lease.closed = true;
        }
        leasesByCapability.clear();
        epoch++;
        return new ActionRevocation(revokedEpoch, epoch, reason);
    }

    private RevocationDispatch beginDispatchLocked(ActionRevocation revocation) {
        if (revocation == null) {
            throw new IllegalStateException("safety revocation state is unavailable");
        }
        revocationTransitionsInProgress++;
        return new RevocationDispatch(revocation, new ArrayList<>(revocationListeners));
    }

    private void notifyRevocation(RevocationDispatch dispatch) {
        Throwable firstFailure = null;
        try {
            for (ActionRevocationListener listener : dispatch.listeners) {
                try {
                    listener.onActionEpochRevoked(dispatch.revocation);
                } catch (RuntimeException | LinkageError failure) {
                    if (firstFailure == null) {
                        firstFailure = failure;
                    } else {
                        firstFailure.addSuppressed(failure);
                    }
                }
            }
        } finally {
            synchronized (this) {
                revocationTransitionsInProgress--;
            }
        }
        if (firstFailure != null) {
            throw new IllegalStateException("one or more action revocation listeners failed", firstFailure);
        }
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

    private static final class RevocationDispatch {

        private final ActionRevocation revocation;
        private final List<ActionRevocationListener> listeners;

        private RevocationDispatch(ActionRevocation revocation, List<ActionRevocationListener> listeners) {
            this.revocation = revocation;
            this.listeners = listeners;
        }
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
