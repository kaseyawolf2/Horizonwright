package io.github.kaseyawolf2.horizonwright.forge.client;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import net.minecraft.block.material.Material;
import net.minecraft.util.AxisAlignedBB;

import org.junit.Test;

public class ScaffoldDescentSafetyTest {

    @Test
    public void fullCollisionSupportsLandingRegardlessOfRenderingOpacity() {
        assertTrue(
            ScaffoldDescentSafety.supportsLanding(
                net.minecraft.util.AxisAlignedBB.getBoundingBox(182, 67, 362, 183, 68, 363),
                182,
                67,
                362));
        assertFalse(ScaffoldDescentSafety.supportsLanding(null, 182, 67, 362));
        assertFalse(
            ScaffoldDescentSafety.supportsLanding(
                net.minecraft.util.AxisAlignedBB.getBoundingBox(182, 67, 362, 183, 67.5, 363),
                182,
                67,
                362));
    }

    @Test
    public void acceptsOneBlockDescentOntoNextPillarBlock() {
        assertTrue(ScaffoldDescentSafety.safeLanding(69, 70, y -> false, y -> y == 68));
    }

    @Test
    public void acceptsShortAirGapWhereStandingInsideRemovedBlockIsImpossible() {
        assertTrue(ScaffoldDescentSafety.safeLanding(69, 70, y -> y == 68, y -> y == 67));
    }

    @Test
    public void rejectsUnsafeDropAndUnknownFloor() {
        assertFalse(ScaffoldDescentSafety.safeLanding(69, 70, y -> true, y -> y == 65));
        assertFalse(ScaffoldDescentSafety.safeLanding(69, 70, y -> true, y -> false));
    }

    @Test
    public void rejectsHazardBeforeSafeFloor() {
        assertFalse(ScaffoldDescentSafety.safeLanding(69, 70, y -> false, y -> y == 67));
    }

    @Test
    public void loggedPillarOverTallGrassAllowsTwoBlockDescent() {
        // Physical stall: log (168,67,335), tall grass Y=66, grass floor Y=65; feet Y=68.
        assertTrue(
            ScaffoldDescentSafety.safeLanding(
                67,
                68,
                y -> y == 66 && ScaffoldDescentSafety.passableGap(Material.plants, null, false),
                y -> y == 65));
    }

    @Test
    public void harmlessPlantsStillRequireSafeFloorWithinDropLimit() {
        assertFalse(
            ScaffoldDescentSafety.safeLanding(
                67,
                68,
                y -> ScaffoldDescentSafety.passableGap(Material.plants, null, false),
                y -> y == 63));
        assertFalse(
            ScaffoldDescentSafety
                .safeLanding(67, 68, y -> ScaffoldDescentSafety.passableGap(Material.plants, null, false), y -> false));
    }

    @Test
    public void thornsCannotBeCrossedEvenAboveSafeGround() {
        assertFalse(
            ScaffoldDescentSafety.safeLanding(
                67,
                68,
                y -> ScaffoldDescentSafety.passableGap(Material.plants, null, true),
                y -> y == 65));
    }

    @Test
    public void onlyKnownHarmlessCollisionFreeGapsAreAccepted() {
        assertTrue(ScaffoldDescentSafety.passableGap(Material.air, null, false));
        assertTrue(ScaffoldDescentSafety.passableGap(Material.vine, null, false));
        assertFalse(
            ScaffoldDescentSafety
                .passableGap(Material.plants, AxisAlignedBB.getBoundingBox(168, 66, 335, 169, 67, 336), false));
        for (Material material : new Material[] { Material.water, Material.lava, Material.fire, Material.web,
            Material.portal, Material.sand, Material.rock }) {
            assertFalse(ScaffoldDescentSafety.passableGap(material, null, false));
        }
        assertFalse(ScaffoldDescentSafety.passableGap(Material.sand, null, true));
    }

}
