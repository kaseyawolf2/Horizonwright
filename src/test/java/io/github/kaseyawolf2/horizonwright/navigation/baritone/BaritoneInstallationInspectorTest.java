package io.github.kaseyawolf2.horizonwright.navigation.baritone;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

import io.github.kaseyawolf2.horizonwright.navigation.baritone.BaritoneInstallationInspector.ModCandidate;
import io.github.kaseyawolf2.horizonwright.navigation.baritone.BaritoneInstallationInspector.ProviderResource;
import io.github.kaseyawolf2.horizonwright.navigation.baritone.BaritoneInstallationInspector.Snapshot;
import io.github.kaseyawolf2.horizonwright.navigation.baritone.BaritoneInstallationInspector.SourceArtifact;

public class BaritoneInstallationInspectorTest {

    private static final String SOURCE = "mods/" + BaritoneInstallationInspector.EXPECTED_ARTIFACT;
    private static final String REMAPPED_SHA = "1111111111111111111111111111111111111111111111111111111111111111";

    private final BaritoneInstallationInspector inspector = new BaritoneInstallationInspector();

    @Test
    public void absentModIsUnavailable() {
        BaritoneInstallationStatus status = inspector.inspect(
            new Snapshot(
                Collections.<ModCandidate>emptyList(),
                Collections.<String>emptyList(),
                Collections.<ProviderResource>emptyList(),
                false));

        assertFalse(status.isAvailable());
        assertTrue(
            status.getDiagnostic()
                .contains("was not loaded"));
    }

    @Test
    public void incompatibleVersionIsUnavailable() {
        BaritoneInstallationStatus status = inspector.inspect(
            validSnapshot(
                new ModCandidate(BaritoneInstallationInspector.EXPECTED_MOD_ID, "v1.2.18-mc1.7.10", referenceSource()),
                false));

        assertFalse(status.isAvailable());
        assertTrue(
            status.getDiagnostic()
                .contains("Unsupported Baritone version"));
    }

    @Test
    public void duplicateApiClassResourcesAreUnavailable() {
        Snapshot snapshot = new Snapshot(
            Collections.singletonList(referenceCandidate()),
            Arrays.asList("jar:file:first!/BaritoneAPI.class", "jar:file:second!/BaritoneAPI.class"),
            Collections.singletonList(validProvider()),
            false);

        BaritoneInstallationStatus status = inspector.inspect(snapshot);

        assertFalse(status.isAvailable());
        assertTrue(
            status.getDiagnostic()
                .contains("found 2"));
    }

    @Test
    public void duplicateProviderResourcesAreUnavailable() {
        Snapshot snapshot = new Snapshot(
            Collections.singletonList(referenceCandidate()),
            Collections.singletonList("jar:file:baritone!/BaritoneAPI.class"),
            Arrays.asList(validProvider(), validProvider()),
            false);

        BaritoneInstallationStatus status = inspector.inspect(snapshot);

        assertFalse(status.isAvailable());
        assertTrue(
            status.getDiagnostic()
                .contains("duplicate Baritone providers"));
    }

    @Test
    public void missingProviderResourceIsUnavailable() {
        Snapshot snapshot = new Snapshot(
            Collections.singletonList(referenceCandidate()),
            Collections.singletonList("jar:file:baritone!/BaritoneAPI.class"),
            Collections.<ProviderResource>emptyList(),
            false);

        BaritoneInstallationStatus status = inspector.inspect(snapshot);

        assertFalse(status.isAvailable());
        assertTrue(
            status.getDiagnostic()
                .contains("found 0"));
    }

    @Test
    public void unexpectedProviderEntryIsUnavailable() {
        Snapshot snapshot = new Snapshot(
            Collections.singletonList(referenceCandidate()),
            Collections.singletonList("jar:file:baritone!/BaritoneAPI.class"),
            Collections.singletonList(
                ProviderResource.readable(
                    "jar:file:baritone!/provider",
                    Arrays.asList(BaritoneInstallationInspector.EXPECTED_PROVIDER, "example.UntrustedProvider"))),
            false);

        BaritoneInstallationStatus status = inspector.inspect(snapshot);

        assertFalse(status.isAvailable());
        assertTrue(
            status.getDiagnostic()
                .contains("must contain only"));
    }

    @Test
    public void referenceHashIsAvailableAndReported() {
        BaritoneInstallationStatus status = inspector.inspect(validSnapshot(referenceCandidate(), false));

        assertTrue(status.isAvailable());
        assertTrue(status.isReferenceBytes());
        assertTrue(status.hasSourceSha256());
        assertTrue(
            status.getDiagnostic()
                .contains(BaritoneInstallationInspector.REFERENCE_SHA256));
        assertTrue(BaritoneInstallationInspector.REFERENCE_SHA256.equals(status.getSourceSha256()));
    }

    @Test
    public void pinnedJarForgeNormalizedVersionIsAccepted() {
        ModCandidate forgeNormalized = new ModCandidate(
            BaritoneInstallationInspector.EXPECTED_MOD_ID,
            BaritoneInstallationInspector.EXPECTED_VERSION,
            referenceSource());

        BaritoneInstallationStatus status = inspector.inspect(validSnapshot(forgeNormalized, false));

        assertTrue(status.isAvailable());
        assertTrue(status.isReferenceBytes());
        assertTrue(
            status.getDiagnostic()
                .contains(BaritoneInstallationInspector.EXPECTED_BUILD));
    }

    @Test
    public void embeddedBuildSpellingIsNotMistakenForTheForgeManifestVersion() {
        ModCandidate tagSpelling = new ModCandidate(
            BaritoneInstallationInspector.EXPECTED_MOD_ID,
            BaritoneInstallationInspector.EXPECTED_BUILD,
            referenceSource());

        BaritoneInstallationStatus status = inspector.inspect(validSnapshot(tagSpelling, false));

        assertFalse(status.isAvailable());
        assertTrue(
            status.getDiagnostic()
                .contains("expected '" + BaritoneInstallationInspector.EXPECTED_VERSION + "'"));
    }

    @Test
    public void differentProductionHashIsUnavailable() {
        ModCandidate candidate = candidate(SourceArtifact.regularFile(SOURCE, REMAPPED_SHA));

        BaritoneInstallationStatus status = inspector.inspect(validSnapshot(candidate, false));

        assertFalse(status.isAvailable());
        assertFalse(status.isReferenceBytes());
        assertTrue(
            status.getDiagnostic()
                .contains("Unsupported Baritone bytes"));
        assertTrue(
            status.getDiagnostic()
                .contains(REMAPPED_SHA));
        assertTrue(
            status.getDiagnostic()
                .contains(BaritoneInstallationInspector.EXPECTED_ARTIFACT));
    }

    @Test
    public void remappedDevelopmentHashIsAvailableButNotReferenceBytes() {
        ModCandidate candidate = candidate(SourceArtifact.regularFile(SOURCE, REMAPPED_SHA));

        BaritoneInstallationStatus status = inspector.inspect(validSnapshot(candidate, true));

        assertTrue(status.isAvailable());
        assertFalse(status.isReferenceBytes());
        assertTrue(
            status.getDiagnostic()
                .contains("deobfuscated/remapped development environment"));
        assertTrue(REMAPPED_SHA.equals(status.getSourceSha256()));
    }

    @Test
    public void deobfuscatedDirectoryIsAvailableButHasNoReferenceHash() {
        ModCandidate candidate = candidate(SourceArtifact.directory("build/classes/java/main"));

        BaritoneInstallationStatus status = inspector.inspect(validSnapshot(candidate, true));

        assertTrue(status.isAvailable());
        assertFalse(status.isReferenceBytes());
        assertFalse(status.hasSourceSha256());
        assertNull(status.getSourceSha256());
    }

    @Test
    public void unreadableSourceIsUnavailableEvenInDevelopment() {
        ModCandidate candidate = candidate(SourceArtifact.unreadable(SOURCE, "access denied"));

        BaritoneInstallationStatus status = inspector.inspect(validSnapshot(candidate, true));

        assertFalse(status.isAvailable());
        assertTrue(
            status.getDiagnostic()
                .contains("access denied"));
        assertTrue(
            status.getDiagnostic()
                .contains("Navigation remains disabled"));
    }

    @Test
    public void unreadableProviderResourceIsUnavailable() {
        Snapshot snapshot = new Snapshot(
            Collections.singletonList(referenceCandidate()),
            Collections.singletonList("jar:file:baritone!/BaritoneAPI.class"),
            Collections.singletonList(ProviderResource.unreadable("jar:file:baritone!/provider", "bad zip")),
            false);

        BaritoneInstallationStatus status = inspector.inspect(snapshot);

        assertFalse(status.isAvailable());
        assertTrue(
            status.getDiagnostic()
                .contains("bad zip"));
    }

    private static Snapshot validSnapshot(ModCandidate candidate, boolean deobfuscatedEnvironment) {
        return new Snapshot(
            Collections.singletonList(candidate),
            Collections.singletonList("jar:file:baritone!/BaritoneAPI.class"),
            Collections.singletonList(validProvider()),
            deobfuscatedEnvironment);
    }

    private static ModCandidate referenceCandidate() {
        return candidate(referenceSource());
    }

    private static ModCandidate candidate(SourceArtifact source) {
        return new ModCandidate(
            BaritoneInstallationInspector.EXPECTED_MOD_ID,
            BaritoneInstallationInspector.EXPECTED_VERSION,
            source);
    }

    private static SourceArtifact referenceSource() {
        return SourceArtifact.regularFile(SOURCE, BaritoneInstallationInspector.REFERENCE_SHA256);
    }

    private static ProviderResource validProvider() {
        return ProviderResource.readable(
            "jar:file:baritone!/provider",
            Collections.singletonList(BaritoneInstallationInspector.EXPECTED_PROVIDER));
    }
}
