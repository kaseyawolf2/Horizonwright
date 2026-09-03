package io.github.kaseyawolf2.horizonwright.architecture;

import static org.junit.Assert.assertFalse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import org.junit.Test;

public class GuiStyleArchitectureTest {

    @Test
    public void horizonwrightScreensNeverMixStockAndHorizonwrightButtonRenderers() throws IOException {
        Path guiRoot = Paths.get("src/main/java/io/github/kaseyawolf2/horizonwright/forge/client");
        try (Stream<Path> files = Files.walk(guiRoot)) {
            for (Path source : (Iterable<Path>) files.filter(
                path -> path.getFileName()
                    .toString()
                    .startsWith("Gui")
                    && path.toString()
                        .endsWith(".java"))::iterator) {
                String text = new String(Files.readAllBytes(source), StandardCharsets.UTF_8);
                assertFalse(source + " constructs a stock button", text.contains("new GuiButton("));
            }
        }
    }
}
