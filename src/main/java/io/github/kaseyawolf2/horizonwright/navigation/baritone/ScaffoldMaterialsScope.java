package io.github.kaseyawolf2.horizonwright.navigation.baritone;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.Item;

final class ScaffoldMaterialsScope {

    private List<Item> previous, installed;

    List<Item> begin(List<Item> original, List<Item> materials) {
        if (installed != null) throw new IllegalStateException("Scaffold materials already active");
        previous = original;
        installed = new ArrayList<>(materials);
        return installed;
    }

    List<Item> end(List<Item> current) {
        List<Item> restored = current == installed ? previous : current;
        previous = installed = null;
        return restored;
    }
}
