package io.github.kaseyawolf2.horizonwright.forge.client.farm;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class MinecraftVanillaTreeObserverTest {

    @Test
    public void vanillaSpeciesRoundTripThroughExactSaplingIdentity() {
        for (int species = 0; species <= 5; species++) {
            String fingerprint = MinecraftVanillaTreeObserver.saplingFingerprint(species);
            assertEquals(species, MinecraftVanillaTreeObserver.saplingSpecies(fingerprint));
        }
        assertEquals("minecraft:sapling|meta=4|nbt=none", MinecraftVanillaTreeObserver.saplingFingerprint(4));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsUnknownSaplingIdentity() {
        MinecraftVanillaTreeObserver.saplingSpecies("some-mod:unknown");
    }
}
