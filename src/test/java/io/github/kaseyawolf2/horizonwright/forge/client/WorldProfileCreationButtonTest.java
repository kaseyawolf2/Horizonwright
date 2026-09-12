package io.github.kaseyawolf2.horizonwright.forge.client;

import static org.junit.Assert.*;

import java.nio.file.Path;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import io.github.kaseyawolf2.horizonwright.core.persistence.HorizonwrightPersistenceStore;
import io.github.kaseyawolf2.horizonwright.core.persistence.ProfileBindingIndexStore;
import io.github.kaseyawolf2.horizonwright.core.persistence.ProfileBindingKey;
import io.github.kaseyawolf2.horizonwright.runtime.persistence.session.ClientProfileBindingCoordinator;
import io.github.kaseyawolf2.horizonwright.runtime.persistence.session.ClientProfileBindingObservation;
import io.github.kaseyawolf2.horizonwright.runtime.persistence.session.ClientProfileBindingState;

public class WorldProfileCreationButtonTest {

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void actionPersistsProfileBeforeRuntimeExistsAndCannotCreateItTwice() throws Exception {
        Path root = temporaryFolder.newFolder()
            .toPath();
        ClientProfileBindingCoordinator bindings = coordinator(root);
        WorldProfileCreation action = new WorldProfileCreation(bindings);
        assertFalse(action.canCreate());

        bindings.observe(world("world-one"));
        action.refresh();
        assertTrue(action.canCreate());
        assertTrue(
            action.create()
                .startsWith("World profile created."));
        assertEquals(
            ClientProfileBindingState.READY,
            bindings.getSnapshot()
                .getState());
        assertEquals(
            "profile-created",
            bindings.getSnapshot()
                .getSelectedIdentity()
                .get()
                .getProfileId());
        assertFalse(action.canCreate());
        assertTrue(
            action.create()
                .contains("unavailable"));

        ClientProfileBindingCoordinator restarted = coordinator(root);
        assertEquals(
            ClientProfileBindingState.READY,
            restarted.observe(world("world-one"))
                .getState());
        assertEquals(
            "profile-created",
            restarted.getSnapshot()
                .getSelectedIdentity()
                .get()
                .getProfileId());
    }

    @Test
    public void staleClickDoesNotEnrollADifferentWorld() throws Exception {
        Path root = temporaryFolder.newFolder()
            .toPath();
        ClientProfileBindingCoordinator bindings = coordinator(root);
        bindings.observe(world("world-one"));
        WorldProfileCreation action = new WorldProfileCreation(bindings);
        assertTrue(action.canCreate());

        bindings.clearWorld();
        bindings.observe(world("world-two"));
        assertTrue(
            action.create()
                .contains("world profile changed"));
        assertEquals(
            ClientProfileBindingState.NEEDS_EXPLICIT_ENROLLMENT,
            bindings.getSnapshot()
                .getState());
        assertFalse(
            bindings.getSnapshot()
                .getSelectedIdentity()
                .isPresent());

        assertTrue(
            action.create()
                .startsWith("World profile created."));
        assertEquals(
            "world-two",
            bindings.getSnapshot()
                .getSelectedIdentity()
                .get()
                .getWorldFingerprint());
    }

    @Test
    public void disconnectedOrUnavailableWorldCannotBeEnrolled() throws Exception {
        WorldProfileCreation absent = new WorldProfileCreation(null);
        assertFalse(absent.canCreate());
        assertEquals("No session", absent.diagnostic("No session"));
        assertTrue(
            absent.create()
                .contains("unavailable"));

        ClientProfileBindingCoordinator bindings = coordinator(
            temporaryFolder.newFolder()
                .toPath());
        bindings.observe(world("world-one"));
        WorldProfileCreation action = new WorldProfileCreation(bindings);
        bindings.clearWorld();
        assertTrue(
            action.create()
                .contains("world profile changed"));
        assertFalse(action.canCreate());
        assertEquals(
            ClientProfileBindingState.NO_WORLD,
            bindings.getSnapshot()
                .getState());
    }

    private static ClientProfileBindingCoordinator coordinator(Path root) {
        return new ClientProfileBindingCoordinator(
            new ProfileBindingIndexStore(root),
            new HorizonwrightPersistenceStore(root),
            () -> "profile-created",
            () -> "confirmation-created",
            () -> 100L);
    }

    private static ClientProfileBindingObservation world(String marker) {
        return new ClientProfileBindingObservation(
            ProfileBindingKey.multiplayer("test.example:25565", marker),
            "Test world",
            "test.example:25565",
            marker);
    }
}
