package io.github.kaseyawolf2.horizonwright.forge.client.excavation;

import java.io.IOException;
import java.nio.file.Path;

import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RenderGameOverlayEvent;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import io.github.kaseyawolf2.horizonwright.core.task.ControllerSnapshot;
import io.github.kaseyawolf2.horizonwright.core.task.TaskSnapshot;
import io.github.kaseyawolf2.horizonwright.runtime.persistence.session.CurrentRuntimeProvider;
import io.github.kaseyawolf2.horizonwright.runtime.task.ExcavationStatistics;

/** Compact live timing display; the same persisted numbers remain in task details after completion. */
public final class ExcavationStatisticsOverlay {

    private final CurrentRuntimeProvider provider;
    private long nextRefresh;
    private String text = "";

    public ExcavationStatisticsOverlay(CurrentRuntimeProvider provider) {
        this.provider = provider;
    }

    @SubscribeEvent
    public void render(RenderGameOverlayEvent.Text event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.currentScreen != null
            || mc.gameSettings.hideGUI
            || mc.gameSettings.showDebugInfo) return;
        long now = System.nanoTime();
        if (now >= nextRefresh) {
            nextRefresh = now + 250_000_000L;
            text = "";
            if (provider.getCurrentRuntime()
                .isPresent()) {
                ControllerSnapshot snapshot = provider.getCurrentRuntime()
                    .get()
                    .getController()
                    .snapshot();
                TaskSnapshot active = snapshot.getActiveTaskId()
                    .flatMap(snapshot::findTask)
                    .orElse(null);
                if (active != null && "excavation".equals(
                    active.getSpec()
                        .getType()))
                    text = ExcavationStatistics.describe(active.getCheckpoint());
            }
        }
        if (text.isEmpty()) return;
        ExcavationHudLayout layout = new ExcavationHudLayout(
            mc.fontRenderer,
            text,
            event.resolution.getScaledWidth(),
            event.resolution.getScaledHeight());
        HudPosition position = position();
        layout.draw(
            mc.fontRenderer,
            position.x(event.resolution.getScaledWidth(), layout.width),
            position.y(event.resolution.getScaledHeight(), layout.height),
            false);
    }

    private static HudPosition savedPosition;

    private static Path settingsFile() {
        return Minecraft.getMinecraft().mcDataDir.toPath()
            .resolve("config/horizonwright-hud.properties");
    }

    public static HudPosition position() {
        if (savedPosition == null) {
            try {
                savedPosition = HudPosition.load(settingsFile());
            } catch (IOException failure) {
                io.github.kaseyawolf2.horizonwright.HorizonwrightMod.LOG
                    .warn("Unable to load HUD position; using default", failure);
                savedPosition = HudPosition.DEFAULT;
            }
        }
        return savedPosition;
    }

    public static void savePosition(HudPosition position) throws IOException {
        position.save(settingsFile());
        savedPosition = position;
    }
}
