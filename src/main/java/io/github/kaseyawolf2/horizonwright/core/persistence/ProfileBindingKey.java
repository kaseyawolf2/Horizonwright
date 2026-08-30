package io.github.kaseyawolf2.horizonwright.core.persistence;

import java.net.IDN;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Opaque, deterministic lookup key for one explicit client-side world binding.
 *
 * <p>
 * Raw save locators, endpoints, and world markers are normalized only long enough to hash them. They are never
 * retained or exposed by this value.
 */
public final class ProfileBindingKey {

    private static final int DEFAULT_MINECRAFT_PORT = 25565;
    private static final int MAX_OPAQUE_MARKER_LENGTH = 512;
    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");
    private static final String HASH_DOMAIN = "horizonwright-profile-binding-v1";

    private final ProfileBindingKind kind;
    private final String locatorHash;
    private final String worldMarkerHash;

    private ProfileBindingKey(ProfileBindingKind kind, String locatorHash, String worldMarkerHash) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.locatorHash = requireHash(locatorHash, "locatorHash");
        this.worldMarkerHash = requireHash(worldMarkerHash, "worldMarkerHash");
    }

    /** Creates a key from an explicitly configured save locator and opaque per-world marker. */
    public static ProfileBindingKey singleplayer(String saveLocator, String explicitWorldMarker) {
        String locator = normalizeSingleplayerLocator(saveLocator);
        String marker = normalizeOpaqueMarker(explicitWorldMarker, "explicitWorldMarker");
        return hashed(ProfileBindingKind.SINGLEPLAYER, locator, marker);
    }

    /** Creates a key only when both a configured endpoint and an explicit opaque world fingerprint are available. */
    public static ProfileBindingKey multiplayer(String configuredEndpoint, String explicitWorldFingerprint) {
        String endpoint = normalizeMultiplayerEndpoint(configuredEndpoint);
        String fingerprint = normalizeOpaqueMarker(explicitWorldFingerprint, "explicitWorldFingerprint");
        return hashed(ProfileBindingKind.MULTIPLAYER, endpoint, fingerprint);
    }

    /** Reconstructs persisted hashes; validation never accepts raw locator material here. */
    static ProfileBindingKey restore(ProfileBindingKind kind, String locatorHash, String worldMarkerHash) {
        return new ProfileBindingKey(kind, locatorHash, worldMarkerHash);
    }

    public ProfileBindingKind getKind() {
        return kind;
    }

    public String getLocatorHash() {
        return locatorHash;
    }

    public String getWorldMarkerHash() {
        return worldMarkerHash;
    }

    boolean matches(WorldProfileIdentity identity) {
        if (identity == null) {
            return false;
        }
        String fingerprint;
        try {
            fingerprint = normalizeOpaqueMarker(identity.getWorldFingerprint(), "identity worldFingerprint");
        } catch (IllegalArgumentException invalidIdentity) {
            return false;
        }
        if (!worldMarkerHash.equals(hash("world-marker", kind, fingerprint))) {
            return false;
        }
        if (kind == ProfileBindingKind.SINGLEPLAYER) {
            try {
                normalizeSingleplayerIdentityLocator(identity.getServerAddress());
                return true;
            } catch (IllegalArgumentException invalidIdentity) {
                return false;
            }
        }
        try {
            String identityLocator = normalizeMultiplayerEndpoint(identity.getServerAddress());
            return locatorHash.equals(hash("locator", kind, identityLocator));
        } catch (IllegalArgumentException invalidIdentity) {
            return false;
        }
    }

    void validate() {
        Objects.requireNonNull(kind, "profile binding kind");
        requireHash(locatorHash, "profile binding locatorHash");
        requireHash(worldMarkerHash, "profile binding worldMarkerHash");
    }

    private static ProfileBindingKey hashed(ProfileBindingKind kind, String locator, String marker) {
        return new ProfileBindingKey(kind, hash("locator", kind, locator), hash("world-marker", kind, marker));
    }

    private static String normalizeSingleplayerIdentityLocator(String identityLocator) {
        String normalized = PersistenceValidation.requireText(identityLocator, "singleplayer identity serverAddress")
            .toLowerCase(Locale.ROOT);
        if (!"singleplayer".equals(normalized)) {
            throw new IllegalArgumentException(
                "singleplayer identity serverAddress must be the opaque literal 'singleplayer'");
        }
        return normalized;
    }

    private static String normalizeSingleplayerLocator(String saveLocator) {
        String value = PersistenceValidation.requireText(saveLocator, "saveLocator")
            .replace('\\', '/');
        rejectControlCharacters(value, "saveLocator");
        List<String> segments = new ArrayList<>();
        for (String segment : value.split("/+")) {
            if (segment.isEmpty() || ".".equals(segment)) {
                continue;
            }
            if ("..".equals(segment)) {
                throw new IllegalArgumentException("saveLocator must not contain parent traversal");
            }
            segments.add(segment.toLowerCase(Locale.ROOT));
        }
        if (segments.isEmpty()) {
            throw new IllegalArgumentException("saveLocator must identify a configured save");
        }
        return String.join("/", segments);
    }

    private static String normalizeMultiplayerEndpoint(String configuredEndpoint) {
        String value = PersistenceValidation.requireText(configuredEndpoint, "configuredEndpoint");
        rejectControlCharacters(value, "configuredEndpoint");
        if (value.contains("://") || value.indexOf('/') >= 0
            || value.indexOf('@') >= 0
            || value.indexOf('?') >= 0
            || value.indexOf('#') >= 0) {
            throw new IllegalArgumentException("configuredEndpoint must contain only a host and optional port");
        }

        final URI parsed;
        try {
            parsed = new URI("minecraft://" + value);
        } catch (URISyntaxException invalid) {
            throw new IllegalArgumentException("configuredEndpoint is not a valid host and optional port", invalid);
        }
        String host = parsed.getHost();
        if (host == null || host.trim()
            .isEmpty()) {
            throw new IllegalArgumentException("configuredEndpoint must contain a valid host");
        }
        if (parsed.getRawUserInfo() != null || parsed.getRawPath() != null && !parsed.getRawPath()
            .isEmpty() || parsed.getRawQuery() != null || parsed.getRawFragment() != null) {
            throw new IllegalArgumentException("configuredEndpoint must not contain credentials, paths, or queries");
        }
        int port = parsed.getPort() < 0 ? DEFAULT_MINECRAFT_PORT : parsed.getPort();
        if (port < 1 || port > 65_535) {
            throw new IllegalArgumentException("configuredEndpoint port must be between 1 and 65535");
        }

        String normalizedHost;
        if (host.indexOf(':') >= 0) {
            String ipv6 = host.startsWith("[") && host.endsWith("]") ? host.substring(1, host.length() - 1) : host;
            normalizedHost = '[' + ipv6.toLowerCase(Locale.ROOT) + ']';
        } else {
            String withoutTrailingDot = host.endsWith(".") ? host.substring(0, host.length() - 1) : host;
            if (withoutTrailingDot.isEmpty()) {
                throw new IllegalArgumentException("configuredEndpoint must contain a valid host");
            }
            try {
                normalizedHost = IDN.toASCII(withoutTrailingDot, IDN.USE_STD3_ASCII_RULES)
                    .toLowerCase(Locale.ROOT);
            } catch (IllegalArgumentException invalidHost) {
                throw new IllegalArgumentException("configuredEndpoint host is invalid", invalidHost);
            }
        }
        return normalizedHost + ':' + port;
    }

    private static String normalizeOpaqueMarker(String marker, String field) {
        String value = PersistenceValidation.requireText(marker, field);
        rejectControlCharacters(value, field);
        if (value.length() > MAX_OPAQUE_MARKER_LENGTH) {
            throw new IllegalArgumentException(field + " exceeds " + MAX_OPAQUE_MARKER_LENGTH + " characters");
        }
        for (int index = 0; index < value.length(); index++) {
            if (Character.isWhitespace(value.charAt(index))) {
                throw new IllegalArgumentException(field + " must be an opaque token without whitespace");
            }
        }
        return value;
    }

    private static void rejectControlCharacters(String value, String field) {
        for (int index = 0; index < value.length(); index++) {
            if (Character.isISOControl(value.charAt(index))) {
                throw new IllegalArgumentException(field + " must not contain control characters");
            }
        }
    }

    private static String hash(String purpose, ProfileBindingKind kind, String value) {
        final MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
        String input = HASH_DOMAIN + '\u0000' + purpose + '\u0000' + kind.name() + '\u0000' + value;
        byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
        StringBuilder encoded = new StringBuilder(bytes.length * 2);
        for (byte valueByte : bytes) {
            encoded.append(Character.forDigit((valueByte >>> 4) & 0x0f, 16));
            encoded.append(Character.forDigit(valueByte & 0x0f, 16));
        }
        return encoded.toString();
    }

    private static String requireHash(String hash, String field) {
        String normalized = PersistenceValidation.requireText(hash, field);
        if (!SHA_256.matcher(normalized)
            .matches()) {
            throw new IllegalArgumentException(field + " must be a lowercase SHA-256 hash");
        }
        return normalized;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ProfileBindingKey)) {
            return false;
        }
        ProfileBindingKey that = (ProfileBindingKey) other;
        return kind == that.kind && locatorHash.equals(that.locatorHash)
            && worldMarkerHash.equals(that.worldMarkerHash);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, locatorHash, worldMarkerHash);
    }
}
