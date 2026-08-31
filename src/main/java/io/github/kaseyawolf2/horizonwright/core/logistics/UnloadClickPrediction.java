package io.github.kaseyawolf2.horizonwright.core.logistics;

import io.github.kaseyawolf2.horizonwright.core.container.VerifiedContainerClick;

/** Explicit container-layout adapter prediction for one approved player slot. */
public final class UnloadClickPrediction {

    private final int playerSlot;
    private final VerifiedContainerClick click;

    public UnloadClickPrediction(int playerSlot, VerifiedContainerClick click) {
        if (playerSlot < 0 || click == null) {
            throw new IllegalArgumentException("playerSlot and click are required");
        }
        this.playerSlot = playerSlot;
        this.click = click;
    }

    public int getPlayerSlot() {
        return playerSlot;
    }

    public VerifiedContainerClick getClick() {
        return click;
    }
}
