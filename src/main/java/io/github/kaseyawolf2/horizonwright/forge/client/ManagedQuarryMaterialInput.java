package io.github.kaseyawolf2.horizonwright.forge.client;

import net.minecraft.block.Block;
import net.minecraft.item.Item;

/** Client-side validation for operator-entered managed-quarry block registry names. */
final class ManagedQuarryMaterialInput {

    private ManagedQuarryMaterialInput() {}

    static String requirePlaceableBlock(String value, String field) {
        if (value == null || value.trim()
            .isEmpty()) throw new IllegalArgumentException(field + " is required");
        String requested = value.trim();
        Object registered = Block.blockRegistry.getObject(requested);
        if (!(registered instanceof Block)) {
            throw new IllegalArgumentException(field + " is not a registered block: " + requested);
        }
        Block block = (Block) registered;
        if (Item.getItemFromBlock(block) == null) {
            throw new IllegalArgumentException(field + " has no placeable inventory item: " + requested);
        }
        Object canonical = Block.blockRegistry.getNameForObject(block);
        if (!(canonical instanceof String) || ((String) canonical).trim()
            .isEmpty()) {
            throw new IllegalArgumentException(field + " has no stable registry name: " + requested);
        }
        return ((String) canonical).trim();
    }
}
