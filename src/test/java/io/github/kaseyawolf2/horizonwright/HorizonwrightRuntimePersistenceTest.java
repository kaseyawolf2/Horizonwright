package io.github.kaseyawolf2.horizonwright;

import static org.junit.Assert.assertEquals;

import java.nio.file.Path;
import java.util.Collections;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import io.github.kaseyawolf2.horizonwright.core.action.ActionSessionGuard;
import io.github.kaseyawolf2.horizonwright.core.action.InMemoryActionBroker;
import io.github.kaseyawolf2.horizonwright.core.persistence.HorizonwrightPersistenceStore;
import io.github.kaseyawolf2.horizonwright.core.persistence.NamedLocation;
import io.github.kaseyawolf2.horizonwright.core.persistence.NamedRoute;
import io.github.kaseyawolf2.horizonwright.core.persistence.ProfileEnvelope;
import io.github.kaseyawolf2.horizonwright.core.persistence.ProfileReassociation;
import io.github.kaseyawolf2.horizonwright.core.persistence.ProfileStatePaths;
import io.github.kaseyawolf2.horizonwright.core.persistence.WorldProfileIdentity;
import io.github.kaseyawolf2.horizonwright.core.task.MonotonicClock;
import io.github.kaseyawolf2.horizonwright.runtime.persistence.TaskControllerPersistenceCoordinator;

public class HorizonwrightRuntimePersistenceTest {

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void advertisedPersistenceRestoreReseedsRuntimeOwnedGoToSequence() throws Exception {
        Path root = temporaryFolder.newFolder()
            .toPath();
        HorizonwrightPersistenceStore store = new HorizonwrightPersistenceStore(root);
        WorldProfileIdentity identity = new WorldProfileIdentity(
            "gtnh-main",
            "GTNH Main",
            "server.test:25565",
            "world:alpha",
            1L);
        ProfileStatePaths paths = store.pathsForProfile(identity.getProfileId());
        store.saveProfile(
            paths,
            new ProfileEnvelope(
                10L,
                identity,
                Collections.<ProfileReassociation>emptyList(),
                Collections.<NamedLocation>emptyList(),
                Collections.<NamedRoute>emptyList()));
        TaskControllerPersistenceCoordinator persistence = new TaskControllerPersistenceCoordinator(store, identity);
        HorizonwrightRuntime original = runtime();
        HorizonwrightRuntime restored = runtime();
        try {
            assertEquals(
                "goto-1",
                original.submitGoTo(0, 2, 64, 2, 1)
                    .getSpec()
                    .getId());
            persistence.save(20L, 1L, null, original.getController());

            persistence.restoreFresh(restored::restoreControllerState);

            assertEquals(
                "goto-2",
                restored.createGoToTaskSpec(0, 4, 64, 4, 1)
                    .getId());
        } finally {
            original.close();
            restored.close();
        }
    }

    private static HorizonwrightRuntime runtime() {
        return new HorizonwrightRuntime(new InMemoryActionBroker(), new ActionSessionGuard(), new ZeroClock());
    }

    private static final class ZeroClock implements MonotonicClock {

        @Override
        public long nowMillis() {
            return 0L;
        }
    }
}
