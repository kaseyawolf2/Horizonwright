package io.github.kaseyawolf2.horizonwright.forge.client.safety;

/** Verifies that a snapshot is being captured from the Minecraft client thread. */
public interface ClientThreadVerifier {

    boolean isClientThread();
}
