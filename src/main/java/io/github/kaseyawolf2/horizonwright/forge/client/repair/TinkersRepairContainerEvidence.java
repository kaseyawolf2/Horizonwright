package io.github.kaseyawolf2.horizonwright.forge.client.repair;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.github.kaseyawolf2.horizonwright.core.container.ItemFingerprint;
import io.github.kaseyawolf2.horizonwright.core.repair.RepairToolSnapshot;

/** Immutable client-thread evidence from one exact TConstruct repair container layout. */
public final class TinkersRepairContainerEvidence {

    private final TinkersStationKind stationKind;
    private final int windowId;
    private final int stationSlotCount;
    private final int reservedContainerSlot;
    private final RepairToolSnapshot tool;
    private final List<ItemFingerprint> materialSlots;

    TinkersRepairContainerEvidence(TinkersStationKind stationKind, int windowId, int stationSlotCount,
        int reservedContainerSlot, RepairToolSnapshot tool, List<ItemFingerprint> materialSlots) {
        this.stationKind = stationKind;
        this.windowId = windowId;
        this.stationSlotCount = stationSlotCount;
        this.reservedContainerSlot = reservedContainerSlot;
        this.tool = tool;
        this.materialSlots = Collections.unmodifiableList(new ArrayList<>(materialSlots));
    }

    public TinkersStationKind getStationKind() {
        return stationKind;
    }

    public int getWindowId() {
        return windowId;
    }

    public int getStationSlotCount() {
        return stationSlotCount;
    }

    public int getReservedContainerSlot() {
        return reservedContainerSlot;
    }

    public RepairToolSnapshot getTool() {
        return tool;
    }

    public List<ItemFingerprint> getMaterialSlots() {
        return materialSlots;
    }
}
