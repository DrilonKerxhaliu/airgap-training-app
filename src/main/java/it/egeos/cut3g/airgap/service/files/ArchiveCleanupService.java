package it.egeos.cut3g.airgap.service.files;

import it.egeos.cut3g.airgap.persistence.entity.PackageEntity;
import it.egeos.cut3g.airgap.persistence.entity.TransactionEntity;
import it.egeos.cut3g.airgap.persistence.entity.UploadPackageEntity;
import it.egeos.cut3g.airgap.persistence.enums.Direction;
import it.egeos.cut3g.airgap.persistence.enums.PackageState;
import it.egeos.cut3g.airgap.persistence.enums.UploadPackageStatus;
import it.egeos.cut3g.airgap.persistence.repo.PackageRepository;
import it.egeos.cut3g.airgap.persistence.repo.UploadPackageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class ArchiveCleanupService {

    private static final Logger log =
            LoggerFactory.getLogger(ArchiveCleanupService.class);

    @Value("${airgap.cleanup.archive.days}")
    private int retentionDays;

    @Value("${airgap.cleanup.enabled}")
    private boolean enabled;

    @Autowired
    private PackageRepository packageRepository;

    @Autowired
    private TransactionService transactionService;
    @Autowired
    private UploadPackageRepository uploadPackageRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void cleanupArchivedPackages() {

        if (!enabled) {
            log.info("Archive cleanup disabled by configuration");
            return;
        }

        Instant threshold =
                Instant.now().minus(retentionDays, ChronoUnit.DAYS);

        List<PackageEntity> candidates =
                packageRepository.findArchivedBefore(
                        PackageState.ARCHIVED,
                        threshold
                );

        log.info("Archive cleanup started – {} candidates found", candidates.size());

        for (PackageEntity pkg : candidates) {
            deleteSinglePackage(pkg,null);
        }

        log.info("Archive cleanup completed");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void manualCleanupArchivedPkg(String packageId, String username) {

        UploadPackageEntity pkg = uploadPackageRepository.findById(packageId)
                .orElseThrow(() ->
                        new IllegalArgumentException("Package not found: " + packageId));

        // optional domain protection
        if (pkg.getStatus() == UploadPackageStatus.DELETED) {
            log.info("Package {} already deleted – skipping", packageId);
            return;
        }

     //   deleteSinglePackage(pkg, username);
    }

    private void deleteSinglePackage(PackageEntity pkg, String username) {
        TransactionEntity tx =
               transactionService.startTransaction(
                        pkg.getId(),
                        Direction.CLEANUP,
                       username
                );

        tx.setPackageState(pkg.getState());


        Path tarPath = Paths.get(pkg.getPackagePath());

        try {
            if (Files.exists(tarPath)) {
                Files.delete(tarPath);
                log.info("Deleted archived file {}", tarPath);
            } else {
                log.warn("Archive file not found: {}", tarPath);
            }

            pkg.setState(PackageState.DELETED);
            pkg.setNotes("Deleted by archive cleanup job");
            pkg.setExportedAt(Instant.now()); // last state change

            packageRepository.save(pkg);

            transactionService.closeSuccess(
                    tx.getId(),
                    "Package deleted by archive cleanup"
            );

        } catch (Exception e) {
            log.error(
                    "Failed to cleanup archived package {}",
                    pkg.getId(),
                    e
            );

            transactionService.closeFailure(
                    tx.getId(),
                    e.getMessage(),
                    username
            );
        }
    }
}
