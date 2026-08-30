package io.github.kaseyawolf2.horizonwright.architecture;

import static org.junit.Assert.assertTrue;

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

public class CoreArchitectureTest {

    private static final List<String> FORBIDDEN_IMPORTS = Arrays.asList(
        "import net.minecraft.",
        "import net.minecraftforge.",
        "import cpw.mods.",
        "import baritone.",
        "import thaumcraft.");

    @Test
    public void coreRemainsIndependentOfGameAndBackendTypes() throws IOException {
        Path coreRoot = Paths.get("src", "main", "java", "io", "github", "kaseyawolf2", "horizonwright", "core");
        List<String> violations = new ArrayList<>();

        try (Stream<Path> paths = Files.walk(coreRoot)) {
            paths.filter(
                path -> path.toString()
                    .endsWith(".java"))
                .forEach(path -> findViolations(path, violations));
        }

        assertTrue("Forbidden core imports: " + violations, violations.isEmpty());
    }

    private static void findViolations(Path path, List<String> violations) {
        try {
            String source = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            for (String forbiddenImport : FORBIDDEN_IMPORTS) {
                if (source.contains(forbiddenImport)) {
                    violations.add(path + " -> " + forbiddenImport);
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to inspect " + path, exception);
        }
    }
}
