package it.egeos.cut3g.airgap.service.files;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

@Service
public class FileTransferService {

    private static final Logger log = LoggerFactory.getLogger(FileTransferService.class);

    public Path copyFile(Path source, Path destinationDir) throws IOException {

        Path normalizedSource = source.toAbsolutePath().normalize();

        if (!Files.exists(normalizedSource)) {
            throw new IllegalArgumentException("Source file not found: " + normalizedSource);
        }

        Path destDir = ensureDirectory(destinationDir);

        Path target = destDir.resolve(normalizedSource.getFileName());

        log.info("COPY FILE {} -> {}", normalizedSource, target);

        Files.copy(normalizedSource, target, StandardCopyOption.REPLACE_EXISTING);

        return target;
    }

    public void copyDirectory(Path sourceDir, Path destinationDir) throws IOException {

        Path normalizedSource = sourceDir.toAbsolutePath().normalize();

        if (!Files.exists(normalizedSource)) {
            throw new IllegalArgumentException("Source directory not found: " + normalizedSource);
        }

        Path destRoot = ensureDirectory(destinationDir);

        log.info("COPY DIRECTORY {} -> {}", normalizedSource, destRoot);

        Files.walkFileTree(normalizedSource, new SimpleFileVisitor<>() {
        });
    }


    public Path ensureDirectory(Path dir) throws IOException {
        Path normalized = dir.toAbsolutePath().normalize();

        if (!Files.exists(normalized)) {
            Files.createDirectories(normalized);
            log.info("Created directory: {}", normalized);
        }

        return normalized;
    }
}