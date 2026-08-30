package io.github.kaseyawolf2.horizonwright.forge.client.safety;

import io.github.kaseyawolf2.horizonwright.core.persistence.UnresolvedDeathState;
import io.github.kaseyawolf2.horizonwright.core.persistence.death.UnresolvedDeathPersistenceAdapter;
import io.github.kaseyawolf2.horizonwright.core.safety.death.ConnectionIdentity;
import io.github.kaseyawolf2.horizonwright.core.safety.death.DeathSafetyController;
import io.github.kaseyawolf2.horizonwright.core.safety.death.DeathSafetyInterlock;
import io.github.kaseyawolf2.horizonwright.core.safety.death.DeathSafetyPolicy;
import io.github.kaseyawolf2.horizonwright.core.safety.death.DeathSafetyUpdate;
import io.github.kaseyawolf2.horizonwright.core.safety.death.UnresolvedDeathProjection;

/** Owns one live death-safety authority and retires all event sources at disconnect. */
public final class DeathSafetyConnectionCoordinator {

    private final DeathSafetyPolicy policy;
    private final DeathSafetyInterlock interlock;
    private Session activeSession;
    private long highestConnectionEpoch;

    public DeathSafetyConnectionCoordinator(DeathSafetyPolicy policy, DeathSafetyInterlock interlock) {
        if (policy == null || interlock == null) {
            throw new IllegalArgumentException("policy and interlock must not be null");
        }
        this.policy = policy;
        this.interlock = interlock;
    }

    public synchronized Session openFresh(ConnectionIdentity identity) {
        requireNoActiveSession();
        requireAdvancingConnection(identity);
        return activate(
            identity,
            new DeathSafetyController(policy, interlock, identity),
            new GraveActivationReplayBlock(false));
    }

    public synchronized Session restore(UnresolvedDeathState persisted, ConnectionIdentity identity) {
        requireNoActiveSession();
        requireAdvancingConnection(identity);
        UnresolvedDeathProjection projection = UnresolvedDeathPersistenceAdapter
            .prepareRestartProjection(persisted, identity);
        GraveActivationReplayBlock replayBlock = new GraveActivationReplayBlock(
            persisted.getGraveState()
                .requiresActivationReplayBlock());
        DeathSafetyController controller = DeathSafetyController.restore(policy, interlock, identity, projection);
        return activate(identity, controller, replayBlock);
    }

    /** Records disconnect in the kernel before making all old boundary adapters fail closed. */
    public synchronized DeathSafetyUpdate disconnect(Session session, long clientTick) {
        requireActive(session);
        try {
            return session.controller.onDisconnect(session.stamps.next(clientTick));
        } finally {
            session.stamps.retire();
            if (activeSession == session) {
                activeSession = null;
            }
        }
    }

    public synchronized boolean isActive(Session session) {
        return activeSession == session && session != null && session.stamps.isOpen();
    }

    private Session activate(ConnectionIdentity identity, DeathSafetyController controller,
        GraveActivationReplayBlock replayBlock) {
        ConnectionSafetyEventStampSource stamps = new ConnectionSafetyEventStampSource(identity.getConnectionEpoch());
        Session session = new Session(identity, controller, stamps, replayBlock);
        highestConnectionEpoch = identity.getConnectionEpoch();
        activeSession = session;
        return session;
    }

    private void requireNoActiveSession() {
        if (activeSession != null) {
            throw new IllegalStateException("the previous connection must be disconnected before opening another");
        }
    }

    private void requireAdvancingConnection(ConnectionIdentity identity) {
        if (identity == null) {
            throw new IllegalArgumentException("identity must not be null");
        }
        if (identity.getConnectionEpoch() <= highestConnectionEpoch) {
            throw new IllegalArgumentException("connection epochs must advance monotonically");
        }
    }

    private void requireActive(Session session) {
        if (!isActive(session)) {
            throw new IllegalArgumentException("session is not the active connection");
        }
    }

    public static final class Session {

        private final ConnectionIdentity identity;
        private final DeathSafetyController controller;
        private final ConnectionSafetyEventStampSource stamps;
        private final GraveActivationReplayBlock replayBlock;

        private Session(ConnectionIdentity identity, DeathSafetyController controller,
            ConnectionSafetyEventStampSource stamps, GraveActivationReplayBlock replayBlock) {
            this.identity = identity;
            this.controller = controller;
            this.stamps = stamps;
            this.replayBlock = replayBlock;
        }

        public ConnectionIdentity getIdentity() {
            return identity;
        }

        public DeathSafetyController getController() {
            return controller;
        }

        public ConnectionSafetyEventStampSource getStamps() {
            return stamps;
        }

        public GraveActivationReplayBlock getReplayBlock() {
            return replayBlock;
        }
    }
}
