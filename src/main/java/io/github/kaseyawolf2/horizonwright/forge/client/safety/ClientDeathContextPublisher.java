package io.github.kaseyawolf2.horizonwright.forge.client.safety;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/** Publishes client-thread snapshots for synchronous use by the inbound network hook. */
public final class ClientDeathContextPublisher {

    private final ClientThreadVerifier clientThreadVerifier;
    private final AtomicReference<ClientDeathContextSnapshot> latest = new AtomicReference<>();

    public ClientDeathContextPublisher(ClientThreadVerifier clientThreadVerifier) {
        if (clientThreadVerifier == null) {
            throw new IllegalArgumentException("clientThreadVerifier must not be null");
        }
        this.clientThreadVerifier = clientThreadVerifier;
    }

    public ClientDeathContextSnapshot captureAndPublish(long connectionEpoch, long clientTick,
        ClientDeathContextSource source) {
        if (!clientThreadVerifier.isClientThread()) {
            throw new IllegalStateException("death context may only be captured on the client thread");
        }
        if (source == null) {
            throw new IllegalArgumentException("source must not be null");
        }
        ClientDeathContextSnapshot snapshot = new ClientDeathContextSnapshot(
            connectionEpoch,
            clientTick,
            source.getPlayerPosition(),
            source.getPlayerIdentity(),
            source.getActiveTaskId(),
            source.getInventorySnapshot());
        latest.set(snapshot);
        return snapshot;
    }

    public Optional<ClientDeathContextSnapshot> latestFor(long connectionEpoch) {
        ClientDeathContextSnapshot snapshot = latest.get();
        return snapshot != null && snapshot.getConnectionEpoch() == connectionEpoch ? Optional.of(snapshot)
            : Optional.<ClientDeathContextSnapshot>empty();
    }

    public void clear(long connectionEpoch) {
        ClientDeathContextSnapshot snapshot = latest.get();
        if (snapshot != null && snapshot.getConnectionEpoch() == connectionEpoch) {
            latest.compareAndSet(snapshot, null);
        }
    }
}
