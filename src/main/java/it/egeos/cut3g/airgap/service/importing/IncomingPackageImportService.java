package it.egeos.cut3g.airgap.service.importing;

import it.egeos.cut3g.airgap.api.dto.ImportDeliveryResult;
import it.egeos.cut3g.airgap.persistence.entity.TransactionEntity;
import it.egeos.cut3g.airgap.persistence.entity.UploadFileEntity;
import it.egeos.cut3g.airgap.persistence.entity.UploadPackageEntity;
import it.egeos.cut3g.airgap.persistence.enums.Direction;
import it.egeos.cut3g.airgap.persistence.enums.UploadFileStatus;
import it.egeos.cut3g.airgap.persistence.enums.UploadPackageStatus;
import it.egeos.cut3g.airgap.persistence.repo.UploadFileRepository;
import it.egeos.cut3g.airgap.persistence.repo.UploadPackageRepository;
import it.egeos.cut3g.airgap.service.files.TransactionService;
import it.egeos.cut3g.airgap.service.manifest.ManifestFileItem;
import it.egeos.cut3g.airgap.service.manifest.PackageManifest;
import it.egeos.cut3g.airgap.service.upstream.UploadNewFileService;
import it.egeos.cut3g.airgap.service.upstream.UploadNewPackageService;
import it.egeos.cut3g.airgap.service.upstream.UploadSequenceService;
import it.egeos.cut3g.airgap.service.util.PackageNameParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import java.nio.file.*;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
public class IncomingPackageImportService {

    private static final Logger log = LoggerFactory.getLogger(IncomingPackageImportService.class);

    @Autowired
    private ImportDeliveryOrchestrator importDeliveryOrchestrator;

    @Autowired
    private UploadPackageRepository uploadPackageRepository;

    @Autowired
    private UploadFileRepository uploadFileRepository;

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private UploadSequenceService uploadSequenceService;

    @Autowired
    private PackageNameParser packageNameParser;

    @Autowired
    private UploadNewPackageService uploadNewPackageService;

    @Autowired
    private UploadNewFileService uploadNewFileService;

    @Autowired
    private EntityManager entityManager;

    @Value("${airgap.unpack.work.dir}")
    private String unpackWorkDir;

    @Value("${airgap.archive.packages.dir}")
    private String archiveDir;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UploadPackageEntity importUploadedPackage(Path tarPath, String username) {

        Path normalizedTar = tarPath.toAbsolutePath().normalize();
        String packageName = normalizedTar.getFileName().toString();
        long sequence = packageNameParser.extractSequence(packageName);

        UploadPackageEntity uploadPkg = new UploadPackageEntity();

        uploadPkg = uploadPackageRepository
                .findByPackageName(packageName)
                .orElse(null);

        boolean reprocess = false;

        if (uploadPkg != null) {

            if (uploadPkg.getStatus() == UploadPackageStatus.FAILED
                    || uploadPkg.getStatus() == UploadPackageStatus.REJECTED) {

                log.warn("REPROCESSING PACKAGE package={} previousStatus={}",
                        packageName, uploadPkg.getStatus());

                reprocess = true;

                // cleanup previous files
                uploadFileRepository.deleteByUploadPackageId(uploadPkg.getId());

                // reset fields
                uploadPkg.setImportedAt(null);
                uploadPkg.setArchivedAt(null);
                uploadPkg.setArchivedTarPath(null);
                uploadPkg.setFileCount(null);
                uploadPkg.setTotalSizeBytes(null);
                uploadPkg.setOuterDirPath(null);
                uploadPkg.setDataDirPath(null);
                uploadPkg.setManifestRelativePath(null);
                uploadPkg.setManifestMd5DataTar(null);

            } else {
                throw new IllegalStateException(
                        "Package already processed with status "
                                + uploadPkg.getStatus() + ": " + packageName
                );
            }
        }

        if (!reprocess) {

            uploadSequenceService.reserveNext(sequence);

            uploadPkg = new UploadPackageEntity();
            uploadPkg.setPackageName(packageName);
            uploadPkg.setSequenceIndex(sequence);
            uploadPkg.setOriginalTarPath(normalizedTar.toString());
            uploadPkg.setStatus(UploadPackageStatus.RECEIVED);
            uploadPkg.setUploadedBy(username != null ? username : "auto");
            uploadPkg.setNote("Package received for import");
            uploadPkg = uploadNewPackageService.saveNewPackage(uploadPkg);

        } else {
            uploadPkg.setStatus(UploadPackageStatus.RECEIVED);
            uploadPkg.setNote("Reprocessing package");
            uploadPkg = uploadNewPackageService.saveNewPackage(uploadPkg);
        }

        TransactionEntity tx = transactionService.startUploadTransaction(
                uploadPkg,
                Direction.UPSTREAM,
                username
        );

        Path workDir = Paths.get(unpackWorkDir).resolve(uploadPkg.getId()).toAbsolutePath().normalize();
        uploadPkg.setWorkDirPath(workDir.toString());

        try {
            Files.createDirectories(workDir);

            uploadPkg.setStatus(UploadPackageStatus.SEQUENCE_VALIDATED);
            uploadPkg.setNote("Sequence validated successfully");
            uploadPkg = uploadNewPackageService.saveNewPackage(uploadPkg);

            uploadPkg.setStatus(UploadPackageStatus.UNPACKING);
            uploadPkg.setNote("Unpacking package");
            uploadPkg = uploadNewPackageService.saveNewPackage(uploadPkg);

            ImportDeliveryResult result = importDeliveryOrchestrator.unpackAndDeliver(normalizedTar, workDir);
            PackageManifest manifest = result.getManifest();

            uploadPkg.setOuterDirPath(result.getOuterDir().toString());
            uploadPkg.setDataDirPath(result.getDataDir().toString());
            uploadPkg.setManifestRelativePath("manifest.enc");
            uploadPkg.setManifestMd5DataTar(manifest.getMd5DataTar());
            uploadPkg.setStatus(UploadPackageStatus.UNPACKED);
            uploadPkg.setNote("Package unpacked and validated");
            uploadPkg = uploadNewPackageService.saveNewPackage(uploadPkg);

            persistFilesFromManifest(uploadPkg, manifest, result);

            uploadPkg.setStatus(UploadPackageStatus.DELIVERING);
            uploadPkg.setNote("Files delivered to collect/out");
            uploadPkg = uploadNewPackageService.saveNewPackage(uploadPkg);

            finalizePackageStats(uploadPkg);

            uploadPkg.setStatus(UploadPackageStatus.IMPORTED);
            uploadPkg.setImportedAt(Instant.now());
            uploadPkg.setNote("Import completed successfully");
            uploadPkg = uploadNewPackageService.saveNewPackage(uploadPkg);

            archiveTar(normalizedTar, uploadPkg);
            transactionService.closeSuccess(tx.getId(), "UPLOAD IMPORT SUCCESS");

            cleanupWorkDir(workDir);

            log.info("UPLOAD IMPORT SUCCESS package={} sequence={} uploadPackageId={}",
                    packageName, sequence, uploadPkg.getId());

            return uploadPkg;

        } catch (Exception ex) {
            uploadPkg.setStatus(UploadPackageStatus.FAILED);
            uploadPkg.setNote(ex.getMessage());
            uploadPkg = uploadNewPackageService.saveNewPackage(uploadPkg);

            markFilesFailed(uploadPkg.getId(), ex.getMessage());

            if (tx != null) {
                transactionService.closeFailure(tx.getId(), ex.getMessage(), username);
            }

            log.error("UPLOAD IMPORT FAILED package={} uploadPackageId={}", packageName, uploadPkg.getId(), ex);
            throw new RuntimeException("Upload import failed for " + packageName + ": " + ex.getMessage(), ex);
        }
    }

    private void persistFilesFromManifest(UploadPackageEntity uploadPkg,
                                          PackageManifest manifest,
                                          ImportDeliveryResult result) {

        Map<String, Path> extractedFiles = result.getExtractedFiles();
        Map<String, Path> deliveredFiles = result.getDeliveredFiles();

        for (ManifestFileItem item : manifest.getFiles()) {
            String relative = item.getRelativePath().replace("\\", "/");
            Path extractedPath = extractedFiles.get(relative);
            Path deliveredPath = deliveredFiles.get(relative);

            UploadFileEntity file = new UploadFileEntity();
            file.setUploadPackage(uploadPkg);
            file.setRelativePath(relative);
            file.setExtractedAbsolutePath(extractedPath != null ? extractedPath.toString() : null);
            file.setDeliveredAbsolutePath(deliveredPath != null ? deliveredPath.toString() : null);
            file.setSizeBytes(item.getSizeBytes());
       //     file.setChecksumMd5(item.getMd5());
            file.setStatus(UploadFileStatus.DELIVERED);
            file.setNote("File imported and delivered");

            uploadNewFileService.saveNewFile(file);

            log.info("UPLOAD FILE SAVED uploadPackageId={} relativePath={} deliveredPath={}",
                    uploadPkg.getId(), relative, deliveredPath);
        }
    }

    private void finalizePackageStats(UploadPackageEntity uploadPkg) {
        List<UploadFileEntity> files = uploadFileRepository.findByUploadPackageId(uploadPkg.getId());

        long totalSize = files.stream().mapToLong(UploadFileEntity::getSizeBytes).sum();
        int count = files.size();

        uploadPkg.setFileCount(count);
        uploadPkg.setTotalSizeBytes(totalSize);
        uploadNewPackageService.saveNewPackage(uploadPkg);
    }

    private void archiveTar(Path originalTar, UploadPackageEntity uploadPkg) throws Exception {
        Path archiveRoot = Paths.get(archiveDir).toAbsolutePath().normalize();
        Files.createDirectories(archiveRoot);

        Path archivedTar = archiveRoot.resolve(originalTar.getFileName()).normalize();
        Files.move(originalTar, archivedTar, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);

        uploadPkg.setArchivedTarPath(archivedTar.toString());
        uploadPkg.setArchivedAt(Instant.now());
        uploadPkg.setStatus(UploadPackageStatus.ARCHIVED);
        uploadPkg.setNote("Tar archived successfully");
        uploadPkg = uploadNewPackageService.saveNewPackage(uploadPkg);

        log.info("UPLOAD TAR ARCHIVED uploadPackageId={} archivedTar={}",
                uploadPkg.getId(), archivedTar);
    }

    private void markFilesFailed(String uploadPackageId, String note) {
        List<UploadFileEntity> files = uploadFileRepository.findByUploadPackageId(uploadPackageId);
        for (UploadFileEntity file : files) {
            file.setStatus(UploadFileStatus.FAILED);
            file.setNote(note);
            uploadNewFileService.saveNewFile(file);
        }
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