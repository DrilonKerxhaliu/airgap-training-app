package it.egeos.cut3g.airgap.service.files;

import it.egeos.cut3g.airgap.persistence.entity.TransactionEntity;
import it.egeos.cut3g.airgap.persistence.entity.UploadPackageEntity;
import it.egeos.cut3g.airgap.persistence.enums.Direction;
import it.egeos.cut3g.airgap.persistence.enums.UploadPackageStatus;
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

        List<UploadPackageEntity> candidates =
                uploadPackageRepository.findArchivedBefore(
                        UploadPackageStatus.ARCHIVED,
                        threshold
                );

        log.info("Archive cleanup started – {} candidates found", candidates.size());

        for (UploadPackageEntity pkg : candidates) {
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

     deleteSinglePackage(pkg, username);
    }

    private void deleteSinglePackage(UploadPackageEntity pkg, String username) {
        TransactionEntity tx =
               transactionService.startTransaction(
                        pkg.getId(),
                        Direction.CLEANUP,
                       username
                );

        tx.setUploadPackageStatus(pkg.getStatus());


        Path tarPath = Paths.get(pkg.getArchivedTarPath());

        try {
            if (Files.exists(tarPath)) {
                Files.delete(tarPath);
                log.info("Deleted archived file {}", tarPath);
            } else {
                log.warn("Archive file not found: {}", tarPath);
            }

            pkg.setStatus(UploadPackageStatus.DELETED);
            pkg.setNote("Deleted by archive cleanup job");
            pkg.setRemovedAt(Instant.now()); // last state change

            uploadPackageRepository.save(pkg);

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
