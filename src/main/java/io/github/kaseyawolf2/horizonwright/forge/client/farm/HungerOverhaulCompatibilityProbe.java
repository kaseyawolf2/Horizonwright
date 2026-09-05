package io.github.kaseyawolf2.horizonwright.forge.client.farm;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.block.Block;
import net.minecraft.launchwrapper.Launch;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;
import io.github.kaseyawolf2.horizonwright.forge.client.farm.HungerOverhaulCompatibilityInspector.ArtifactEvidence;

/** Collects Forge source/config evidence without linking Horizonwright to Hunger Overhaul classes. */
final class HungerOverhaulCompatibilityProbe {

    private static final String CONFIG_CLASS = "iguanaman.hungeroverhaul.config.IguanaConfig";
    private static final String RIGHT_CLICK_FIELD = "enableRightClickHarvesting";
    private static final int HASH_BUFFER_SIZE = 8192;

    private HungerOverhaulCompatibilityProbe() {}

    static HungerOverhaulCompatibilityStatus inspect() {
        try {
            return new HungerOverhaulCompatibilityInspector().inspect(collect(), isDeobfuscatedEnvironment());
        } catch (IOException | RuntimeException | LinkageError failure) {
            String detail = failure.getMessage() == null ? failure.getClass()
                .getName() : failure.getMessage();
            return HungerOverhaulCompatibilityStatus
                .unavailable("Hunger Overhaul compatibility inspection failed: " + detail);
        }
    }

    static boolean rightClickHarvestingEnabled(Block block) {
        if (block == null) return false;
        try {
            Class<?> config = Class.forName(
                CONFIG_CLASS,
                false,
                block.getClass()
                    .getClassLoader());
            Field field = config.getField(RIGHT_CLICK_FIELD);
            if (field.getType() != Boolean.TYPE || !Modifier.isStatic(field.getModifiers())) {
                throw new IllegalStateException("Hunger Overhaul right-click flag has an unsupported shape");
            }
            return field.getBoolean(null);
        } catch (ClassNotFoundException | NoSuchFieldException | IllegalAccessException failure) {
            throw new IllegalStateException(
                "Hunger Overhaul right-click API does not match the tested adapter",
                failure);
        }
    }

    private static List<ArtifactEvidence> collect() throws IOException {
        List<ArtifactEvidence> result = new ArrayList<>();
        for (ModContainer container : Loader.instance()
            .getModList()) {
            if (!HungerOverhaulCompatibilityInspector.MOD_ID.equals(container.getModId())) continue;
            File source = container.getSource();
            if (source == null) {
                result.add(new ArtifactEvidence(container.getModId(), container.getVersion(), null, false));
            } else if (source.isDirectory()) {
                result.add(new ArtifactEvidence(container.getModId(), container.getVersion(), null, true));
            } else if (source.isFile()) {
                result.add(new ArtifactEvidence(container.getModId(), container.getVersion(), sha256(source), false));
            } else {
                result.add(new ArtifactEvidence(container.getModId(), container.getVersion(), null, false));
            }
        }
        return result;
    }

    private static String sha256(File source) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
        try (InputStream input = new FileInputStream(source)) {
            byte[] buffer = new byte[HASH_BUFFER_SIZE];
            int count;
            while ((count = input.read(buffer)) >= 0) {
                if (count > 0) digest.update(buffer, 0, count);
            }
        }
        StringBuilder value = new StringBuilder(64);
        for (byte item : digest.digest()) value.append(String.format(Locale.ROOT, "%02X", item & 0xff));
        return value.toString();
    }

    private static boolean isDeobfuscatedEnvironment() {
        return Boolean.TRUE.equals(Launch.blackboard.get("fml.deobfuscatedEnvironment"));
    }
}
