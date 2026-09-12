package io.github.kaseyawolf2.horizonwright.navigation.baritone;

import static org.junit.Assert.*;

import java.nio.file.Path;
import java.util.Arrays;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import io.github.kaseyawolf2.horizonwright.core.excavation.BlockPosition;

public class ScaffoldLedgerTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void exactPositionsAndMetadataSurviveReloadUntilRemoved() {
        Path file = folder.getRoot()
            .toPath()
            .resolve("dimension-0.properties");
        BlockPosition pos = new BlockPosition(4, 80, -6);
        ScaffoldLedger original = new ScaffoldLedger();
        original.bind(file);
        original.record(pos, "minecraft:log:4");
        ScaffoldLedger reloaded = new ScaffoldLedger();
        reloaded.bind(file);
        assertTrue(reloaded.matches(pos, "minecraft:log:4"));
        assertFalse(reloaded.matches(pos, "minecraft:log:0"));
        assertFalse(reloaded.matches(new BlockPosition(4, 79, -6), "minecraft:log:4"));
        reloaded.remove(pos);
        original.bind(file);
        assertTrue(
            original.positions()
                .isEmpty());
    }

    @Test
    public void cleanupOrdersSupportsHighestFirstAndSeparatesWorlds() {
        ScaffoldLedger ledger = new ScaffoldLedger();
        Path a = folder.getRoot()
            .toPath()
            .resolve("profile-a/dimension-0.properties");
        Path b = folder.getRoot()
            .toPath()
            .resolve("profile-b/dimension-0.properties");
        BlockPosition low = new BlockPosition(0, 64, 0), high = new BlockPosition(0, 70, 0);
        ledger.bind(a);
        ledger.record(low, "minecraft:dirt:0");
        ledger.record(high, "minecraft:log:0");
        assertEquals(Arrays.asList(high, low), ledger.positions());
        ledger.bind(b);
        assertTrue(
            ledger.positions()
                .isEmpty());
        ledger.bind(a);
        assertEquals(
            2,
            ledger.positions()
                .size());
    }
}
