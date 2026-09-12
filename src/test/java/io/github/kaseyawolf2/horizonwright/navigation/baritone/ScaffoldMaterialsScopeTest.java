package io.github.kaseyawolf2.horizonwright.navigation.baritone;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.Item;

import org.junit.Test;

public class ScaffoldMaterialsScopeTest {

    @Test
    public void restoresOriginalSettingAfterTreeNavigation() {
        ScaffoldMaterialsScope scope = new ScaffoldMaterialsScope();
        List<Item> previous = new ArrayList<>(), supplies = new ArrayList<>();
        supplies.add(new Item());
        List<Item> installed = scope.begin(previous, supplies);
        assertNotSame(supplies, installed);
        assertEquals(supplies, installed);
        assertSame(previous, scope.end(installed));
    }

    @Test
    public void doesNotOverwriteExternalSettingChange() {
        ScaffoldMaterialsScope scope = new ScaffoldMaterialsScope();
        scope.begin(new ArrayList<>(), new ArrayList<>());
        List<Item> changed = new ArrayList<>();
        assertSame(changed, scope.end(changed));
    }
}
