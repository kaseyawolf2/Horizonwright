package io.github.kaseyawolf2.horizonwright.runtime.persistence.session;

import io.github.kaseyawolf2.horizonwright.core.persistence.RuntimeEnvelope;
import io.github.kaseyawolf2.horizonwright.core.persistence.UnresolvedDeathState;
import io.github.kaseyawolf2.horizonwright.core.task.IHorizonwrightController;

/**
 * Fresh per-connection runtime owned by {@link ProfileRuntimeSession}.
 *
 * <p>
 * The complete validated envelope is restored in one callback before this runtime can receive a tick. Implementations
 * may delegate its controller portion to {@code RuntimeStateRestoreBoundary} and its death portion to the live safety
 * runtime when those production adapters are wired.
 * </p>
 */
public interface RuntimeSessionRuntime extends AutoCloseable {

    void restore(RuntimeEnvelope envelope);

    void clientTick();

    IHorizonwrightController getController();

    UnresolvedDeathState snapshotUnresolvedDeathState();

    @Override
    void close();
}
