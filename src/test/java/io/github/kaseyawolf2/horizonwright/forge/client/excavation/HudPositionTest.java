package io.github.kaseyawolf2.horizonwright.forge.client.excavation;

import static org.junit.Assert.*;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

public class HudPositionTest {

    @Test
    public void dragClampsToVisibleScreenAndSurvivesResize() {
        HudPosition edge = HudPosition.at(1000, -30, 800, 600, 280, 80);
        assertEquals(520, edge.x(800, 280));
        assertEquals(0, edge.y(600, 80));
        assertEquals(120, edge.x(400, 280));
        assertEquals(0, edge.x(200, 280));
        HudPosition center = HudPosition.at(260, 260, 800, 600, 280, 80);
        assertEquals(60, center.x(400, 280));
        assertEquals(110, center.y(300, 80));
    }

    @Test
    public void positionRoundTripsAndReplacementLeavesNoTemporaryFiles() throws Exception {
        Path directory = Files.createTempDirectory("hud-position-test");
        Path file = directory.resolve("hud.properties");
        try {
            assertSame(HudPosition.DEFAULT, HudPosition.load(file));
            new HudPosition(0.7, 0.2).save(file);
            assertEquals(0.7, HudPosition.load(file).horizontal, 0.00001);
            new HudPosition(0.1, 0.8).save(file);
            assertEquals(0.8, HudPosition.load(file).vertical, 0.00001);
            try (java.util.stream.Stream<Path> files = Files.list(directory)) {
                assertEquals(1, files.count());
            }
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(directory);
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void nonFinitePositionIsRejected() {
        new HudPosition(Double.NaN, 0);
    }
}
