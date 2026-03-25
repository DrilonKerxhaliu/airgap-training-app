package it.egeos.cut3g.airgap.service.util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Component
public class PathInitializerCheck {

    private static final Logger log = LoggerFactory.getLogger(PathInitializerCheck.class);

    @Value("${airgap.collect.in}")
    private String collectIn;

    @Value("${airgap.collect.out}")
    private String collectOut;

    @Value("${airgap.save.original.packages.dir}")
    private String originalPackages;

    @Value("${airgap.archive.packages.dir}")
    private String archiveDir;

    @Value("${airgap.unpack.work.dir}")
    private String workDir;

    @Value("${airgap.uploaded.packages.dir}")
    private String uploadedDir;

    @PostConstruct
    public void init() {
        log.info("Initializing Airgap paths...");

        List<Path> paths = List.of(
                Path.of(collectIn),
                Path.of(collectOut),
                Path.of(originalPackages),
                Path.of(archiveDir),
                Path.of(workDir),
                Path.of(uploadedDir)
        );

        for (Path path : paths) {
            createAndValidate(path);
        }

        log.info("All Airgap paths initialized successfully");
    }

    private void createAndValidate(Path path) {
        try {
            if (!Files.exists(path)) {
                Files.createDirectories(path);
                log.info("Created directory: {}", path);
            } else {
                log.info("Directory exists: {}", path);
            }

            if (!Files.isDirectory(path)) {
                throw new IllegalStateException("Path is not a directory: " + path);
            }

            if (!Files.isWritable(path)) {
                throw new IllegalStateException("Path is not writable: " + path);
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize path: " + path, e);
        }
    }
}
