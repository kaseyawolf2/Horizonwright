package io.github.kaseyawolf2.horizonwright.testfixtures;

public final class FakeStorageTransaction {

    private final int windowId;
    private final int actionNumber;
    private final long actionEpoch;
    private final FakeInventory.Snapshot before;
    private final FakeInventory.Snapshot expectedAfter;
    private State state = State.AWAITING_CONFIRMATION;
    private String diagnostic = "Awaiting server confirmation";

    private FakeStorageTransaction(int windowId, int actionNumber, long actionEpoch, FakeInventory.Snapshot before,
        FakeInventory.Snapshot expectedAfter) {
        this.windowId = windowId;
        this.actionNumber = actionNumber;
        this.actionEpoch = actionEpoch;
        this.before = before;
        this.expectedAfter = expectedAfter;
    }

    public static FakeStorageTransaction prepareMove(int windowId, int actionNumber, long actionEpoch,
        FakeInventory.Snapshot before, int fromSlot, int toSlot, int count) {
        if (windowId < 1 || actionNumber < 1 || actionEpoch < 1L || before == null) {
            throw new IllegalArgumentException("window, action, epoch, and before snapshot are required");
        }
        FakeInventory expected = FakeInventory.fromSnapshot(before);
        expected.move(fromSlot, toSlot, count);
        return new FakeStorageTransaction(windowId, actionNumber, actionEpoch, before, expected.snapshot());
    }

    public synchronized boolean confirm(Confirmation confirmation) {
        requireAwaiting();
        if (confirmation == null) {
            throw new IllegalArgumentException("confirmation must not be null");
        }
        if (confirmation.windowId != windowId) {
            return abort("Window ID changed");
        }
        if (confirmation.actionNumber != actionNumber) {
            return abort("Action number changed");
        }
        if (confirmation.actionEpoch != actionEpoch) {
            return abort("Action epoch changed");
        }
        if (!expectedAfter.equals(confirmation.serverSnapshot)) {
            return abort("Server inventory did not match the expected post-click snapshot");
        }
        state = State.COMMITTED;
        diagnostic = "Server confirmed exact post-click snapshot";
        return true;
    }

    public synchronized void timeout() {
        requireAwaiting();
        abort("Confirmation timed out; transaction must be rebuilt from a new snapshot");
    }

    public FakeInventory.Snapshot getBefore() {
        return before;
    }

    public FakeInventory.Snapshot getExpectedAfter() {
        return expectedAfter;
    }

    public synchronized State getState() {
        return state;
    }

    public synchronized String getDiagnostic() {
        return diagnostic;
    }

    private boolean abort(String reason) {
        state = State.ABORTED;
        diagnostic = reason;
        return false;
    }

    private void requireAwaiting() {
        if (state != State.AWAITING_CONFIRMATION) {
            throw new IllegalStateException("transaction is already terminal: " + state);
        }
    }

    public enum State {
        AWAITING_CONFIRMATION,
        COMMITTED,
        ABORTED
    }

    public static final class Confirmation {

        private final int windowId;
        private final int actionNumber;
        private final long actionEpoch;
        private final FakeInventory.Snapshot serverSnapshot;

        public Confirmation(int windowId, int actionNumber, long actionEpoch, FakeInventory.Snapshot serverSnapshot) {
            if (serverSnapshot == null) {
                throw new IllegalArgumentException("serverSnapshot must not be null");
            }
            this.windowId = windowId;
            this.actionNumber = actionNumber;
            this.actionEpoch = actionEpoch;
            this.serverSnapshot = serverSnapshot;
        }
    }
}
