package io.github.kaseyawolf2.horizonwright.forge.client.safety;

import net.minecraft.client.Minecraft;

/** Minecraft 1.7.10 client-thread verifier. */
public final class MinecraftClientThreadVerifier implements ClientThreadVerifier {

    @Override
    public boolean isClientThread() {
        return Minecraft.getMinecraft()
            .func_152345_ab();
    }
}
