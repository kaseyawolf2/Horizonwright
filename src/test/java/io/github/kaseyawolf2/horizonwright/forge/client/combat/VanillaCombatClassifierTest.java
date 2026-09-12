package io.github.kaseyawolf2.horizonwright.forge.client.combat;

import static org.junit.Assert.*;

import net.minecraft.entity.monster.EntityCreeper;
import net.minecraft.entity.monster.EntityEnderman;
import net.minecraft.entity.monster.EntityPigZombie;
import net.minecraft.entity.monster.EntitySpider;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.passive.EntityCow;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

import org.junit.Test;

import io.github.kaseyawolf2.horizonwright.core.combat.CombatTarget.Kind;

public class VanillaCombatClassifierTest {

    @Test
    public void recognizesExactHostilesWithoutProvokingNeutralMobs() {
        assertEquals(Kind.HOSTILE, VanillaCombatClassifier.classify(EntityZombie.class));
        assertEquals(Kind.HOSTILE, VanillaCombatClassifier.classify(EntityCreeper.class));
        assertEquals(Kind.NEUTRAL, VanillaCombatClassifier.classify(EntityPigZombie.class));
        assertEquals(Kind.NEUTRAL, VanillaCombatClassifier.classify(EntityEnderman.class));
        assertEquals(Kind.NEUTRAL, VanillaCombatClassifier.classify(EntitySpider.class));
        assertEquals(Kind.PASSIVE, VanillaCombatClassifier.classify(EntityCow.class));
        assertEquals(Kind.PLAYER, VanillaCombatClassifier.classify(EntityPlayer.class));
    }

    @Test
    public void moddedHostileSubclassesNeedAnExplicitAdapter() {
        assertEquals(Kind.UNKNOWN, VanillaCombatClassifier.classify(UnverifiedZombie.class));
        assertEquals(Kind.UNKNOWN, VanillaCombatClassifier.classify(null));
    }

    private static class UnverifiedZombie extends EntityZombie {

        UnverifiedZombie(World world) {
            super(world);
        }
    }
}
