package it.egeos.cut3g.airgap.service.files;

import it.egeos.cut3g.airgap.persistence.entity.PackageEntity;
import it.egeos.cut3g.airgap.persistence.enums.PackageState;
import it.egeos.cut3g.airgap.persistence.repo.PackageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.file.*;
import java.time.Instant;

@Service
public class AsyncArchiveService {

    private static final Logger log = LoggerFactory.getLogger(AsyncArchiveService.class);

    @Value("${airgap.incoming.packages.archive.dir}")
    private String archiveDir;

    @Autowired
    private PackageRepository packageRepository;

    @Async
    public void archiveLater(String packageId, Path tarPath) {
        try {
            Thread.sleep(2 * 60 * 1000); // configurable delay

            Path archiveRoot = Paths.get(archiveDir);
            Files.createDirectories(archiveRoot);

            Path target = archiveRoot.resolve(tarPath.getFileName());
            Files.move(tarPath, target, StandardCopyOption.REPLACE_EXISTING);

            PackageEntity pkg = packageRepository.findById(packageId).orElseThrow();
            pkg.setState(PackageState.ARCHIVED);
            pkg.setNotes("Archived successfully");
            pkg.setExportedAt(Instant.now()); // TODO: archivedAt
            packageRepository.save(pkg);

        } catch (Exception e) {
            log.warn("Archive failed for {}", tarPath, e);
        }
    }
}

