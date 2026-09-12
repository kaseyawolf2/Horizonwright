package io.github.kaseyawolf2.horizonwright.forge.client.combat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntityCreeper;
import net.minecraft.entity.passive.EntityHorse;
import net.minecraft.entity.passive.EntityTameable;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;

import io.github.kaseyawolf2.horizonwright.core.combat.CombatReadiness;
import io.github.kaseyawolf2.horizonwright.core.combat.CombatTarget;
import io.github.kaseyawolf2.horizonwright.core.combat.CombatTarget.Protection;
import io.github.kaseyawolf2.horizonwright.core.combat.CombatTargetPolicy;
import io.github.kaseyawolf2.horizonwright.core.combat.CombatThreatController;
import io.github.kaseyawolf2.horizonwright.core.persistence.ProtectedLivestock;
import io.github.kaseyawolf2.horizonwright.forge.client.MinecraftRuntimeAccess;
import io.github.kaseyawolf2.horizonwright.forge.client.ToolCapabilities;
import io.github.kaseyawolf2.horizonwright.runtime.persistence.profile.ProfileAssetEditorProvider;

/** On-demand diagnostics only. Does not acquire action capabilities, move, select a slot, or send attacks. */
public final class MinecraftCombatObserver {

    private final Minecraft minecraft;
    private final ProfileAssetEditorProvider profiles;

    public MinecraftCombatObserver(Minecraft minecraft, ProfileAssetEditorProvider profiles) {
        if (minecraft == null || profiles == null) throw new IllegalArgumentException("client and profiles required");
        this.minecraft = minecraft;
        this.profiles = profiles;
    }

    public List<String> describe(int radius) {
        if (radius < 1 || radius > 32) throw new IllegalArgumentException("scan radius must be 1..32 blocks");
        if (minecraft.theWorld == null || minecraft.thePlayer == null)
            throw new IllegalStateException("join a world first");
        EntityPlayer player = minecraft.thePlayer;
        EntityLivingBase livingPlayer = player;
        Set<String> protectedStock = new HashSet<>();
        for (ProtectedLivestock stock : profiles.getCurrentProfileAssetEditor()
            .orElseThrow(() -> new IllegalStateException("active profile assets are unavailable"))
            .load()
            .getProtectedLivestock()) {
            protectedStock.add(stock.getEntityIdentity());
        }
        List<CombatTarget> targets = new ArrayList<>();
        boolean explosion = false;
        int hostiles = 0;
        for (Object candidate : minecraft.theWorld.loadedEntityList) {
            if (!(candidate instanceof EntityLivingBase) || candidate == minecraft.thePlayer) continue;
            EntityLivingBase entity = (EntityLivingBase) candidate;
            double dx = player.posX - entity.posX;
            double dy = player.posY - entity.posY;
            double dz = player.posZ - entity.posZ;
            double distance = dx * dx + dy * dy + dz * dz;
            if (distance > radius * radius) continue;
            EnumSet<Protection> protections = EnumSet.noneOf(Protection.class);
            if (entity instanceof EntityLiving && ((EntityLiving) entity).hasCustomNameTag())
                protections.add(Protection.NAMED);
            if (entity.isChild()) protections.add(Protection.CHILD);
            if (livingPlayer.isOnSameTeam(entity)) protections.add(Protection.FRIENDLY);
            if (entity instanceof EntityTameable && ((EntityTameable) entity).isTamed()
                || entity instanceof EntityHorse && ((EntityHorse) entity).isTame()) protections.add(Protection.TAMED);
            String identity = MinecraftRuntimeAccess.uniqueId(entity)
                .toString();
            if (protectedStock.contains(identity)) protections.add(Protection.HUSBANDRY);
            CombatTarget target = new CombatTarget(
                identity,
                VanillaCombatClassifier.classify(entity.getClass()),
                protections,
                entity.isEntityAlive(),
                livingPlayer.canEntityBeSeen(entity),
                distance);
            targets.add(target);
            // Protected and occluded hostiles still count as threats, even though they cannot be attacked.
            if (target.isAlive() && target.getKind() == CombatTarget.Kind.HOSTILE) hostiles++;
            if (target.isAlive() && entity instanceof EntityCreeper
                && distance <= 36
                && ((EntityCreeper) entity).getCreeperState() > 0) explosion = true;
        }
        CombatTargetPolicy policy = new CombatTargetPolicy(radius);
        Map<CombatTargetPolicy.Verdict, Integer> counts = new EnumMap<>(CombatTargetPolicy.Verdict.class);
        for (CombatTarget target : targets) counts.merge(policy.evaluate(target), 1, Integer::sum);
        ItemStack held = MinecraftRuntimeAccess.heldItem(player);
        boolean supportedWeapon = held != null && held.getItem()
            .getClass() == ItemSword.class && ToolCapabilities.usable(held) && held.getMaxDamage() > 0;
        double durability = supportedWeapon
            ? Math.max(0, Math.min(1, (held.getMaxDamage() - (double) held.getItemDamage()) / held.getMaxDamage()))
            : 0;
        float health = MinecraftRuntimeAccess.health(player);
        float maximumHealth = MinecraftRuntimeAccess.maximumHealth(player);
        boolean healthKnown = Float.isFinite(health) && Float.isFinite(maximumHealth) && maximumHealth > 0;
        CombatReadiness readiness = new CombatReadiness(
            healthKnown,
            true,
            true,
            healthKnown ? Math.max(0, Math.min(1, (double) health / maximumHealth)) : 0,
            player.getTotalArmorValue(),
            player.getFoodStats()
                .getFoodLevel(),
            durability,
            0,
            false,
            explosion);
        CombatThreatController.Result advice = CombatThreatController.defaults()
            .evaluate(readiness, hostiles > 0 || explosion);
        List<String> lines = new ArrayList<>();
        lines.add(
            "Read-only combat scan: " + radius
                + " blocks, "
                + targets.size()
                + " loaded living entities; "
                + hostiles
                + " known hostiles.");
        lines.add("Target decisions: " + counts + ". Unknown and neutral entities are excluded.");
        lines.add(
            "Resource advice: " + advice
                .getDecision() + " (" + advice.getReason() + "); held vanilla sword: " + supportedWeapon + ".");
        lines.add("Defaults: retreat at <=50% health, recover at 70%; armor >=4, food >=6, weapon durability >10%.");
        lines.add("Diagnostic only: no attack/movement authority checked or granted. No combat automation is running.");
        targets.sort(
            Comparator.comparingDouble(CombatTarget::getDistanceSquared)
                .thenComparing(CombatTarget::getIdentity));
        policy.select(targets, null)
            .ifPresent(
                target -> lines.add("Candidate: " + target.getIdentity() + " (observation range, not melee reach)."));
        for (int i = 0; i < Math.min(8, targets.size()); i++) {
            CombatTarget target = targets.get(i);
            lines.add(
                String.format(
                    Locale.ROOT,
                    "%s %s %.1f blocks: %s %s",
                    target.getIdentity(),
                    target.getKind(),
                    Math.sqrt(target.getDistanceSquared()),
                    policy.evaluate(target),
                    target.getProtections()));
        }
        if (targets.size() > 8) lines.add("Showing nearest 8; all " + targets.size() + " counted above.");
        return lines;
    }
}
