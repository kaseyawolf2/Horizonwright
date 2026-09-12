package io.github.kaseyawolf2.horizonwright.forge.client.farm;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Pure validation of the separately installed HarvestCraft artifact observed for this adapter. */
final class PamHarvestCraftCompatibilityInspector {

    static final String MOD_ID = "harvestcraft";
    static final String VERSION = "1.3.14-GTNH";
    static final String SHA256 = "6F605B9BBF03122BF8700A3332E3C56BFD5D0472E8D4E03C04841064E5BA9A01";

    PamHarvestCraftCompatibilityStatus inspect(List<ArtifactEvidence> evidence, boolean deobfuscatedEnvironment) {
        if (evidence == null || evidence.contains(null)) {
            throw new IllegalArgumentException("HarvestCraft artifact evidence must not be null or contain null");
        }
        List<ArtifactEvidence> matches = new ArrayList<>();
        for (ArtifactEvidence candidate : evidence) {
            if (MOD_ID.equals(candidate.getModId())) matches.add(candidate);
        }
        if (matches.size() != 1) {
            return PamHarvestCraftCompatibilityStatus
                .unavailable("Expected exactly one loaded HarvestCraft artifact but found " + matches.size());
        }
        ArtifactEvidence actual = matches.get(0);
        if (!VERSION.equals(actual.getVersion())) {
            return PamHarvestCraftCompatibilityStatus.unavailable(
                "Unsupported HarvestCraft version '" + actual.getVersion() + "'; expected '" + VERSION + "'");
        }
        if (actual.isDirectory()) {
            return deobfuscatedEnvironment
                ? PamHarvestCraftCompatibilityStatus
                    .available(false, "HarvestCraft development directory passed version validation")
                : PamHarvestCraftCompatibilityStatus
                    .unavailable("HarvestCraft source is a directory outside a deobfuscated environment");
        }
        if (actual.getSha256() == null) {
            return PamHarvestCraftCompatibilityStatus.unavailable("HarvestCraft source bytes could not be hashed");
        }
        if (!SHA256.equals(actual.getSha256())) {
            return deobfuscatedEnvironment
                ? PamHarvestCraftCompatibilityStatus
                    .available(false, "HarvestCraft development bytes passed version validation")
                : PamHarvestCraftCompatibilityStatus
                    .unavailable("Unsupported HarvestCraft SHA-256 " + actual.getSha256() + "; expected " + SHA256);
        }
        return PamHarvestCraftCompatibilityStatus
            .available(true, "HarvestCraft " + VERSION + " passed exact-byte validation");
    }

    static final class ArtifactEvidence {

        private final String modId;
        private final String version;
        private final String sha256;
        private final boolean directory;

        ArtifactEvidence(String modId, String version, String sha256, boolean directory) {
            this.modId = requireText(modId, "modId");
            this.version = requireText(version, "version");
            this.sha256 = sha256 == null || sha256.trim()
                .isEmpty() ? null
                    : sha256.trim()
                        .toUpperCase(Locale.ROOT);
            this.directory = directory;
        }

        String getModId() {
            return modId;
        }

        String getVersion() {
            return version;
        }

        String getSha256() {
            return sha256;
        }

        boolean isDirectory() {
            return directory;
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.trim()
            .isEmpty()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }
}
