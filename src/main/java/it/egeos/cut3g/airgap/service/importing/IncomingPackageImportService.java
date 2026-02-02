package it.egeos.cut3g.airgap.service.importing;

import com.google.gson.Gson;
import it.egeos.cut3g.airgap.exceptions.PackageFileNotFoundException;
import it.egeos.cut3g.airgap.exceptions.PackageNotFoundException;
import it.egeos.cut3g.airgap.persistence.entity.PackageEntity;
import it.egeos.cut3g.airgap.persistence.entity.TransactionEntity;
import it.egeos.cut3g.airgap.persistence.enums.Direction;
import it.egeos.cut3g.airgap.persistence.enums.FileItemState;
import it.egeos.cut3g.airgap.persistence.enums.PackageState;
import it.egeos.cut3g.airgap.persistence.repo.FileItemRepository;
import it.egeos.cut3g.airgap.persistence.repo.PackageRepository;
import it.egeos.cut3g.airgap.service.crypto.CryptoService;
import it.egeos.cut3g.airgap.service.files.TransactionService;
import it.egeos.cut3g.airgap.service.manifest.PackageManifest;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.*;
import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class IncomingPackageImportService {

    private static final Logger log = LoggerFactory.getLogger(IncomingPackageImportService.class);

    @Value("${airgap.incoming.packages.dir}")
    private String incomingPackagesDir;

    @Value("${airgap.incoming.packages.archive.dir}")
    private String archiveDir;

    @Value("${airgap.uploaded.packages.dir}")
    private String uploadedPackagesDir;

    @Value("${airgap.unpack.work.dir}")
    private String unpackWorkDir;

    @Autowired
    private CryptoService cryptoService;

    @Autowired
    private ImportDeliveryOrchestrator importDeliveryOrchestrator;

    @Autowired
    private PackageRepository packageRepository;

    @Autowired
    private FileItemRepository fileItemRepository;

    @Autowired
    private TransactionService transactionService;

    private final Gson gson = new Gson();

    /**
     * Imports a single incoming package.tar.
     * FULL FLOW:
     * - read outer tar
     * - decrypt manifest
     * - verify md5(data.tar)
     * - unpack + deliver to downstream.out
     * - audit + archive
     */
    @Transactional
    public TransactionEntity importIncomingPackage(String packageId) {


    PackageEntity pkg = packageRepository.findById(packageId)
            .orElseThrow(() -> new PackageNotFoundException(packageId));

    Path tarPath = Paths.get(pkg.getPackagePath())
            .toAbsolutePath()
            .normalize();

    Path uploadRoot = Paths.get(uploadedPackagesDir)
            .toAbsolutePath()
            .normalize();

    if (!tarPath.startsWith(uploadRoot)) {
        throw new SecurityException("Package path outside UPLOAD dir: " + tarPath);
    }

    if (!Files.exists(tarPath)) {
        throw new PackageFileNotFoundException(tarPath.toString());
    }

    // 2️⃣ Start IMPORT transaction
    TransactionEntity tx =
            transactionService.startTransaction(packageId, Direction.IMPORT);

    Path workDir = Paths.get(unpackWorkDir).resolve(packageId);

    try {

        importDeliveryOrchestrator.unpackAndDeliver(tarPath, workDir);
        log.info("UNZIP SUCCESS for packageId={} into workDir={}", packageId, workDir);
        markPackageImported(pkg,"Unzip completed successfully");

        // FILES → EXTRACTED (DB)
        fileItemRepository.updateStateByPackageId(
                packageId,
                FileItemState.EXTRACTED
        );

        // ARCHIVE TAR (MOVE = delete from UPLOAD)
        Path archiveRoot = Paths.get(archiveDir)
                .toAbsolutePath()
                .normalize();

        Files.createDirectories(archiveRoot);
        Path archivedTar = archiveRoot.resolve(tarPath.getFileName());
        Files.move(
                tarPath,
                archivedTar,
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
        );

        // PACKAGE → ARCHIVED
        markPackageArchived(pkg, archivedTar, "Archived successfully" );
        // TX → COMPLETED
        transactionService.closeSuccess(tx, "IMPORTED + ARCHIVED successfully", Direction.IMPORT);
        cleanupWorkDir(workDir);
        return tx;

    } catch (Exception ex) {
        log.error("FAILED importing package {}", packageId, ex);
        transactionService.closeFailure(tx, ex.getMessage(), Direction.IMPORT);
            return tx;
        }
    }

    // ----------------------------
    // Helpers
    // ----------------------------

    private void markPackageImported(PackageEntity pkg, String notes) {
        pkg.setState(PackageState.IMPORTED);
        pkg.setNotes(notes);
        pkg.setExportedAt(Instant.now());
        packageRepository.save(pkg);
    }

    private void markPackageArchived(PackageEntity pkg,Path archivedTar, String notes) {
        pkg.setState(PackageState.ARCHIVED);
        pkg.setNotes(notes);
        pkg.setExportedAt(Instant.now());
        pkg.setPackagePath(archivedTar.toString());
        packageRepository.save(pkg);
    }

    private void cleanupWorkDir(Path workDir) {
        try {
            if (workDir != null && Files.exists(workDir)) {
                Files.walk(workDir)
                        .sorted(Comparator.reverseOrder())
                        .forEach(p -> {
                            try {
                                Files.deleteIfExists(p);
                            } catch (Exception e) {
                                log.warn("Unable to delete work path {}", p, e);
                            }
                        });
            }
        } catch (Exception e) {
            log.warn("WORK cleanup failed for {}", workDir, e);
        }
    }
}