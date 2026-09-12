package io.github.kaseyawolf2.horizonwright.forge.client.inventory;

import static org.junit.Assert.*;

import java.util.Collections;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import org.junit.Test;

public class BagContentsCacheTest {

    @Test
    public void observationsAreCopiedBoundToOneBagAndInvalidatedOnRetirement() {
        BagContentsCache cache = new BagContentsCache();
        ItemStack cargo = new ItemStack(new Item(), 12);
        cache.observe("bag-one", Collections.singletonList(cargo), 20);
        cargo.stackSize = 1;
        assertEquals(
            12,
            cache.get("bag-one")
                .contents()
                .get(0).stackSize);
        cache.get("bag-one")
            .contents()
            .get(0).stackSize = 3;
        assertEquals(
            12,
            cache.get("bag-one")
                .contents()
                .get(0).stackSize);
        assertNull(cache.get("bag-two"));
        cache.clear();
        assertNull(cache.get("bag-one"));
    }
}
