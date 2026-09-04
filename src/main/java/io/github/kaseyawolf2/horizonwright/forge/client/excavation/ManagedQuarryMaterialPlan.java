package io.github.kaseyawolf2.horizonwright.forge.client.excavation;

/** Pure slot plan for temporarily staging one approved infrastructure material stack. */
final class ManagedQuarryMaterialPlan {

    private final int inventorySlot;
    private final int hotbarSlot;

    private ManagedQuarryMaterialPlan(int inventorySlot, int hotbarSlot) {
        this.inventorySlot = inventorySlot;
        this.hotbarSlot = hotbarSlot;
    }

    static ManagedQuarryMaterialPlan choose(int inventorySlot, boolean[] occupiedHotbar, int selectedHotbarSlot) {
        if (inventorySlot < 0 || inventorySlot > 35) {
            throw new IllegalArgumentException("inventorySlot must be from 0 to 35");
        }
        if (occupiedHotbar == null || occupiedHotbar.length != 9) {
            throw new IllegalArgumentException("occupiedHotbar must describe exactly nine slots");
        }
        if (selectedHotbarSlot < 0 || selectedHotbarSlot > 8) {
            throw new IllegalArgumentException("selectedHotbarSlot must be from 0 to 8");
        }
        if (inventorySlot < 9) return new ManagedQuarryMaterialPlan(inventorySlot, inventorySlot);
        for (int slot = 0; slot < occupiedHotbar.length; slot++) {
            if (!occupiedHotbar[slot]) return new ManagedQuarryMaterialPlan(inventorySlot, slot);
        }
        return new ManagedQuarryMaterialPlan(inventorySlot, selectedHotbarSlot);
    }

    int getInventorySlot() {
        return inventorySlot;
    }

    int getHotbarSlot() {
        return hotbarSlot;
    }

    boolean requiresStaging() {
        return inventorySlot >= 9;
    }
}
