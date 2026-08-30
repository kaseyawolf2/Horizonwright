package io.github.kaseyawolf2.horizonwright.architecture;

import static org.junit.Assert.assertFalse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import org.junit.Test;

public class M1RuntimeArchitectureTest {

    @Test
    public void taskAndGuiSourcesDoNotImportBaritoneOrInputImplementations() throws IOException {
        List<Path> sources = new ArrayList<>();
        Path taskRoot = Paths.get("src/main/java/io/github/kaseyawolf2/horizonwright/runtime/task");
        try (Stream<Path> files = Files.walk(taskRoot)) {
            files.filter(
                path -> path.toString()
                    .endsWith(".java"))
                .forEach(sources::add);
        }
        sources.addAll(
            Arrays.asList(
                Paths.get(
                    "src/main/java/io/github/kaseyawolf2/horizonwright/forge/client/GuiHorizonwrightDashboard.java"),
                Paths.get(
                    "src/main/java/io/github/kaseyawolf2/horizonwright/forge/client/HorizonwrightClientCommand.java")));

        for (Path source : sources) {
            String text = new String(Files.readAllBytes(source), StandardCharsets.UTF_8);
            assertFalse(source + " imports Baritone", text.contains("import baritone."));
            assertFalse(source + " imports the Baritone adapter", text.contains("navigation.baritone"));
            assertFalse(
                source + " imports ClientInputArbiter",
                text.contains("import io.github.kaseyawolf2.horizonwright.forge.client.ClientInputArbiter"));
            if (source.startsWith(taskRoot)) {
                assertFalse(source + " imports Minecraft", text.contains("import net.minecraft."));
                assertFalse(source + " imports key bindings", text.contains("KeyBinding"));
            }
        }
    }
}
