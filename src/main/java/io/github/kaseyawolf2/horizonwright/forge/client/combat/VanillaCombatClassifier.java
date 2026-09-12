package io.github.kaseyawolf2.horizonwright.forge.client.combat;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntityBlaze;
import net.minecraft.entity.monster.EntityCaveSpider;
import net.minecraft.entity.monster.EntityCreeper;
import net.minecraft.entity.monster.EntityEnderman;
import net.minecraft.entity.monster.EntityGhast;
import net.minecraft.entity.monster.EntityGolem;
import net.minecraft.entity.monster.EntityMagmaCube;
import net.minecraft.entity.monster.EntityPigZombie;
import net.minecraft.entity.monster.EntitySilverfish;
import net.minecraft.entity.monster.EntitySkeleton;
import net.minecraft.entity.monster.EntitySlime;
import net.minecraft.entity.monster.EntitySpider;
import net.minecraft.entity.monster.EntityWitch;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.player.EntityPlayer;

import io.github.kaseyawolf2.horizonwright.core.combat.CombatTarget.Kind;

/** Exact vanilla hostile classes only: modded subclasses must supply their own eligibility evidence. */
public final class VanillaCombatClassifier {

    private static final Set<Class<?>> HOSTILES = new HashSet<>(
        Arrays.asList(
            EntityZombie.class,
            EntitySkeleton.class,
            EntityCreeper.class,
            EntitySilverfish.class,
            EntitySlime.class,
            EntityMagmaCube.class,
            EntityWitch.class,
            EntityBlaze.class,
            EntityGhast.class));

    private VanillaCombatClassifier() {}

    public static Kind classify(Class<? extends EntityLivingBase> type) {
        if (type == null) return Kind.UNKNOWN;
        if (EntityPlayer.class.isAssignableFrom(type)) return Kind.PLAYER;
        // Spider aggression depends on light; the client does not provide authoritative aggression evidence.
        if (EntityPigZombie.class.isAssignableFrom(type) || EntityEnderman.class.isAssignableFrom(type)
            || EntitySpider.class.isAssignableFrom(type)
            || EntityCaveSpider.class.isAssignableFrom(type)) return Kind.NEUTRAL;
        if (HOSTILES.contains(type)) return Kind.HOSTILE;
        if (EntityAnimal.class.isAssignableFrom(type) || EntityVillager.class.isAssignableFrom(type)
            || EntityGolem.class.isAssignableFrom(type)) return Kind.PASSIVE;
        return Kind.UNKNOWN;
    }
}
