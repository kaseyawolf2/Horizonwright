package io.github.kaseyawolf2.horizonwright.forge.client.farm;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.launchwrapper.Launch;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;
import io.github.kaseyawolf2.horizonwright.forge.client.farm.PamHarvestCraftCompatibilityInspector.ArtifactEvidence;

/** Collects Forge source evidence without resolving a HarvestCraft implementation class. */
final class PamHarvestCraftCompatibilityProbe {

    private static final int HASH_BUFFER_SIZE = 8192;

    private PamHarvestCraftCompatibilityProbe() {}

    static PamHarvestCraftCompatibilityStatus inspect() {
        try {
            return new PamHarvestCraftCompatibilityInspector().inspect(collect(), isDeobfuscatedEnvironment());
        } catch (IOException | RuntimeException | LinkageError failure) {
            String detail = failure.getMessage() == null ? failure.getClass()
                .getName() : failure.getMessage();
            return PamHarvestCraftCompatibilityStatus
                .unavailable("HarvestCraft compatibility inspection failed: " + detail);
        }
    }

    private static List<ArtifactEvidence> collect() throws IOException {
        List<ArtifactEvidence> result = new ArrayList<>();
        for (ModContainer container : Loader.instance()
            .getModList()) {
            if (!PamHarvestCraftCompatibilityInspector.MOD_ID.equals(container.getModId())) continue;
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
