package io.github.kaseyawolf2.horizonwright.runtime.task;

/** Session-scoped access to optional portable inventory preparation. */
public interface InventoryRuntimeAccess {

    InventoryService getInventoryService();
}
