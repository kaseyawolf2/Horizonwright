package io.github.kaseyawolf2.horizonwright.core.persistence;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;

final class NioPersistenceFileSystem implements PersistenceFileSystem {

    @Override
    public boolean exists(Path path) throws IOException {
        try {
            Files.readAttributes(path, BasicFileAttributes.class);
            return true;
        } catch (NoSuchFileException missing) {
            return false;
        }
    }

    @Override
    public byte[] readAllBytes(Path path) throws IOException {
        return Files.readAllBytes(path);
    }

    @Override
    public void createDirectories(Path directory) throws IOException {
        Files.createDirectories(directory);
    }

    @Override
    public void writeAndSync(Path path, byte[] content) throws IOException {
        try (FileOutputStream output = new FileOutputStream(path.toFile(), false)) {
            output.write(content);
            output.flush();
            output.getFD()
                .sync();
        }
    }

    @Override
    public void atomicReplace(Path source, Path target) throws IOException {
        Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }

    @Override
    public void deleteIfExists(Path path) throws IOException {
        Files.deleteIfExists(path);
    }
}
