package it.egeos.cut3g.airgap;

import it.egeos.cut3g.airgap.persistence.entity.PackageEntity;
import it.egeos.cut3g.airgap.persistence.enums.FileItemState;
import it.egeos.cut3g.airgap.persistence.repo.FileItemRepository;
import it.egeos.cut3g.airgap.persistence.repo.PackageRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class PackagingIntegrationTest {

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(PackagingIntegrationTest.class);

    @Autowired
    FileItemRepository fileItemRepository;

    @Autowired
    PackageRepository packageRepository;


    @Test
    void fullFlow_folderWatcher_to_packaging() throws Exception {

        // prepare filesystem
        Path root = Paths.get("target/test/IN");
        Files.createDirectories(root.resolve("CUT3G"));
        Files.createDirectories(root.resolve("brAInt"));

        Files.write(root.resolve("CUT3G/file1.txt"), "hello".getBytes());
        Files.write(root.resolve("brAInt/file2.txt"), "world".getBytes());

        long newCount = fileItemRepository.countByState(FileItemState.NEW);
        log.info("Files in NEW state: {}", newCount);

        log.info("Files created on filesystem.");

        // wait for watcher to mark NEW
        Thread.sleep(4000);

        // wait for scheduler / packaging
        Thread.sleep(4000);

        long packCount = fileItemRepository.countByState(FileItemState.PACKED);
        log.info("Files in NEW state: {}", packCount);

        List<PackageEntity> pkgs = packageRepository.findAll();
        log.info("Total packages in DB: {}", pkgs.size());
    }
}
