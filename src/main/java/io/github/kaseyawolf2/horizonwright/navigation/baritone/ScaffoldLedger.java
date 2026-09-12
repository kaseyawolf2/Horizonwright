package io.github.kaseyawolf2.horizonwright.navigation.baritone;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import io.github.kaseyawolf2.horizonwright.core.excavation.BlockPosition;

/** Profile/dimension journal of exact scaffold identities; cleanup survives runtime recreation. */
final class ScaffoldLedger {

    private final Map<BlockPosition, String> placed = new HashMap<>();
    private Path file;

    synchronized void bind(Path file) {
        this.file = file;
        placed.clear();
        if (file == null || !Files.exists(file)) return;
        Properties data = new Properties();
        try (java.io.InputStream in = Files.newInputStream(file)) {
            data.load(in);
            for (String key : data.stringPropertyNames()) {
                String[] xyz = key.split(",");
                if (xyz.length != 3) throw new IllegalArgumentException("Invalid scaffold coordinate");
                placed.put(
                    new BlockPosition(Integer.parseInt(xyz[0]), Integer.parseInt(xyz[1]), Integer.parseInt(xyz[2])),
                    data.getProperty(key));
            }
        } catch (IOException | RuntimeException failure) {
            throw new IllegalStateException("Cannot load scaffold cleanup journal", failure);
        }
    }

    synchronized void record(BlockPosition position, String fingerprint) {
        placed.put(position, fingerprint);
        save();
    }

    synchronized boolean matches(BlockPosition position, String fingerprint) {
        return fingerprint.equals(placed.get(position));
    }

    synchronized List<BlockPosition> positions() {
        List<BlockPosition> result = new ArrayList<>(placed.keySet());
        result.sort(
            java.util.Comparator.comparingInt(BlockPosition::getY)
                .reversed()
                .thenComparingInt(BlockPosition::getX)
                .thenComparingInt(BlockPosition::getZ));
        return result;
    }

    synchronized void remove(BlockPosition position) {
        if (placed.remove(position) != null) save();
    }

    synchronized void clear() {
        placed.clear();
        file = null;
    }

    private void save() {
        if (file == null) return;
        Properties data = new Properties();
        placed.forEach((pos, value) -> data.setProperty(pos.getX() + "," + pos.getY() + "," + pos.getZ(), value));
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.createDirectories(file.getParent());
            try (java.io.OutputStream out = Files.newOutputStream(temporary)) {
                data.store(out, "Horizonwright scaffolds pending removal");
            }
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException fallback) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot persist scaffold cleanup journal", failure);
        }
    }
}
