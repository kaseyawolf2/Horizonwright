package io.github.kaseyawolf2.horizonwright.forge.client.repair;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.junit.Test;

import io.github.kaseyawolf2.horizonwright.core.container.ContainerSnapshot;
import io.github.kaseyawolf2.horizonwright.core.container.ContainerTransaction;
import io.github.kaseyawolf2.horizonwright.core.container.ItemFingerprint;
import io.github.kaseyawolf2.horizonwright.core.container.VerifiedContainerClick;
import io.github.kaseyawolf2.horizonwright.core.repair.RepairToolSnapshot;
import io.github.kaseyawolf2.horizonwright.forge.client.container.ConfirmedContainerTransactionExecutor;
import io.github.kaseyawolf2.horizonwright.runtime.task.RepairActionConfirmation;
import io.github.kaseyawolf2.horizonwright.runtime.task.RepairActionProgress;
import io.github.kaseyawolf2.horizonwright.runtime.task.RepairActionRequest;
import io.github.kaseyawolf2.horizonwright.runtime.task.RepairActionState;

public class TinkersRepairActionHandleTest {

    private static final RepairToolSnapshot INPUT = new RepairToolSnapshot("pick", 700, 1000, 2);
    private static final RepairToolSnapshot OUTPUT = new RepairToolSnapshot("pick", 400, 1000, 2);

    @Test
    public void confirmsOnlyAfterExactTransactionAndCachesSynchronizedEvidence() {
        ContainerTransaction transaction = transaction();
        RepairActionRequest request = request(transaction);
        FakeExecutor executor = new FakeExecutor();
        CountingConfirmation confirmations = new CountingConfirmation();
        int[] sessionCloses = { 0 };
        TinkersRepairActionHandle handle = new TinkersRepairActionHandle(
            request,
            executor,
            confirmations,
            () -> sessionCloses[0]++);

        assertEquals(
            RepairActionState.EXECUTING,
            handle.progress()
                .getState());
        complete(transaction, true);
        RepairActionProgress completed = handle.progress();
        assertEquals(RepairActionState.CONFIRMED, completed.getState());
        assertEquals(
            OUTPUT,
            completed.getConfirmation()
                .get()
                .getOutputTool());
        assertEquals(1, confirmations.calls);
        assertEquals(1, sessionCloses[0]);
        assertEquals(
            RepairActionState.CONFIRMED,
            handle.progress()
                .getState());
        assertEquals(1, confirmations.calls);
        assertEquals(1, sessionCloses[0]);
    }

    @Test
    public void serverRejectionNeverRequestsConfirmationEvidence() {
        ContainerTransaction transaction = transaction();
        CountingConfirmation confirmations = new CountingConfirmation();
        TinkersRepairActionHandle handle = new TinkersRepairActionHandle(
            request(transaction),
            new FakeExecutor(),
            confirmations);

        complete(transaction, false);

        assertEquals(
            RepairActionState.REJECTED,
            handle.progress()
                .getState());
        assertEquals(0, confirmations.calls);
    }

    @Test
    public void cancelTargetsOnlyItsExactTransaction() {
        ContainerTransaction transaction = transaction();
        FakeExecutor executor = new FakeExecutor();
        TinkersRepairActionHandle handle = new TinkersRepairActionHandle(
            request(transaction),
            executor,
            new CountingConfirmation());

        handle.cancel();

        assertSame(transaction, executor.cancelled);
        assertTrue(executor.reason.contains("repair task"));
    }

    private static RepairActionRequest request(ContainerTransaction transaction) {
        return new RepairActionRequest("request", 1L, 41L, "fingerprint", transaction, INPUT);
    }

    private static ContainerTransaction transaction() {
        ItemFingerprint tool = new ItemFingerprint("TConstruct:pickaxe", 0, "tool", 1);
        ContainerSnapshot before = new ContainerSnapshot(
            7,
            TinkersRepairContainerAdapter.TOOL_STATION_CONTAINER,
            "station",
            0L,
            Arrays.asList(tool, tool, null, null),
            null);
        ContainerSnapshot after = new ContainerSnapshot(
            7,
            TinkersRepairContainerAdapter.TOOL_STATION_CONTAINER,
            "station",
            1L,
            Arrays.asList(null, null, null, tool),
            null);
        return new ContainerTransaction(
            "repair",
            41L,
            Arrays.asList(new VerifiedContainerClick("take", 0, 0, 0, before, after)));
    }

    private static void complete(ContainerTransaction transaction, boolean accepted) {
        VerifiedContainerClick click = transaction.nextClick(
            transaction.getClicks()
                .get(0)
                .getExpectedBefore(),
            41L)
            .get();
        transaction.confirm(click.getClickId(), accepted, click.getExpectedAfter(), 41L);
    }

    private static final class FakeExecutor implements ConfirmedContainerTransactionExecutor {

        private ContainerTransaction cancelled;
        private String reason;

        @Override
        public void begin(ContainerTransaction transaction) {}

        @Override
        public boolean cancel(ContainerTransaction expected, String reason) {
            cancelled = expected;
            this.reason = reason;
            return true;
        }
    }

    private static final class CountingConfirmation implements TinkersRepairActionHandle.ConfirmationSource {

        private int calls;

        @Override
        public RepairActionConfirmation confirm(RepairActionRequest request) {
            calls++;
            return new RepairActionConfirmation(request.getTransactionFingerprint(), OUTPUT, 2, true);
        }
    }
}
