package io.github.kaseyawolf2.horizonwright.forge.client.inventory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemStack;

/** Session-local observations only. A cache entry is never authority for a container click. */
final class BagContentsCache {

    private final Map<String, Entry> entries = new LinkedHashMap<>();

    void observe(String identity, List<ItemStack> contents, int tick) {
        entries.remove(identity);
        if (entries.size() >= 128) entries.remove(
            entries.keySet()
                .iterator()
                .next());
        entries.put(identity, new Entry(contents, tick));
    }

    Entry get(String identity) {
        return entries.get(identity);
    }

    void invalidate(String identity) {
        entries.remove(identity);
    }

    void clear() {
        entries.clear();
    }

    static final class Entry {

        private final List<ItemStack> contents = new ArrayList<>();
        final int tick;

        Entry(List<ItemStack> values, int tick) {
            for (ItemStack value : values) if (value != null && value.stackSize > 0) contents.add(value.copy());
            this.tick = tick;
        }

        List<ItemStack> contents() {
            List<ItemStack> result = new ArrayList<>();
            for (ItemStack stack : contents) result.add(stack.copy());
            return result;
        }
    }
}
