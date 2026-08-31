package io.github.kaseyawolf2.horizonwright.forge.client.safety;

/** Immutable exact preparation authority published from the Minecraft thread. */
public final class GravePreparationPacketSnapshot {

    private final long permitId;
    private final int emptyHotbarSlot;

    public GravePreparationPacketSnapshot(long permitId, int emptyHotbarSlot) {
        if (permitId <= 0L || emptyHotbarSlot < 0 || emptyHotbarSlot > 8) {
            throw new IllegalArgumentException("grave preparation permit and hotbar slot are invalid");
        }
        this.permitId = permitId;
        this.emptyHotbarSlot = emptyHotbarSlot;
    }

    public long getPermitId() {
        return permitId;
    }

    public int getEmptyHotbarSlot() {
        return emptyHotbarSlot;
    }
}
