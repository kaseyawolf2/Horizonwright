package io.github.kaseyawolf2.horizonwright.forge.client.farm;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Pure validation of the exact Hunger Overhaul artifact observed for right-click harvesting. */
final class HungerOverhaulCompatibilityInspector {

    static final String MOD_ID = "HungerOverhaul";
    static final String VERSION = "1.0.0.jenkins104";
    static final String SHA256 = "800E55C375575941E7EB6CE1F5C13BE518453016171AF1BA77A7B607C92641F9";

    HungerOverhaulCompatibilityStatus inspect(List<ArtifactEvidence> evidence, boolean deobfuscatedEnvironment) {
        if (evidence == null || evidence.contains(null)) {
            throw new IllegalArgumentException("Hunger Overhaul artifact evidence must not be null or contain null");
        }
        List<ArtifactEvidence> matches = new ArrayList<>();
        for (ArtifactEvidence candidate : evidence) {
            if (MOD_ID.equals(candidate.getModId())) matches.add(candidate);
        }
        if (matches.size() != 1) {
            return HungerOverhaulCompatibilityStatus
                .unavailable("Expected exactly one loaded Hunger Overhaul artifact but found " + matches.size());
        }
        ArtifactEvidence actual = matches.get(0);
        if (!VERSION.equals(actual.getVersion())) {
            return HungerOverhaulCompatibilityStatus.unavailable(
                "Unsupported Hunger Overhaul version '" + actual.getVersion() + "'; expected '" + VERSION + "'");
        }
        if (actual.isDirectory()) {
            return deobfuscatedEnvironment
                ? HungerOverhaulCompatibilityStatus
                    .available(false, "Hunger Overhaul development directory passed version validation")
                : HungerOverhaulCompatibilityStatus
                    .unavailable("Hunger Overhaul source is a directory outside a deobfuscated environment");
        }
        if (actual.getSha256() == null) {
            return HungerOverhaulCompatibilityStatus.unavailable("Hunger Overhaul source bytes could not be hashed");
        }
        if (!SHA256.equals(actual.getSha256())) {
            return deobfuscatedEnvironment
                ? HungerOverhaulCompatibilityStatus
                    .available(false, "Hunger Overhaul development bytes passed version validation")
                : HungerOverhaulCompatibilityStatus
                    .unavailable("Unsupported Hunger Overhaul SHA-256 " + actual.getSha256() + "; expected " + SHA256);
        }
        return HungerOverhaulCompatibilityStatus
            .available(true, "Hunger Overhaul " + VERSION + " passed exact-byte validation");
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
