package io.github.kaseyawolf2.horizonwright.forge.client.safety;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import net.minecraft.tileentity.TileEntity;

import org.junit.Test;

import io.github.kaseyawolf2.horizonwright.core.safety.death.DimensionBlockPosition;

public class OpenBlocksGraveTileReaderTest {

    private static final DimensionBlockPosition POSITION = new DimensionBlockPosition(7, 12, 64, -3);

    @Test
    public void exactSupportedTileExposesOnlySynchronizedEvidence() {
        FakeGraveTile tile = new FakeGraveTile("Kasey", false);
        OpenBlocksGraveTileEvidence evidence = new OpenBlocksGraveTileReader(FakeGraveTile.class.getName())
            .read(tile, POSITION)
            .get();

        assertEquals("Kasey", evidence.getOwnerUsername());
        assertFalse(evidence.isInventoryEmpty());
        assertEquals(
            POSITION,
            evidence.getIdentity()
                .getPosition());
        assertEquals(
            "openblocks-grave-v1:5:Kasey",
            evidence.getIdentity()
                .getTileIdentity());
    }

    @Test
    public void unrelatedTilePassesThroughAsNotOpenBlocksEvidence() {
        assertFalse(
            new OpenBlocksGraveTileReader(FakeGraveTile.class.getName()).read(new TileEntity(), POSITION)
                .isPresent());
    }

    @Test(expected = IllegalStateException.class)
    public void malformedSupportedTileFailsInsteadOfGuessing() {
        new OpenBlocksGraveTileReader(MalformedGraveTile.class.getName()).read(new MalformedGraveTile(), POSITION);
    }

    public static final class FakeGraveTile extends TileEntity {

        private final String username;
        private final boolean empty;

        FakeGraveTile(String username, boolean empty) {
            this.username = username;
            this.empty = empty;
        }

        public String getUsername() {
            return username;
        }

        public boolean isInventoryEmpty() {
            return empty;
        }
    }

    public static final class MalformedGraveTile extends TileEntity {

        public String getUsername() {
            return "Kasey";
        }

        public String isInventoryEmpty() {
            return "no";
        }
    }
}
