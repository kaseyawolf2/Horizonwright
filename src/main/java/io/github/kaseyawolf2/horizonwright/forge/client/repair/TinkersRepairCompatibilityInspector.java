package io.github.kaseyawolf2.horizonwright.forge.client.repair;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Pure validation of the three installed artifacts the pinned repair adapter was characterized against. */
public final class TinkersRepairCompatibilityInspector {

    public static final ArtifactExpectation TCONSTRUCT = new ArtifactExpectation(
        "TConstruct",
        "1.14.93-GTNH",
        "D4B5C6F42D195938AEA74853680581FA175A925D8D17DAD8AB0A44663F9C772E");
    public static final ArtifactExpectation TGREGWORKS = new ArtifactExpectation(
        "TGregworks",
        "1.7.10-GTNH-1.0.33",
        "93FFCA6F64FC394383A8B73838EB416BC0C4B97C8B4E89000AFBCD89A2A5E807");
    public static final ArtifactExpectation MANTLE = new ArtifactExpectation(
        "Mantle",
        "0.5.4",
        "6E5C4B0699888B54D81EC9E2A2AC0FBD2905A08766A819B8F823FF3E1BD6B9EE");

    private static final List<ArtifactExpectation> EXPECTED = Collections
        .unmodifiableList(java.util.Arrays.asList(TCONSTRUCT, TGREGWORKS, MANTLE));

    public TinkersRepairCompatibilityStatus inspect(List<ArtifactEvidence> evidence, boolean deobfuscatedEnvironment) {
        if (evidence == null || evidence.contains(null)) {
            throw new IllegalArgumentException("artifact evidence must not be null or contain null");
        }
        boolean allReferenceBytes = true;
        List<String> accepted = new ArrayList<>();
        for (ArtifactExpectation expected : EXPECTED) {
            List<ArtifactEvidence> matches = matching(evidence, expected.getModId());
            if (matches.size() != 1) {
                return TinkersRepairCompatibilityStatus.unavailable(
                    "Expected exactly one loaded " + expected.getModId() + " artifact but found " + matches.size());
            }
            ArtifactEvidence actual = matches.get(0);
            if (!expected.getVersion()
                .equals(actual.getVersion())) {
                return TinkersRepairCompatibilityStatus.unavailable(
                    "Unsupported " + expected.getModId()
                        + " version '"
                        + actual.getVersion()
                        + "'; expected '"
                        + expected.getVersion()
                        + "'");
            }
            if (actual.isDirectory()) {
                if (!deobfuscatedEnvironment) {
                    return TinkersRepairCompatibilityStatus
                        .unavailable(expected.getModId() + " source is a directory outside a deobfuscated environment");
                }
                allReferenceBytes = false;
                accepted.add(expected.getModId() + " development directory");
                continue;
            }
            if (actual.getSha256() == null) {
                return TinkersRepairCompatibilityStatus
                    .unavailable(expected.getModId() + " source bytes could not be hashed");
            }
            if (!expected.getSha256()
                .equals(actual.getSha256())) {
                if (!deobfuscatedEnvironment) {
                    return TinkersRepairCompatibilityStatus.unavailable(
                        "Unsupported " + expected
                            .getModId() + " SHA-256 " + actual.getSha256() + "; expected " + expected.getSha256());
                }
                allReferenceBytes = false;
            }
            accepted.add(expected.getModId() + " " + expected.getVersion());
        }
        return TinkersRepairCompatibilityStatus
            .available(allReferenceBytes, "Pinned repair stack passed exact-version validation: " + accepted);
    }

    private static List<ArtifactEvidence> matching(List<ArtifactEvidence> evidence, String modId) {
        List<ArtifactEvidence> result = new ArrayList<>();
        for (ArtifactEvidence candidate : evidence) {
            if (modId.equals(candidate.getModId())) {
                result.add(candidate);
            }
        }
        return result;
    }

    public static final class ArtifactExpectation {

        private final String modId;
        private final String version;
        private final String sha256;

        private ArtifactExpectation(String modId, String version, String sha256) {
            this.modId = modId;
            this.version = version;
            this.sha256 = sha256;
        }

        public String getModId() {
            return modId;
        }

        public String getVersion() {
            return version;
        }

        public String getSha256() {
            return sha256;
        }
    }

    public static final class ArtifactEvidence {

        private final String modId;
        private final String version;
        private final String sha256;
        private final boolean directory;

        public ArtifactEvidence(String modId, String version, String sha256, boolean directory) {
            this.modId = requireText(modId, "modId");
            this.version = requireText(version, "version");
            this.sha256 = sha256 == null || sha256.trim()
                .isEmpty() ? null
                    : sha256.trim()
                        .toUpperCase(Locale.ROOT);
            this.directory = directory;
        }

        public String getModId() {
            return modId;
        }

        public String getVersion() {
            return version;
        }

        public String getSha256() {
            return sha256;
        }

        public boolean isDirectory() {
            return directory;
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.trim()
            .isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
