package io.github.kaseyawolf2.horizonwright.navigation.baritone;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;

import net.minecraft.launchwrapper.Launch;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;
import io.github.kaseyawolf2.horizonwright.navigation.baritone.BaritoneInstallationInspector.ModCandidate;
import io.github.kaseyawolf2.horizonwright.navigation.baritone.BaritoneInstallationInspector.ProviderResource;
import io.github.kaseyawolf2.horizonwright.navigation.baritone.BaritoneInstallationInspector.Snapshot;
import io.github.kaseyawolf2.horizonwright.navigation.baritone.BaritoneInstallationInspector.SourceArtifact;

/**
 * Collects Forge/class-loader evidence before any Baritone API type is loaded, then validates it fail-closed.
 */
public final class BaritoneInstallationProbe {

    private static final Logger LOG = LogManager.getLogger("horizonwright-baritone-probe");
    private static final int HASH_BUFFER_SIZE = 8192;

    private BaritoneInstallationProbe() {}

    /**
     * Inspects the currently loaded installation without resolving or initializing any {@code baritone.*} class.
     */
    public static BaritoneInstallationStatus inspect() {
        ClassLoader contextLoader = Thread.currentThread()
            .getContextClassLoader();
        ClassLoader resourceLoader = contextLoader == null ? BaritoneInstallationProbe.class.getClassLoader()
            : contextLoader;
        try {
            Snapshot snapshot = collectSnapshot(resourceLoader, isDeobfuscatedEnvironment());
            BaritoneInstallationStatus status = new BaritoneInstallationInspector().inspect(snapshot);
            log(status);
            return status;
        } catch (IOException | RuntimeException | LinkageError exception) {
            String detail = exception.getMessage() == null ? exception.getClass()
                .getName() : exception.getMessage();
            BaritoneInstallationStatus status = BaritoneInstallationStatus.unavailable(
                "Baritone installation inspection failed before API loading: " + detail
                    + ". Navigation remains disabled; install the pinned Baritone JAR and retry.",
                "Baritone source inspection failed",
                null);
            LOG.error(status.getDiagnostic(), exception);
            return status;
        }
    }

    private static Snapshot collectSnapshot(ClassLoader resourceLoader, boolean deobfuscatedEnvironment)
        throws IOException {
        List<ModCandidate> candidates = collectCandidates();
        List<String> apiResources = collectResourceLocations(
            resourceLoader,
            BaritoneInstallationInspector.API_RESOURCE);
        List<ProviderResource> providerResources = collectProviderResources(resourceLoader);
        return new Snapshot(candidates, apiResources, providerResources, deobfuscatedEnvironment);
    }

    private static List<ModCandidate> collectCandidates() {
        List<ModCandidate> candidates = new ArrayList<>();
        for (ModContainer container : Loader.instance()
            .getModList()) {
            if (BaritoneInstallationInspector.EXPECTED_MOD_ID.equals(container.getModId())) {
                candidates.add(
                    new ModCandidate(
                        container.getModId(),
                        container.getVersion(),
                        inspectSource(container.getSource())));
            }
        }
        return candidates;
    }

    private static SourceArtifact inspectSource(File source) {
        if (source == null) {
            return SourceArtifact.unreadable("<missing Forge mod source>", "Forge ModContainer returned no source");
        }

        String description = source.getAbsolutePath();
        if (source.isDirectory()) {
            return SourceArtifact.directory(description);
        }
        if (!source.isFile()) {
            return SourceArtifact.unreadable(description, "source is not a regular file or directory");
        }

        try {
            return SourceArtifact.regularFile(description, sha256(source));
        } catch (IOException exception) {
            return SourceArtifact.unreadable(description, describe(exception));
        }
    }

    private static List<String> collectResourceLocations(ClassLoader resourceLoader, String resourceName)
        throws IOException {
        List<String> locations = new ArrayList<>();
        Enumeration<URL> resources = resourceLoader.getResources(resourceName);
        while (resources.hasMoreElements()) {
            locations.add(
                resources.nextElement()
                    .toExternalForm());
        }
        return locations;
    }

    private static List<ProviderResource> collectProviderResources(ClassLoader resourceLoader) throws IOException {
        List<ProviderResource> resources = new ArrayList<>();
        Enumeration<URL> urls = resourceLoader.getResources(BaritoneInstallationInspector.PROVIDER_RESOURCE);
        while (urls.hasMoreElements()) {
            URL url = urls.nextElement();
            try {
                resources.add(ProviderResource.readable(url.toExternalForm(), readProviders(url)));
            } catch (IOException exception) {
                resources.add(ProviderResource.unreadable(url.toExternalForm(), describe(exception)));
            }
        }
        return resources;
    }

    private static List<String> readProviders(URL resource) throws IOException {
        List<String> providers = new ArrayList<>();
        try (InputStream input = resource.openStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                int comment = line.indexOf('#');
                String provider = (comment < 0 ? line : line.substring(0, comment)).trim();
                if (!provider.isEmpty()) {
                    providers.add(provider);
                }
            }
        }
        return Collections.unmodifiableList(providers);
    }

    private static String sha256(File source) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM does not provide SHA-256", exception);
        }

        try (InputStream input = new FileInputStream(source)) {
            byte[] buffer = new byte[HASH_BUFFER_SIZE];
            int count;
            while ((count = input.read(buffer)) >= 0) {
                if (count > 0) {
                    digest.update(buffer, 0, count);
                }
            }
        }

        StringBuilder value = new StringBuilder(64);
        for (byte item : digest.digest()) {
            value.append(String.format(Locale.ROOT, "%02X", item & 0xff));
        }
        return value.toString();
    }

    private static boolean isDeobfuscatedEnvironment() {
        Object value = Launch.blackboard.get("fml.deobfuscatedEnvironment");
        return Boolean.TRUE.equals(value);
    }

    private static void log(BaritoneInstallationStatus status) {
        if (status.isAvailable() && status.isReferenceBytes()) {
            LOG.info(status.getDiagnostic());
        } else if (status.isAvailable()) {
            LOG.warn(status.getDiagnostic());
        } else {
            LOG.error(status.getDiagnostic());
        }
    }

    private static String describe(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.trim()
            .isEmpty() ? exception.getClass()
                .getName() : message;
    }
}
