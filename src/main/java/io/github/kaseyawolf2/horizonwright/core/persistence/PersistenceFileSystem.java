package io.github.kaseyawolf2.horizonwright.core.persistence;

import java.io.IOException;
import java.nio.file.Path;

interface PersistenceFileSystem {

    boolean exists(Path path) throws IOException;

    byte[] readAllBytes(Path path) throws IOException;

    void createDirectories(Path directory) throws IOException;

    void writeAndSync(Path path, byte[] content) throws IOException;

    void atomicReplace(Path source, Path target) throws IOException;

    void deleteIfExists(Path path) throws IOException;
}
