package io.github.kaseyawolf2.horizonwright.forge.client.persistence;

import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Bridges integrated-server world lifecycle events to an immutable client-readable marker publication.
 *
 * <p>
 * Marker storage is touched synchronously from the server-world load callback. The client thread only reads the
 * volatile immutable snapshot and therefore never reaches into server world storage. Remote multiplayer worlds are
 * deliberately ignored; multiplayer identity requires a separate explicit enrollment boundary.
 * </p>
 */
public final class SingleplayerWorldMarkerRegistry {

    private static final SingleplayerWorldMarkerRegistry INSTANCE = new SingleplayerWorldMarkerRegistry(
        new SingleplayerWorldMarkerResolver());

    private final SingleplayerWorldMarkerResolver resolver;

    private volatile SingleplayerWorldMarkerSnapshot snapshot = SingleplayerWorldMarkerSnapshot
        .noWorld(0L, "no integrated singleplayer world is loaded");
    private Object activeWorld;
    private long revision;
    private boolean initialized;

    SingleplayerWorldMarkerRegistry(SingleplayerWorldMarkerResolver resolver) {
        if (resolver == null) {
            throw new IllegalArgumentException("resolver must not be null");
        }
        this.resolver = resolver;
    }

    public static SingleplayerWorldMarkerRegistry getInstance() {
        return INSTANCE;
    }

    /** Registers this boundary once; callers can safely invoke this during repeated client initialization. */
    public synchronized void initialize() {
        if (initialized) {
            return;
        }
        MinecraftForge.EVENT_BUS.register(this);
        initialized = true;
    }

    /** Returns the latest immutable publication without accessing server-owned world state. */
    public SingleplayerWorldMarkerSnapshot snapshot() {
        return snapshot;
    }

    /** Runs on the thread that posts the server-world load event, which is the integrated-server thread. */
    @SubscribeEvent
    public void onWorldLoad(final WorldEvent.Load event) {
        try {
            if (event == null || !isIntegratedDimensionZero(event.world)) {
                return;
            }
            final World world = event.world;
            recordWorldLoad(world, new MarkerResolution() {

                @Override
                public SingleplayerWorldMarkerResult resolve() {
                    return resolver.resolve(world);
                }
            });
        } catch (Throwable ignored) {
            // A lifecycle adapter failure must never escape into Minecraft's world-loading path.
        }
    }

    /** Clears a publication only when the exact server-world object that produced it unloads. */
    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        try {
            if (event != null) {
                recordWorldUnload(event.world);
            }
        } catch (Throwable ignored) {
            // A lifecycle adapter failure must never escape into Minecraft's world-unloading path.
        }
    }

    synchronized boolean recordWorldLoad(Object world, MarkerResolution resolution) {
        if (world == null || resolution == null || activeWorld == world) {
            return false;
        }
        activeWorld = world;
        revision++;
        try {
            snapshot = SingleplayerWorldMarkerSnapshot.fromResult(revision, resolution.resolve());
        } catch (Throwable failure) {
            snapshot = SingleplayerWorldMarkerSnapshot
                .unavailable(revision, "singleplayer world marker lifecycle failed: " + describe(failure));
        }
        return true;
    }

    synchronized boolean recordWorldUnload(Object world) {
        if (world == null || activeWorld != world) {
            return false;
        }
        activeWorld = null;
        revision++;
        snapshot = SingleplayerWorldMarkerSnapshot.noWorld(revision, "integrated singleplayer world unloaded");
        return true;
    }

    private static boolean isIntegratedDimensionZero(World world) {
        if (!(world instanceof WorldServer) || world.isRemote
            || world.provider == null
            || world.provider.dimensionId != 0) {
            return false;
        }
        return ((WorldServer) world).func_73046_m() instanceof IntegratedServer;
    }

    private static String describe(Throwable failure) {
        String message = failure.getMessage();
        return failure.getClass()
            .getSimpleName()
            + (message == null || message.trim()
                .isEmpty() ? "" : ": " + message.trim());
    }

    interface MarkerResolution {

        SingleplayerWorldMarkerResult resolve();
    }
}
