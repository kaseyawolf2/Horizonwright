package io.github.kaseyawolf2.horizonwright.forge.client.husbandry;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;

import io.github.kaseyawolf2.horizonwright.DevelopmentTrace;
import io.github.kaseyawolf2.horizonwright.core.base.AnimalObservation;
import io.github.kaseyawolf2.horizonwright.core.base.BasePosition;
import io.github.kaseyawolf2.horizonwright.core.base.HusbandryDropObservation;
import io.github.kaseyawolf2.horizonwright.core.base.HusbandryObservation;
import io.github.kaseyawolf2.horizonwright.core.base.NamedArea;
import io.github.kaseyawolf2.horizonwright.forge.client.MinecraftRuntimeAccess;
import io.github.kaseyawolf2.horizonwright.forge.client.container.MinecraftContainerSnapshotter;

/** Complete, bounded, read-only observation of exact vanilla livestock in one loaded named pen. */
public final class MinecraftHusbandryObserver {

    private final Minecraft minecraft;
    private final ProfileHusbandryConfiguration configuration;
    private final VanillaLivestockClassifier classifier = new VanillaLivestockClassifier();
    private final MinecraftContainerSnapshotter items = new MinecraftContainerSnapshotter();
    private long revision;

    public MinecraftHusbandryObserver(Minecraft minecraft, ProfileHusbandryConfiguration configuration) {
        if (minecraft == null || configuration == null) {
            throw new IllegalArgumentException("minecraft and husbandry configuration are required");
        }
        this.minecraft = minecraft;
        this.configuration = configuration;
    }

    public HusbandryObservation observe(String penId) {
        requireClient();
        NamedArea pen = configuration.resolve(penId);
        requireCurrentDimension(pen);
        requireBoundedLoadedArea(pen);
        AxisAlignedBB bounds = observationBounds(pen);
        List<AnimalObservation> animals = observeAnimals(pen, bounds);
        List<HusbandryDropObservation> drops = observeDrops(pen, bounds);
        long currentRevision = ++revision;
        HusbandryObservation observation = new HusbandryObservation(
            pen,
            currentRevision,
            "sha256:" + sha256(fingerprint(pen, animals, drops)),
            animals,
            drops,
            true,
            true);
        DevelopmentTrace.event(
            "husbandry-observer",
            "complete-pen-scan",
            "pen",
            pen.getId(),
            "revision",
            currentRevision,
            "fingerprint",
            observation.getObservationFingerprint(),
            "animals",
            animals.size(),
            "drops",
            drops.size());
        return observation;
    }

    public int findBreedingItemHotbarSlot(VanillaLivestockClassifier.Descriptor descriptor) {
        requireClient();
        if (descriptor == null) throw new IllegalArgumentException("livestock descriptor is required");
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = minecraft.thePlayer.inventory.mainInventory[slot];
            Object registryName = stack == null ? null : Item.itemRegistry.getNameForObject(stack.getItem());
            if (registryName != null && descriptor.getBreedingItemId()
                .equals(registryName.toString())) return slot;
        }
        return -1;
    }

    public EntityAnimal findSupportedAnimal(String identity) {
        requireClient();
        if (identity == null) return null;
        for (Object candidate : minecraft.theWorld.loadedEntityList) {
            if (candidate instanceof EntityAnimal) {
                EntityAnimal animal = (EntityAnimal) candidate;
                if (!animal.isDead && descriptor(animal) != null && identity.equals(identity(animal))) return animal;
            }
        }
        return null;
    }

    public EntityItem findDrop(String identity) {
        requireClient();
        if (identity == null) return null;
        for (Object candidate : minecraft.theWorld.loadedEntityList) {
            if (candidate instanceof EntityItem) {
                EntityItem drop = (EntityItem) candidate;
                if (!drop.isDead && identity.equals(identity(drop))) return drop;
            }
        }
        return null;
    }

    public VanillaLivestockClassifier.Descriptor descriptor(Entity entity) {
        return entity == null ? null
            : classifier.classify(
                entity.getClass()
                    .getName());
    }

    private List<AnimalObservation> observeAnimals(NamedArea pen, AxisAlignedBB bounds) {
        @SuppressWarnings("unchecked")
        List<EntityAnimal> candidates = MinecraftRuntimeAccess
            .getEntitiesWithinAabb(minecraft.theWorld, EntityAnimal.class, bounds);
        List<AnimalObservation> animals = new ArrayList<>();
        for (EntityAnimal entity : candidates) {
            VanillaLivestockClassifier.Descriptor descriptor = descriptor(entity);
            if (descriptor == null || entity.isDead) continue;
            boolean adult = !entity.isChild();
            boolean breedingEngaged = adult && entity.isInLove();
            boolean readyToBreed = adult && !breedingEngaged && entity.getGrowingAge() == 0;
            AnimalObservation observation = new AnimalObservation(
                identity(entity),
                descriptor.getSpecies(),
                policyPosition(pen, entity),
                adult,
                entity.hasCustomNameTag(),
                false,
                false,
                readyToBreed,
                breedingEngaged);
            animals.add(observation);
            DevelopmentTrace.event(
                "husbandry-observer",
                "animal",
                "identity",
                observation.getIdentity(),
                "species",
                observation.getSpecies(),
                "position",
                observation.getPosition(),
                "adult",
                observation.isAdult(),
                "named",
                observation.isNamed(),
                "ready",
                observation.isReadyToBreed(),
                "engaged",
                observation.isBreedingEngaged());
        }
        animals.sort(Comparator.comparing(AnimalObservation::getIdentity));
        if (animals.size() > HusbandryObservation.MAX_ANIMAL_CANDIDATES) {
            throw new IllegalStateException("named pen exceeds the 512-animal observation bound");
        }
        return animals;
    }

    private List<HusbandryDropObservation> observeDrops(NamedArea pen, AxisAlignedBB bounds) {
        @SuppressWarnings("unchecked")
        List<EntityItem> candidates = MinecraftRuntimeAccess
            .getEntitiesWithinAabb(minecraft.theWorld, EntityItem.class, bounds);
        List<HusbandryDropObservation> drops = new ArrayList<>();
        for (EntityItem entity : candidates) {
            ItemStack stack = entity.getEntityItem();
            io.github.kaseyawolf2.horizonwright.core.container.ItemFingerprint item = entity.isDead || stack == null
                ? null
                : items.fingerprint(stack);
            if (item == null) continue;
            HusbandryDropObservation observation = new HusbandryDropObservation(
                identity(entity),
                item.toString(),
                policyPosition(pen, entity));
            drops.add(observation);
            DevelopmentTrace.event(
                "husbandry-observer",
                "drop",
                "identity",
                observation.getIdentity(),
                "item",
                observation.getItemFingerprint(),
                "position",
                observation.getPosition());
        }
        drops.sort(Comparator.comparing(HusbandryDropObservation::getIdentity));
        if (drops.size() > HusbandryObservation.MAX_DROP_CANDIDATES) {
            throw new IllegalStateException("named pen exceeds the 256-drop observation bound");
        }
        return drops;
    }

    private void requireClient() {
        if (!minecraft.func_152345_ab() || minecraft.thePlayer == null
            || minecraft.theWorld == null
            || minecraft.theWorld.provider == null) {
            throw new IllegalStateException("a joined Minecraft client thread is required for husbandry observation");
        }
    }

    private void requireCurrentDimension(NamedArea pen) {
        if (pen.getMinimum()
            .getDimensionId() != minecraft.theWorld.provider.dimensionId) {
            throw new IllegalStateException("named livestock pen is in another dimension");
        }
    }

    private void requireBoundedLoadedArea(NamedArea pen) {
        if (HusbandryPenGeometry.observationVolume(pen) > HusbandryPenGeometry.MAX_OBSERVATION_VOLUME) {
            throw new IllegalStateException("named livestock pen exceeds the 65,536-block observation bound");
        }
        BasePosition minimum = pen.getMinimum();
        BasePosition maximum = pen.getMaximum();
        for (int chunkX = minimum.getX() >> 4; chunkX <= maximum.getX() >> 4; chunkX++) {
            for (int chunkZ = minimum.getZ() >> 4; chunkZ <= maximum.getZ() >> 4; chunkZ++) {
                if (!MinecraftRuntimeAccess.chunkProvider(minecraft.theWorld)
                    .chunkExists(chunkX, chunkZ)) {
                    throw new IllegalStateException("every chunk in the named livestock pen must be loaded");
                }
            }
        }
    }

    private static AxisAlignedBB observationBounds(NamedArea pen) {
        BasePosition minimum = pen.getMinimum();
        BasePosition maximum = pen.getMaximum();
        return AxisAlignedBB.getBoundingBox(
            minimum.getX(),
            minimum.getY(),
            minimum.getZ(),
            maximum.getX() + 1.0D,
            maximum.getY() + 3.0D,
            maximum.getZ() + 1.0D);
    }

    private BasePosition policyPosition(NamedArea pen, Entity entity) {
        return HusbandryPenGeometry
            .policyPosition(pen, minecraft.theWorld.provider.dimensionId, entity.posX, entity.posY, entity.posZ);
    }

    private static String identity(Entity entity) {
        return MinecraftRuntimeAccess.uniqueId(entity)
            .toString();
    }

    private static String fingerprint(NamedArea pen, List<AnimalObservation> animals,
        List<HusbandryDropObservation> drops) {
        StringBuilder value = new StringBuilder(pen.toString());
        for (AnimalObservation animal : animals) {
            value.append("|animal:")
                .append(animal.getIdentity())
                .append(':')
                .append(animal.getSpecies())
                .append(':')
                .append(animal.getPosition())
                .append(':')
                .append(animal.isAdult())
                .append(':')
                .append(animal.isNamed())
                .append(':')
                .append(animal.isReadyToBreed())
                .append(':')
                .append(animal.isBreedingEngaged());
        }
        for (HusbandryDropObservation drop : drops) {
            value.append("|drop:")
                .append(drop.getIdentity())
                .append(':')
                .append(drop.getItemFingerprint())
                .append(':')
                .append(drop.getPosition());
        }
        return value.toString();
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder encoded = new StringBuilder(digest.length * 2);
            for (byte current : digest) encoded.append(String.format("%02x", current & 0xff));
            return encoded.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
