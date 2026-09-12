package io.github.kaseyawolf2.horizonwright.forge.client;

import java.lang.reflect.Method;

/** Optional Freecam 1.0.9 integration; camera control is distinct from player control. */
public final class FreecamCompatibility {

    private static final Method INSTANCE;
    private static final Method ACTIVE;
    private static final Method PLAYER_CONTROLLED;

    static {
        Method instance = null, active = null, player = null;
        try {
            Class<?> controller = Class.forName(
                "com.caedis.freecam.camera.FreecamController",
                false,
                FreecamCompatibility.class.getClassLoader());
            instance = controller.getMethod("instance");
            active = controller.getMethod("isActive");
            player = controller.getMethod("isPlayerControlled");
        } catch (ReflectiveOperationException | LinkageError unavailable) {
            // Freecam is optional. Unknown integrations keep normal manual preemption.
        }
        INSTANCE = instance;
        ACTIVE = active;
        PLAYER_CONTROLLED = player;
    }

    private FreecamCompatibility() {}

    public static boolean controlsCamera() {
        if (INSTANCE == null || ACTIVE == null || PLAYER_CONTROLLED == null) return false;
        try {
            Object controller = INSTANCE.invoke(null);
            return cameraOnly(
                Boolean.TRUE.equals(ACTIVE.invoke(controller)),
                Boolean.TRUE.equals(PLAYER_CONTROLLED.invoke(controller)));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
            return false;
        }
    }

    static boolean cameraOnly(boolean active, boolean playerControlled) {
        return active && !playerControlled;
    }
}
