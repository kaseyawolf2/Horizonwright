package io.github.kaseyawolf2.horizonwright.forge.client;

import static org.junit.Assert.*;

import org.junit.Test;

public class FreecamCompatibilityTest {

    @Test
    public void cameraOnlyControlExemptsMovementButPlayerControlDoesNot() {
        assertTrue(FreecamCompatibility.cameraOnly(true, false));
        assertFalse(FreecamCompatibility.cameraOnly(true, true));
        assertFalse(FreecamCompatibility.cameraOnly(false, false));
        assertFalse(FreecamCompatibility.cameraOnly(false, true));
    }
}
