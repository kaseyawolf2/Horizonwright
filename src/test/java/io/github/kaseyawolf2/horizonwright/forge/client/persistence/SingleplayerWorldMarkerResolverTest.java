package io.github.kaseyawolf2.horizonwright.forge.client.persistence;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.File;

import net.minecraft.nbt.NBTTagCompound;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class SingleplayerWorldMarkerResolverTest {

    private static final String MARKER_ID = "dba3ca5f-e15d-4667-84f6-42d39ce8406f";

    @Rule
    public final TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void createsAndPersistsOneOpaqueMarkerForANewSave() throws Exception {
        FakeStorage storage = new FakeStorage();
        SingleplayerWorldMarkerResolver resolver = resolver(MARKER_ID);
        File save = temporary.newFolder("new-save");

        SingleplayerWorldMarkerResult result = resolver.resolve(storage, save);

        assertEquals(SingleplayerWorldMarkerResult.Status.CREATED, result.getStatus());
        assertTrue(result.isAvailable());
        assertTrue(storage.marker.isDirty());
        assertEquals(
            "uuid:" + MARKER_ID,
            result.getEvidence()
                .get()
                .getWorldFingerprint());
        assertEquals(
            SingleplayerWorldMarkerResolver.locatorKey(save),
            result.getEvidence()
                .get()
                .getLocatorKey());

        NBTTagCompound encoded = new NBTTagCompound();
        storage.marker.writeToNBT(encoded);
        HorizonwrightWorldMarkerData reloaded = new HorizonwrightWorldMarkerData(
            HorizonwrightWorldMarkerData.DATA_NAME);
        reloaded.readFromNBT(encoded);
        storage.marker = reloaded;

        SingleplayerWorldMarkerResult second = resolver.resolve(storage, save);
        assertEquals(SingleplayerWorldMarkerResult.Status.LOADED, second.getStatus());
        assertEquals(result.getEvidence(), second.getEvidence());
        assertSame(reloaded, storage.marker);
    }

    @Test
    public void corruptMarkerIsPreservedAndNeverReplaced() throws Exception {
        HorizonwrightWorldMarkerData corrupt = new HorizonwrightWorldMarkerData(HorizonwrightWorldMarkerData.DATA_NAME);
        NBTTagCompound malformed = new NBTTagCompound();
        malformed.setInteger("schemaVersion", 1);
        malformed.setString("markerId", "not-a-uuid");
        corrupt.readFromNBT(malformed);
        FakeStorage storage = new FakeStorage(corrupt);

        SingleplayerWorldMarkerResult result = resolver(MARKER_ID).resolve(storage, temporary.newFolder("corrupt"));

        assertEquals(SingleplayerWorldMarkerResult.Status.CORRUPT, result.getStatus());
        assertFalse(result.isAvailable());
        assertSame(corrupt, storage.marker);
        assertEquals(0, storage.setCount);
        assertTrue(
            result.getDiagnostic()
                .contains("preserved"));
    }

    @Test
    public void newerMarkerSchemaIsNotDowngradedOrOverwritten() throws Exception {
        HorizonwrightWorldMarkerData newer = new HorizonwrightWorldMarkerData(HorizonwrightWorldMarkerData.DATA_NAME);
        NBTTagCompound document = new NBTTagCompound();
        document.setInteger("schemaVersion", HorizonwrightWorldMarkerData.SCHEMA_VERSION + 1);
        document.setString("markerId", MARKER_ID);
        newer.readFromNBT(document);
        FakeStorage storage = new FakeStorage(newer);

        SingleplayerWorldMarkerResult result = resolver(MARKER_ID).resolve(storage, temporary.newFolder("newer"));

        assertEquals(SingleplayerWorldMarkerResult.Status.CORRUPT, result.getStatus());
        assertSame(newer, storage.marker);
        assertEquals(0, storage.setCount);
        assertTrue(
            result.getDiagnostic()
                .contains("newer schema"));
    }

    @Test
    public void saveLocatorIsOpaqueStableAndDirectorySpecific() throws Exception {
        File first = temporary.newFolder("first");
        File second = temporary.newFolder("second");

        String firstKey = SingleplayerWorldMarkerResolver.locatorKey(first);
        String sameKey = SingleplayerWorldMarkerResolver.locatorKey(new File(first, "."));
        String secondKey = SingleplayerWorldMarkerResolver.locatorKey(second);

        assertEquals(firstKey, sameKey);
        assertNotEquals(firstKey, secondKey);
        assertTrue(firstKey.matches("sp-save-sha256:[0-9a-f]{64}"));
        assertFalse(firstKey.contains(first.getAbsolutePath()));
    }

    @Test
    public void storageFailureLeavesAutomationIdentityUnavailable() throws Exception {
        SingleplayerWorldMarkerResolver.MarkerStorage failing = new SingleplayerWorldMarkerResolver.MarkerStorage() {

            @Override
            public HorizonwrightWorldMarkerData load() {
                throw new IllegalStateException("read failure");
            }

            @Override
            public void set(HorizonwrightWorldMarkerData marker) {
                throw new AssertionError("must not create after read failure");
            }
        };

        SingleplayerWorldMarkerResult result = resolver(MARKER_ID).resolve(failing, temporary.newFolder("unavailable"));

        assertEquals(SingleplayerWorldMarkerResult.Status.UNAVAILABLE, result.getStatus());
        assertFalse(result.isAvailable());
        assertTrue(
            result.getDiagnostic()
                .contains("read failure"));
    }

    private static SingleplayerWorldMarkerResolver resolver(final String markerId) {
        return new SingleplayerWorldMarkerResolver(new SingleplayerWorldMarkerResolver.MarkerIdSource() {

            @Override
            public String next() {
                return markerId;
            }
        });
    }

    private static final class FakeStorage implements SingleplayerWorldMarkerResolver.MarkerStorage {

        private HorizonwrightWorldMarkerData marker;
        private int setCount;

        private FakeStorage() {}

        private FakeStorage(HorizonwrightWorldMarkerData marker) {
            this.marker = marker;
        }

        @Override
        public HorizonwrightWorldMarkerData load() {
            return marker;
        }

        @Override
        public void set(HorizonwrightWorldMarkerData replacement) {
            marker = replacement;
            setCount++;
        }
    }
}
