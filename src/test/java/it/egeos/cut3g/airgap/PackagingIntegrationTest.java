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

        // wait for watcher to mark NEW
        Thread.sleep(4000);

        assertEquals(2, fileItemRepository.countByState(FileItemState.NEW));

        // wait for scheduler / size job
        Thread.sleep(4000);

        List<PackageEntity> pkgs = packageRepository.findAll();
        assertFalse(pkgs.isEmpty());

        Path pkgTar = Paths.get(pkgs.get(0).getPackagePath());
        assertTrue(Files.exists(pkgTar));

        // OPTIONAL: assert tar content exists
        assertTrue(Files.size(pkgTar) > 0);
    }
}
