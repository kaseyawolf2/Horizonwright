package io.github.kaseyawolf2.horizonwright.forge.client.container;

import java.util.Optional;

import net.minecraft.client.Minecraft;

import io.github.kaseyawolf2.horizonwright.DevelopmentTrace;
import io.github.kaseyawolf2.horizonwright.core.action.ActionSessionGuard;
import io.github.kaseyawolf2.horizonwright.core.container.ContainerClickCorrelation;
import io.github.kaseyawolf2.horizonwright.core.container.ContainerSnapshot;
import io.github.kaseyawolf2.horizonwright.core.container.ContainerTransaction;
import io.github.kaseyawolf2.horizonwright.core.container.VerifiedContainerClick;
import io.github.kaseyawolf2.horizonwright.forge.client.network.ContainerTransactionPacketCoordinator;

/**
 * Client-tick executor for server-confirmed container plans. It dispatches no
 * later click until the previous click has both a matching accepted response
 * and its exact synchronized after-snapshot.
 */
public final class LiveContainerTransactionExecutor implements ConfirmedContainerTransactionExecutor {

    private static final long DEFAULT_TIMEOUT_NANOS = 5_000_000_000L;

    interface ClientAccess {

        void requireClientThread();

        ContainerSnapshot capture(long revision);

        void click(VerifiedContainerClick click);
    }

    interface EpochSource {

        long activeEpochOrZero();
    }

    interface NanoClock {

        long nanoTime();
    }

    private final ClientAccess client;
    private final EpochSource epochs;
    private final ContainerTransactionPacketCoordinator packets;
    private final NanoClock clock;
    private final long timeoutNanos;
    private ContainerClickCorrelation active;

    public LiveContainerTransactionExecutor(Minecraft minecraft, ActionSessionGuard guard,
        ContainerTransactionPacketCoordinator packets) {
        this(
            new MinecraftClientAccess(minecraft),
            guard::activeEpochOrZero,
            packets,
            System::nanoTime,
            DEFAULT_TIMEOUT_NANOS);
    }

    LiveContainerTransactionExecutor(ClientAccess client, EpochSource epochs,
        ContainerTransactionPacketCoordinator packets, NanoClock clock, long timeoutNanos) {
        if (client == null || epochs == null || packets == null || clock == null || timeoutNanos <= 0L) {
            throw new IllegalArgumentException("executor dependencies and a positive timeout are required");
        }
        this.client = client;
        this.epochs = epochs;
        this.packets = packets;
        this.clock = clock;
        this.timeoutNanos = timeoutNanos;
    }

    @Override
    public synchronized void begin(ContainerTransaction transaction) {
        client.requireClientThread();
        if (transaction == null) {
            throw new IllegalArgumentException("transaction must not be null");
        }
        if (active != null) {
            throw new IllegalStateException("another live container transaction is already active");
        }
        if (!packets.isBoundaryReady()) {
            throw new IllegalStateException("container packet boundary is unavailable");
        }
        if (epochs.activeEpochOrZero() != transaction.getActionEpoch()) {
            throw new IllegalStateException("container transaction does not own the active action epoch");
        }
        trace("begin", transaction, "boundaryReady", packets.isBoundaryReady());
        active = new ContainerClickCorrelation(transaction);
        packets.activate(active);
        try {
            dispatchNext();
        } catch (RuntimeException failure) {
            trace("begin-failed", transaction, "failure", DevelopmentTrace.error(failure));
            active.cancel("live container transaction failed before its first click completed");
            releaseIfTerminal();
            throw failure;
        }
        releaseIfTerminal();
    }

    public synchronized void tick() {
        client.requireClientThread();
        if (active == null) {
            DevelopmentTrace.event("container-live", "tick-idle", "activeEpoch", epochs.activeEpochOrZero());
            return;
        }
        trace("tick", active.getTransaction(), "correlationState", active.getState());
        try {
            long now = clock.nanoTime();
            long epoch = epochs.activeEpochOrZero();
            if (epoch != active.getTransaction()
                .getActionEpoch()) {
                active.cancel(
                    "action epoch changed from " + active.getTransaction()
                        .getActionEpoch() + " to " + epoch);
            } else {
                active.expire(now);
            }
            if (active.getState() == ContainerClickCorrelation.State.SERVER_ACCEPTED
                || active.getState() == ContainerClickCorrelation.State.SERVER_REJECTED_AWAITING_SYNC) {
                VerifiedContainerClick click = active.getOutstandingClick()
                    .orElseThrow(() -> new IllegalStateException("accepted transaction has no outstanding click"));
                ContainerSnapshot observed = client.capture(
                    click.getExpectedAfter()
                        .getRevision());
                active.observeSynchronizedSnapshot(observed, epoch, now);
            }
            if (active.getState() == ContainerClickCorrelation.State.READY) {
                dispatchNext();
            }
        } catch (RuntimeException failure) {
            trace("tick-failed", active.getTransaction(), "failure", DevelopmentTrace.error(failure));
            active.cancel("live container transaction failed while awaiting synchronization");
            releaseIfTerminal();
            throw failure;
        }
        releaseIfTerminal();
    }

    public synchronized void cancel(String reason) {
        if (active != null) {
            trace("cancel", active.getTransaction(), "reason", reason);
            active.cancel(reason);
            releaseIfTerminal();
        }
    }

    @Override
    public synchronized boolean cancel(ContainerTransaction expected, String reason) {
        if (expected == null || active == null || active.getTransaction() != expected) {
            DevelopmentTrace.event(
                "container-live",
                "cancel-mismatch",
                "expected",
                expected == null ? "none" : expected.getTransactionId(),
                "active",
                active == null ? "none"
                    : active.getTransaction()
                        .getTransactionId());
            return false;
        }
        trace("cancel", expected, "reason", reason);
        active.cancel(reason);
        releaseIfTerminal();
        return true;
    }

    public synchronized boolean isActive() {
        return active != null;
    }

    public synchronized Optional<ContainerTransaction> getActiveTransaction() {
        return active == null ? Optional.empty() : Optional.of(active.getTransaction());
    }

    private void dispatchNext() {
        ContainerTransaction transaction = active.getTransaction();
        int nextIndex = transaction.getCompletedClickCount();
        if (nextIndex >= transaction.getClicks()
            .size()) {
            throw new IllegalStateException("transaction has no next click but is not terminal");
        }
        VerifiedContainerClick planned = transaction.getClicks()
            .get(nextIndex);
        ContainerSnapshot observed = client.capture(
            planned.getExpectedBefore()
                .getRevision());
        Optional<VerifiedContainerClick> prepared = active
            .prepare(observed, epochs.activeEpochOrZero(), clock.nanoTime(), timeoutNanos);
        if (!prepared.isPresent()) {
            trace("dispatch-not-prepared", transaction, "nextIndex", nextIndex);
            return;
        }
        try {
            trace(
                "dispatch",
                transaction,
                "click",
                prepared.get()
                    .getClickId(),
                "slot",
                prepared.get()
                    .getSlot(),
                "mouseButton",
                prepared.get()
                    .getMouseButton(),
                "clickMode",
                prepared.get()
                    .getClickMode());
            client.click(prepared.get());
        } catch (RuntimeException failure) {
            trace("dispatch-failed", transaction, "failure", DevelopmentTrace.error(failure));
            active.cancel(
                "client failed while dispatching container click " + prepared.get()
                    .getClickId());
            throw failure;
        }
    }

    private void releaseIfTerminal() {
        if (active != null && active.isTerminal()) {
            trace(
                "released",
                active.getTransaction(),
                "correlationState",
                active.getState(),
                "abortReason",
                active.getTransaction()
                    .getAbortReason());
            packets.release(active);
            active = null;
        }
    }

    private void trace(String event, ContainerTransaction transaction, Object... extraFields) {
        Object[] fields = new Object[10 + extraFields.length];
        fields[0] = "transaction";
        fields[1] = transaction.getTransactionId();
        fields[2] = "state";
        fields[3] = transaction.getState();
        fields[4] = "epoch";
        fields[5] = transaction.getActionEpoch();
        fields[6] = "activeEpoch";
        fields[7] = epochs.activeEpochOrZero();
        fields[8] = "completedClicks";
        fields[9] = transaction.getCompletedClickCount();
        System.arraycopy(extraFields, 0, fields, 10, extraFields.length);
        DevelopmentTrace.event("container-live", event, fields);
    }

    private static final class MinecraftClientAccess implements ClientAccess {

        private final Minecraft minecraft;
        private final MinecraftContainerSnapshotter snapshots = new MinecraftContainerSnapshotter();

        private MinecraftClientAccess(Minecraft minecraft) {
            if (minecraft == null) {
                throw new IllegalArgumentException("minecraft must not be null");
            }
            this.minecraft = minecraft;
        }

        @Override
        public void requireClientThread() {
            if (!minecraft.func_152345_ab() || minecraft.thePlayer == null || minecraft.playerController == null) {
                throw new IllegalStateException("a joined Minecraft client thread is required");
            }
        }

        @Override
        public ContainerSnapshot capture(long revision) {
            return snapshots.captureCurrent(minecraft, revision);
        }

        @Override
        public void click(VerifiedContainerClick click) {
            minecraft.playerController.windowClick(
                click.getExpectedBefore()
                    .getWindowId(),
                click.getSlot(),
                click.getMouseButton(),
                click.getClickMode(),
                minecraft.thePlayer);
        }
    }
}
