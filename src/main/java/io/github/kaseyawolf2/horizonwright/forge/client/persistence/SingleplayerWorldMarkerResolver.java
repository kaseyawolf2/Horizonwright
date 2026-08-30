package io.github.kaseyawolf2.horizonwright.forge.client.persistence;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.UUID;

import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;

/** Resolves or creates the Horizonwright marker for an integrated server's dimension-zero save. */
public final class SingleplayerWorldMarkerResolver {

    private final MarkerIdSource markerIds;

    public SingleplayerWorldMarkerResolver() {
        this(new MarkerIdSource() {

            @Override
            public String next() {
                return UUID.randomUUID()
                    .toString();
            }
        });
    }

    SingleplayerWorldMarkerResolver(MarkerIdSource markerIds) {
        if (markerIds == null) {
            throw new IllegalArgumentException("markerIds must not be null");
        }
        this.markerIds = markerIds;
    }

    public SingleplayerWorldMarkerResult resolve(World dimensionZeroWorld) {
        if (dimensionZeroWorld == null || dimensionZeroWorld.isRemote
            || dimensionZeroWorld.provider == null
            || dimensionZeroWorld.provider.dimensionId != 0) {
            return SingleplayerWorldMarkerResult.unavailable(
                SingleplayerWorldMarkerResult.Status.UNAVAILABLE,
                "singleplayer dimension-zero server world is unavailable");
        }
        try {
            return resolve(
                new WorldMarkerStorage(dimensionZeroWorld),
                dimensionZeroWorld.getSaveHandler()
                    .getWorldDirectory());
        } catch (RuntimeException failure) {
            return SingleplayerWorldMarkerResult.unavailable(
                SingleplayerWorldMarkerResult.Status.UNAVAILABLE,
                "singleplayer world marker lookup failed: " + describe(failure));
        }
    }

    SingleplayerWorldMarkerResult resolve(MarkerStorage storage, File saveDirectory) {
        if (storage == null || saveDirectory == null) {
            return SingleplayerWorldMarkerResult.unavailable(
                SingleplayerWorldMarkerResult.Status.UNAVAILABLE,
                "singleplayer marker storage or save directory is unavailable");
        }
        final String locator;
        try {
            locator = locatorKey(saveDirectory);
        } catch (IOException | RuntimeException failure) {
            return SingleplayerWorldMarkerResult.unavailable(
                SingleplayerWorldMarkerResult.Status.UNAVAILABLE,
                "singleplayer save locator could not be resolved: " + describe(failure));
        }

        final HorizonwrightWorldMarkerData loaded;
        try {
            loaded = storage.load();
        } catch (RuntimeException failure) {
            return SingleplayerWorldMarkerResult.unavailable(
                SingleplayerWorldMarkerResult.Status.UNAVAILABLE,
                "singleplayer world marker could not be read: " + describe(failure));
        }
        if (loaded != null) {
            if (!loaded.isValidMarker()) {
                return SingleplayerWorldMarkerResult.unavailable(
                    SingleplayerWorldMarkerResult.Status.CORRUPT,
                    loaded.getDiagnostic() + "; existing save data was preserved");
            }
            return SingleplayerWorldMarkerResult.available(
                SingleplayerWorldMarkerResult.Status.LOADED,
                new SingleplayerWorldBindingEvidence(locator, loaded.getMarkerId()),
                loaded.getDiagnostic());
        }

        final HorizonwrightWorldMarkerData created;
        try {
            created = HorizonwrightWorldMarkerData.create(markerIds.next());
            storage.set(created);
        } catch (RuntimeException failure) {
            return SingleplayerWorldMarkerResult.unavailable(
                SingleplayerWorldMarkerResult.Status.UNAVAILABLE,
                "singleplayer world marker could not be created: " + describe(failure));
        }
        return SingleplayerWorldMarkerResult.available(
            SingleplayerWorldMarkerResult.Status.CREATED,
            new SingleplayerWorldBindingEvidence(locator, created.getMarkerId()),
            created.getDiagnostic());
    }

    static String locatorKey(File saveDirectory) throws IOException {
        String canonical = saveDirectory.getCanonicalFile()
            .toPath()
            .normalize()
            .toString();
        if (File.separatorChar == '\\') {
            canonical = canonical.toLowerCase(Locale.ROOT);
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
            StringBuilder encoded = new StringBuilder(hash.length * 2);
            for (byte value : hash) {
                encoded.append(String.format(Locale.ROOT, "%02x", value & 0xff));
            }
            return "sp-save-sha256:" + encoded;
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static String describe(Exception failure) {
        String message = failure.getMessage();
        return failure.getClass()
            .getSimpleName()
            + (message == null || message.trim()
                .isEmpty() ? "" : ": " + message);
    }

    interface MarkerIdSource {

        String next();
    }

    interface MarkerStorage {

        HorizonwrightWorldMarkerData load();

        void set(HorizonwrightWorldMarkerData marker);
    }

    private static final class WorldMarkerStorage implements MarkerStorage {

        private final World world;

        private WorldMarkerStorage(World world) {
            this.world = world;
        }

        @Override
        public HorizonwrightWorldMarkerData load() {
            WorldSavedData loaded = world
                .loadItemData(HorizonwrightWorldMarkerData.class, HorizonwrightWorldMarkerData.DATA_NAME);
            return loaded == null ? null : (HorizonwrightWorldMarkerData) loaded;
        }

        @Override
        public void set(HorizonwrightWorldMarkerData marker) {
            world.setItemData(HorizonwrightWorldMarkerData.DATA_NAME, marker);
        }
    }
}
