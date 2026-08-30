package io.github.kaseyawolf2.horizonwright.testfixtures;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import io.github.kaseyawolf2.horizonwright.testfixtures.CleanRoomCylinderGeometry.Column;
import io.github.kaseyawolf2.horizonwright.testfixtures.FakeInventory.ItemStack;
import io.github.kaseyawolf2.horizonwright.testfixtures.FixtureTable.Row;
import io.github.kaseyawolf2.horizonwright.testfixtures.GtProspectGrid.CellCenter;
import io.github.kaseyawolf2.horizonwright.testfixtures.OrdinaryCropSemantics.CropKind;
import io.github.kaseyawolf2.horizonwright.testfixtures.OrdinaryCropSemantics.HarvestAction;
import io.github.kaseyawolf2.horizonwright.testfixtures.PistonBootsCapabilityFixture.CapabilitySnapshot;

public class GoldenCharacterizationFixturesTest {

    @Test
    public void circleColumnsAndCylinderLayerOrderMatchTheGoldenFixture() {
        List<Column> expected = new ArrayList<>();
        for (Row row : FixtureTable.load("/fixtures/characterization/circle-cylinder-radius-2.tsv")) {
            expected.add(new Column(row.getInt("dx"), row.getInt("dz")));
        }

        assertEquals(expected, CleanRoomCylinderGeometry.columns(0, 0, 2));
        assertEquals(
            39,
            CleanRoomCylinderGeometry.blocks(10, -4, 2, 3, 5)
                .size());
        assertEquals(
            5,
            CleanRoomCylinderGeometry.blocks(10, -4, 2, 3, 5)
                .get(0)
                .getY());
        assertEquals(
            3,
            CleanRoomCylinderGeometry.blocks(10, -4, 2, 3, 5)
                .get(2)
                .getY());
    }

    @Test
    public void ordinaryCropMaturityAndActionMatchTheGoldenFixture() {
        for (Row row : FixtureTable.load("/fixtures/characterization/ordinary-crop-harvest.tsv")) {
            Boolean canGrow = "-".equals(row.get("can_grow")) ? null : row.getBoolean("can_grow");
            OrdinaryCropSemantics.Decision decision = OrdinaryCropSemantics
                .evaluate(CropKind.valueOf(row.get("kind")), row.getInt("metadata"), canGrow);

            assertEquals(row.get("case"), row.getBoolean("mature"), decision.isMature());
            assertEquals(row.get("case"), HarvestAction.valueOf(row.get("action")), decision.getAction());
        }
    }

    @Test
    public void gtProspectCellCentersUseThreeNPlusOneForNegativeAndPositiveCells() {
        for (Row row : FixtureTable.load("/fixtures/characterization/gt-prospect-3n-plus-1.tsv")) {
            CellCenter center = GtProspectGrid.cellCenter(row.getInt("cell_x"), row.getInt("cell_z"));

            assertEquals(row.getInt("chunk_x"), center.getChunkX());
            assertEquals(row.getInt("chunk_z"), center.getChunkZ());
            assertEquals(row.getInt("block_x"), center.getBlockX());
            assertEquals(row.getInt("block_z"), center.getBlockZ());
            assertEquals(0, Math.floorMod(center.getChunkX() - 1, 3));
            assertEquals(0, Math.floorMod(center.getChunkZ() - 1, 3));
        }
    }

    @Test
    public void storageCommitsOnlyAnExactServerConfirmation() {
        for (Row row : FixtureTable.load("/fixtures/characterization/storage-confirmation.tsv")) {
            FakeInventory inventory = new FakeInventory(3);
            inventory.setSlot(0, new ItemStack("minecraft:cobblestone", 0, 4, "plain-cobble"));
            FakeStorageTransaction transaction = FakeStorageTransaction.prepareMove(
                row.getInt("window"),
                row.getInt("action"),
                row.getLong("epoch"),
                inventory.snapshot(),
                0,
                1,
                2);

            FakeInventory confirmed = FakeInventory.fromSnapshot(transaction.getBefore());
            confirmed.move(0, 1, row.getInt("confirmed_destination_count"));
            transaction.confirm(
                new FakeStorageTransaction.Confirmation(
                    row.getInt("confirmed_window"),
                    row.getInt("confirmed_action"),
                    row.getLong("confirmed_epoch"),
                    confirmed.snapshot()));

            assertEquals(
                row.get("case"),
                FakeStorageTransaction.State.valueOf(row.get("expected_state")),
                transaction.getState());
        }
    }

    @Test
    public void pistonBootsCapabilitiesAndEquipmentInvalidationMatchTheGoldenFixture() {
        for (Row row : FixtureTable.load("/fixtures/characterization/piston-boots-capabilities.tsv")) {
            CapabilitySnapshot snapshot = PistonBootsCapabilityFixture.evaluate(
                row.getBoolean("equipped"),
                row.getInt("durability"),
                row.getInt("head_clearance"),
                row.getBoolean("landing_safe"),
                row.getBoolean("sprinting"),
                row.getLong("equipment_revision"));

            assertEquals(row.get("case"), row.getBoolean("auto_step"), snapshot.hasAutoStep());
            assertEquals(row.get("case"), row.getBoolean("high_jump"), snapshot.hasHighJumpEdge());
            assertEquals(row.get("case"), row.getBoolean("safe_fall"), snapshot.hasSafeFall());
            assertEquals(row.get("case"), row.getInt("extra_sprint_cost"), snapshot.getExtraSprintCostUnits());
            assertEquals(
                row.get("case"),
                row.getBoolean("valid"),
                snapshot
                    .isValidFor(row.getLong("current_revision"), row.getBoolean("equipped"), row.getInt("durability")));
        }
    }
}
