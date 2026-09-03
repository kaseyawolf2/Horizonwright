package io.github.kaseyawolf2.horizonwright.navigation.baritone;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Pure, deterministic validation of a discovered Baritone installation.
 *
 * <p>
 * This class deliberately has no Forge or Baritone dependency. The production probe converts Forge loader and
 * class-loader evidence into a {@link Snapshot}; tests can inject the same evidence directly.
 * </p>
 */
public final class BaritoneInstallationInspector {

    public static final String EXPECTED_MOD_ID = "baritone";
    /** Exact value exposed by the pinned JAR's Forge ModContainer after Forge normalizes its metadata. */
    public static final String EXPECTED_VERSION = "1.2.19-mc1.7.10";
    /** Full build metadata embedded in the pinned JAR's {@code mcmod.info}. */
    public static final String EXPECTED_BUILD = "v1.2.19-mc1.7.10-1-7-10-forge+fcbbd4882c-dirty";
    public static final String EXPECTED_ARTIFACT = "baritone-v1.2.19-mc1.7.10-1-7-10-forge+fcbbd4882c.jar";
    public static final String EXPECTED_PROVIDER = "baritone.BaritoneProvider";
    public static final String EXPECTED_COMMIT = "fcbbd4882cc7d846a8e613dea4b50203e1fb4ebc";
    public static final String REFERENCE_SHA256 = "9EEADEBBABB253AAE53AF90D46E280C23B217F4DF29D5B693EEC814D7379EDE1";
    public static final String API_RESOURCE = "baritone/api/BaritoneAPI.class";
    public static final String PROVIDER_RESOURCE = "META-INF/services/baritone.api.IBaritoneProvider";

    private static final String UNKNOWN_SOURCE = "Baritone source not discovered";
    private static final String REPLACEMENT_ROUTE = "Install vendor/baritone/" + EXPECTED_ARTIFACT
        + " with SHA-256 "
        + REFERENCE_SHA256
        + ".";

    public BaritoneInstallationStatus inspect(Snapshot snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException("snapshot must not be null");
        }

        List<ModCandidate> candidates = matchingCandidates(snapshot.getModCandidates());
        if (candidates.isEmpty()) {
            return unavailable("Baritone mod id '" + EXPECTED_MOD_ID + "' was not loaded. " + REPLACEMENT_ROUTE);
        }
        if (candidates.size() != 1) {
            return unavailable(
                "Expected exactly one loaded '" + EXPECTED_MOD_ID
                    + "' mod container but found "
                    + candidates.size()
                    + ". Remove duplicate Baritone installations. "
                    + REPLACEMENT_ROUTE);
        }

        ModCandidate candidate = candidates.get(0);
        SourceArtifact source = candidate.getSource();
        if (!EXPECTED_VERSION.equals(candidate.getVersion())) {
            return unavailable(
                "Unsupported Baritone version '" + printable(
                    candidate.getVersion()) + "'; expected '" + EXPECTED_VERSION + "'. " + REPLACEMENT_ROUTE,
                source);
        }

        if (snapshot.getApiClassResources()
            .size() != 1) {
            return unavailable(
                "Expected exactly one " + API_RESOURCE
                    + " resource but found "
                    + snapshot.getApiClassResources()
                        .size()
                    + ". Remove missing or duplicate Baritone installations. "
                    + REPLACEMENT_ROUTE,
                source);
        }

        if (snapshot.getProviderResources()
            .size() != 1) {
            return unavailable(
                "Expected exactly one " + PROVIDER_RESOURCE
                    + " resource but found "
                    + snapshot.getProviderResources()
                        .size()
                    + ". Remove missing or duplicate Baritone providers. "
                    + REPLACEMENT_ROUTE,
                source);
        }

        ProviderResource providerResource = snapshot.getProviderResources()
            .get(0);
        if (!providerResource.isReadable()) {
            return unavailable(
                "Could not read Baritone provider resource " + providerResource
                    .getLocation() + ": " + providerResource.getReadFailure() + ". " + REPLACEMENT_ROUTE,
                source);
        }
        if (providerResource.getProviders()
            .size() != 1
            || !EXPECTED_PROVIDER.equals(
                providerResource.getProviders()
                    .get(0))) {
            return unavailable(
                "Baritone provider resource must contain only '" + EXPECTED_PROVIDER
                    + "' but contained "
                    + providerResource.getProviders()
                    + ". "
                    + REPLACEMENT_ROUTE,
                source);
        }

        if (!source.isReadable()) {
            return unavailable(
                "Could not verify Baritone source " + source.getDescription()
                    + ": "
                    + source.getReadFailure()
                    + ". Navigation remains disabled. "
                    + REPLACEMENT_ROUTE,
                source);
        }

        if (source.getKind() == SourceKind.DIRECTORY) {
            if (snapshot.isDeobfuscatedEnvironment()) {
                return BaritoneInstallationStatus.available(
                    false,
                    "Baritone " + EXPECTED_BUILD
                        + " passed structural validation from deobfuscated directory "
                        + source.getDescription()
                        + "; referenceBytes=false because a directory has no single JAR hash.",
                    source.getDescription(),
                    null);
            }
            return unavailable(
                "Baritone source " + source.getDescription()
                    + " is a directory in a production environment, so its bytes cannot be verified. "
                    + REPLACEMENT_ROUTE,
                source);
        }

        if (source.getKind() != SourceKind.REGULAR_FILE || source.getSha256() == null) {
            return unavailable(
                "Baritone source " + source.getDescription()
                    + " is not a verifiable regular file. "
                    + REPLACEMENT_ROUTE,
                source);
        }

        if (REFERENCE_SHA256.equals(source.getSha256())) {
            return BaritoneInstallationStatus.available(
                true,
                "Baritone " + EXPECTED_BUILD
                    + " passed structural validation; source SHA-256 "
                    + source.getSha256()
                    + " matches the pinned reference bytes.",
                source.getDescription(),
                source.getSha256());
        }

        if (snapshot.isDeobfuscatedEnvironment()) {
            return BaritoneInstallationStatus.available(
                false,
                "Baritone " + EXPECTED_BUILD
                    + " passed structural validation in a deobfuscated/remapped development environment; source SHA-256 "
                    + source.getSha256()
                    + " differs from reference "
                    + REFERENCE_SHA256
                    + "; referenceBytes=false.",
                source.getDescription(),
                source.getSha256());
        }

        return unavailable(
            "Unsupported Baritone bytes: source SHA-256 " + source.getSha256()
                + " differs from reference "
                + REFERENCE_SHA256
                + ". Navigation remains disabled. "
                + REPLACEMENT_ROUTE,
            source);
    }

    private static List<ModCandidate> matchingCandidates(List<ModCandidate> candidates) {
        List<ModCandidate> matching = new ArrayList<>();
        for (ModCandidate candidate : candidates) {
            if (candidate != null && EXPECTED_MOD_ID.equals(candidate.getModId())) {
                matching.add(candidate);
            }
        }
        return matching;
    }

    private static BaritoneInstallationStatus unavailable(String diagnostic) {
        return BaritoneInstallationStatus.unavailable(diagnostic, UNKNOWN_SOURCE, null);
    }

    private static BaritoneInstallationStatus unavailable(String diagnostic, SourceArtifact source) {
        if (source == null) {
            return unavailable(diagnostic);
        }
        return BaritoneInstallationStatus.unavailable(diagnostic, source.getDescription(), source.getSha256());
    }

    private static String printable(String value) {
        return value == null || value.trim()
            .isEmpty() ? "<missing>" : value;
    }

    public static final class Snapshot {

        private final List<ModCandidate> modCandidates;
        private final List<String> apiClassResources;
        private final List<ProviderResource> providerResources;
        private final boolean deobfuscatedEnvironment;

        public Snapshot(List<ModCandidate> modCandidates, List<String> apiClassResources,
            List<ProviderResource> providerResources, boolean deobfuscatedEnvironment) {
            this.modCandidates = immutableCopy(modCandidates, "modCandidates");
            this.apiClassResources = immutableCopy(apiClassResources, "apiClassResources");
            this.providerResources = immutableCopy(providerResources, "providerResources");
            this.deobfuscatedEnvironment = deobfuscatedEnvironment;
        }

        public List<ModCandidate> getModCandidates() {
            return modCandidates;
        }

        public List<String> getApiClassResources() {
            return apiClassResources;
        }

        public List<ProviderResource> getProviderResources() {
            return providerResources;
        }

        public boolean isDeobfuscatedEnvironment() {
            return deobfuscatedEnvironment;
        }

        private static <T> List<T> immutableCopy(List<T> values, String field) {
            if (values == null) {
                throw new IllegalArgumentException(field + " must not be null");
            }
            return Collections.unmodifiableList(new ArrayList<>(values));
        }
    }

    public static final class ModCandidate {

        private final String modId;
        private final String version;
        private final SourceArtifact source;

        public ModCandidate(String modId, String version, SourceArtifact source) {
            this.modId = modId;
            this.version = version;
            this.source = source == null ? SourceArtifact.unreadable(UNKNOWN_SOURCE, "source was not supplied")
                : source;
        }

        public String getModId() {
            return modId;
        }

        public String getVersion() {
            return version;
        }

        public SourceArtifact getSource() {
            return source;
        }
    }

    public static final class ProviderResource {

        private final String location;
        private final List<String> providers;
        private final String readFailure;

        private ProviderResource(String location, List<String> providers, String readFailure) {
            this.location = requireText(location, "location");
            this.providers = providers == null ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<>(providers));
            this.readFailure = normalizeOptionalText(readFailure);
        }

        public static ProviderResource readable(String location, List<String> providers) {
            if (providers == null) {
                throw new IllegalArgumentException("providers must not be null");
            }
            return new ProviderResource(location, providers, null);
        }

        public static ProviderResource unreadable(String location, String readFailure) {
            return new ProviderResource(
                location,
                Collections.<String>emptyList(),
                requireText(readFailure, "readFailure"));
        }

        public String getLocation() {
            return location;
        }

        public List<String> getProviders() {
            return providers;
        }

        public boolean isReadable() {
            return readFailure == null;
        }

        public String getReadFailure() {
            return readFailure;
        }
    }

    public static final class SourceArtifact {

        private final String description;
        private final SourceKind kind;
        private final String sha256;
        private final String readFailure;

        private SourceArtifact(String description, SourceKind kind, String sha256, String readFailure) {
            this.description = requireText(description, "description");
            this.kind = kind;
            this.sha256 = normalizeHash(sha256);
            this.readFailure = normalizeOptionalText(readFailure);
        }

        public static SourceArtifact regularFile(String description, String sha256) {
            return new SourceArtifact(description, SourceKind.REGULAR_FILE, requireText(sha256, "sha256"), null);
        }

        public static SourceArtifact directory(String description) {
            return new SourceArtifact(description, SourceKind.DIRECTORY, null, null);
        }

        public static SourceArtifact unreadable(String description, String readFailure) {
            return new SourceArtifact(
                description,
                SourceKind.UNREADABLE,
                null,
                requireText(readFailure, "readFailure"));
        }

        public String getDescription() {
            return description;
        }

        public SourceKind getKind() {
            return kind;
        }

        public String getSha256() {
            return sha256;
        }

        public boolean isReadable() {
            return readFailure == null;
        }

        public String getReadFailure() {
            return readFailure;
        }

        private static String normalizeHash(String value) {
            String normalized = normalizeOptionalText(value);
            return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
        }
    }

    public enum SourceKind {
        REGULAR_FILE,
        DIRECTORY,
        UNREADABLE
    }

    private static String requireText(String value, String field) {
        if (value == null || value.trim()
            .isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    private static String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
