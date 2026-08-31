package io.github.kaseyawolf2.horizonwright.forge.client.container;

import io.github.kaseyawolf2.horizonwright.core.container.ContainerTransaction;

/** Single-owner boundary for live, server-confirmed container transactions. */
public interface ConfirmedContainerTransactionExecutor {

    void begin(ContainerTransaction transaction);

    /**
     * Cancels only when the exact transaction still owns the executor. This
     * cleanup operation is safe during network-thread retirement because it
     * never reads or mutates Minecraft container state.
     */
    boolean cancel(ContainerTransaction expected, String reason);
}
