package io.github.kaseyawolf2.horizonwright.forge.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;

import io.github.kaseyawolf2.horizonwright.core.action.ActionRevocation;
import io.github.kaseyawolf2.horizonwright.core.action.ActionRevocationListener;
import io.github.kaseyawolf2.horizonwright.core.action.ActionRevocationReason;

public final class ClientInputArbiter implements ActionRevocationListener {

    @Override
    public void onActionEpochRevoked(ActionRevocation revocation) {
        if (revocation.getReason() != ActionRevocationReason.SAFETY_LOCKDOWN) {
            return;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        Runnable releaseInputs = KeyBinding::unPressAllKeys;
        if (minecraft.func_152345_ab()) {
            releaseInputs.run();
        } else {
            minecraft.func_152344_a(releaseInputs);
        }
    }
}
