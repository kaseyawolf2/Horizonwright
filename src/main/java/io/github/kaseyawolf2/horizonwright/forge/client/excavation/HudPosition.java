package io.github.kaseyawolf2.horizonwright.forge.client.excavation;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

/** Position relative to the space available around the panel, independent of resolution and GUI scale. */
public final class HudPosition {

    public static final HudPosition DEFAULT = new HudPosition(0.02, 0.35);
    public final double horizontal;
    public final double vertical;

    public HudPosition(double horizontal, double vertical) {
        this.horizontal = clamp(horizontal);
        this.vertical = clamp(vertical);
    }

    private static double clamp(double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("HUD position must be finite");
        return Math.max(0, Math.min(1, value));
    }

    public int x(int screenWidth, int panelWidth) {
        return (int) Math.round(horizontal * Math.max(0, screenWidth - panelWidth));
    }

    public int y(int screenHeight, int panelHeight) {
        return (int) Math.round(vertical * Math.max(0, screenHeight - panelHeight));
    }

    public static HudPosition at(int x, int y, int screenWidth, int screenHeight, int panelWidth, int panelHeight) {
        return new HudPosition(
            x / (double) Math.max(1, screenWidth - panelWidth),
            y / (double) Math.max(1, screenHeight - panelHeight));
    }

    public static HudPosition load(Path file) throws IOException {
        if (!Files.exists(file)) return DEFAULT;
        Properties values = new Properties();
        try (Reader reader = Files.newBufferedReader(file)) {
            values.load(reader);
        }
        try {
            return new HudPosition(
                Double.parseDouble(values.getProperty("x")),
                Double.parseDouble(values.getProperty("y")));
        } catch (RuntimeException invalid) {
            throw new IOException("Invalid saved HUD position", invalid);
        }
    }

    public void save(Path file) throws IOException {
        Files.createDirectories(
            file.toAbsolutePath()
                .getParent());
        Path temporary = Files.createTempFile(
            file.toAbsolutePath()
                .getParent(),
            "horizonwright-hud-",
            ".tmp");
        try {
            Properties values = new Properties();
            values.setProperty("x", Double.toString(horizontal));
            values.setProperty("y", Double.toString(vertical));
            try (Writer writer = Files.newBufferedWriter(temporary)) {
                values.store(writer, "Horizonwright HUD position (0..1)");
            }
            try {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
