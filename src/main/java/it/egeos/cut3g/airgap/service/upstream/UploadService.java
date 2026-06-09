package it.egeos.cut3g.airgap.service.upstream;

import it.egeos.cut3g.airgap.api.dto.*;
import it.egeos.cut3g.airgap.exceptions.DownstreamIOException;
import it.egeos.cut3g.airgap.exceptions.PackageFileNotFoundException;
import it.egeos.cut3g.airgap.exceptions.PackageNotFoundException;
import it.egeos.cut3g.airgap.exceptions.SequenceMismatchException;
import it.egeos.cut3g.airgap.persistence.entity.PackageEntity;
import it.egeos.cut3g.airgap.persistence.entity.TransactionEntity;
import it.egeos.cut3g.airgap.persistence.entity.UploadPackageEntity;
import it.egeos.cut3g.airgap.persistence.entity.UploadSequenceEntity;
import it.egeos.cut3g.airgap.persistence.enums.Direction;
import it.egeos.cut3g.airgap.persistence.enums.PackageState;
import it.egeos.cut3g.airgap.persistence.enums.TransactionState;
import it.egeos.cut3g.airgap.persistence.enums.UploadPackageStatus;
import it.egeos.cut3g.airgap.persistence.repo.*;
import it.egeos.cut3g.airgap.service.files.TransactionService;
import it.egeos.cut3g.airgap.service.importing.IncomingPackageImportService;
import it.egeos.cut3g.airgap.service.tar.TarListingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.persistence.EntityManager;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.*;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static it.egeos.cut3g.airgap.service.util.PackageNameParser.extractSequence;

@Service
public class UploadService {

    private static final Logger log = LoggerFactory.getLogger(UploadService.class);

    @Value("${airgap.uploaded.packages.dir}")
    private String uploadedDir;

    @Autowired
    private PackageRepository packageRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private IncomingPackageImportService incomingPackageImportService;

    @Autowired
    private UploadPackageRepository uploadPackageRepository;

    @Autowired
    private UploadFileRepository uploadFileRepository;

    @Autowired
    private UploadSequenceRepository uploadSequenceRepository;

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private EntityManager entityManager;

    public UploadPackageEntity uploadPackage(String packageName, String username, boolean contingency) {
        Path uploadedRoot = Paths.get(uploadedDir).toAbsolutePath().normalize();
        Path tarPath = uploadedRoot.resolve(packageName).normalize();

        if (!tarPath.startsWith(uploadedRoot)) {
            throw new SecurityException("Package path outside upload dir: " + tarPath);
        }

        if (!Files.exists(tarPath)) {
            throw new IllegalArgumentException("Package file not found: " + tarPath);
        }

        log.info("UPLOAD REQUEST packageName={} path={}", packageName, tarPath);

        return incomingPackageImportService.importUploadedPackage(tarPath, username, contingency, null, null);
    }

    public UploadPackageEntity dragAndDrop(MultipartFile zipFile, String username, boolean contingency) throws IOException {
        Path uploadedRoot = Paths.get(uploadedDir).toAbsolutePath().normalize();
        Files.createDirectories(uploadedRoot);
        String originalFilename = zipFile.getOriginalFilename();

        if (originalFilename == null || !originalFilename.endsWith(".tar")) {
            throw new IllegalArgumentException("Only .tar files are allowed");
        }

        Path targetPath = uploadedRoot.resolve(originalFilename).normalize();

        if (!targetPath.startsWith(uploadedRoot)) {
            throw new SecurityException("Invalid path");
        }

        log.info("UPLOAD (MULTIPART) saving file={} to={}", originalFilename, targetPath);
        zipFile.transferTo(targetPath);
        log.info("UPLOAD (MULTIPART) saved file={}, starting import", originalFilename);

        return incomingPackageImportService.importUploadedPackage(targetPath, username, contingency, null, null);
    }

    public UpstreamStatusResponse status() {
        UpstreamStatusResponse out = new UpstreamStatusResponse();

        out.transactionOngoing = transactionRepository.existsByState(TransactionState.STARTED);

        List<UploadPackageEntity> received = uploadPackageRepository.findByStatus(UploadPackageStatus.RECEIVED);
        out.readyForUploadCount = received.size();

        Optional<UploadPackageEntity> last = uploadPackageRepository.findAll().stream().max((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()));

        last.ifPresent(pkg -> {
            out.lastPackageId = pkg.getId();
            out.lastPackageState = pkg.getStatus().name();
            out.lastTransactionStart = pkg.getCreatedAt();
            out.lastPackageName = pkg.getPackageName();
        });
        return out;
    }

    public List<UploadPackageDto> listOfUploadPackages() {
        List<UploadPackageEntity> pkgs = uploadPackageRepository.findAll();
        return pkgs.stream()
            .sorted(Comparator.comparing(
                    UploadPackageEntity::getCreatedAt, 
                    Comparator.nullsLast(Comparator.reverseOrder())
            ))
            .map(UploadPackageDto::from)
            .collect(Collectors.toList());
    }

    public PackageContentResponse tarContent(String packageId) {
            UploadPackageEntity pkg = uploadPackageRepository.findById(packageId)
                    .orElseThrow(() -> new PackageNotFoundException(packageId));

            List<FileContentDto> files = uploadFileRepository
                    .findByUploadPackageIdOrderByRelativePathAsc(packageId)
                    .stream()
                    .map(f -> new FileContentDto(
                            f.getRelativePath().startsWith("/") ? f.getRelativePath() : "/" + f.getRelativePath(),
                            f.getSizeBytes()
                    ))
                    .collect(Collectors.toList());

            PackageContentResponse resp = new PackageContentResponse();
            resp.packageId = pkg.getId();
            resp.packageName = pkg.getPackageName();
            resp.files = files;

            return resp;
    }

    public UploadPackageEntity streamUpload(InputStream inputStream, String filename, String username, boolean contingency) throws IOException {

        Path uploadedRoot = Paths.get(uploadedDir).toAbsolutePath().normalize();

        Files.createDirectories(uploadedRoot);

        if (filename == null || !filename.endsWith(".tar")) {
            throw new IllegalArgumentException("Only .tar files are allowed");
        }

        Path targetPath = uploadedRoot.resolve(filename).normalize();

        if (!targetPath.startsWith(uploadedRoot)) {
            throw new SecurityException("Invalid path");
        }

        UploadPackageEntity uploadPkg = createProcessingPackage(filename, username);

        try {
            long sequence = validateOnly(filename, contingency);

            uploadPkg.setSequenceIndex(sequence);
            uploadPkg.setNote("Sequence pre-check passed");
            uploadPkg = uploadPackageRepository.saveAndFlush(uploadPkg);

        } catch (Exception ex) {
            uploadPkg.setStatus(UploadPackageStatus.FAILED);
            uploadPkg.setNote(ex.getMessage());
            uploadPackageRepository.saveAndFlush(uploadPkg);
            throw ex;
        }

        TransactionEntity tx = transactionService.startUploadTransaction(uploadPkg, Direction.UPSTREAM, username);

        try {

            log.info("STREAM UPLOAD START file={} target={} uploadPackageId={}", filename, targetPath, uploadPkg.getId());

            long totalBytes = 0;
            long nextLogBytes = 100L * 1024 * 1024;

            try (InputStream in = inputStream; OutputStream out = Files.newOutputStream(targetPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {

                byte[] buffer = new byte[1024 * 1024];
                int read;

                while ((read = in.read(buffer)) != -1) {

                    out.write(buffer, 0, read);

                    totalBytes += read;

                    if (totalBytes >= nextLogBytes) {

                        log.info("STREAM UPLOAD PROGRESS file={} mb={}", filename, totalBytes / 1024 / 1024);

                        nextLogBytes += 100L * 1024 * 1024;
                    }
                }

                out.flush();
            }

            if (totalBytes == 0) {
                throw new IOException("Empty stream");
            }

            uploadPkg.setOriginalTarPath(targetPath.toString());
            uploadPkg.setTotalSizeBytes(totalBytes);
            uploadPkg.setNote("Stream upload completed");

            uploadPkg = uploadPackageRepository.saveAndFlush(uploadPkg);

            log.info("STREAM UPLOAD COMPLETE file={} sizeBytes={} uploadPackageId={}", filename, totalBytes, uploadPkg.getId());

            UploadPackageEntity result =
                    incomingPackageImportService.importUploadedPackage(
                            targetPath,
                            username,
                            contingency,
                            uploadPkg.getId(),
                            tx.getId());

            uploadPackageRepository.flush();

            entityManager.clear();

            log.warn("FINAL RESULT STATUS={} NOTE={}",
                    result.getStatus(),
                    result.getNote());

            return result;
        } catch (Exception ex) {

            uploadPkg.setStatus(UploadPackageStatus.FAILED);
            uploadPkg.setNote(ex.getMessage());

            uploadPackageRepository.saveAndFlush(uploadPkg);

            if (tx != null) {
                transactionService.closeFailure(tx.getId(), ex.getMessage(), username);
            }

            log.error("STREAM UPLOAD FAILED file={} uploadPackageId={}", filename, uploadPkg.getId(), ex);

            throw new RuntimeException("Stream upload failed for " + filename + ": " + ex.getMessage(), ex);
        }
    }

    public UploadPackageEntity createProcessingPackage(String packageName, String username) {
        UploadPackageEntity existing = uploadPackageRepository.findByPackageName(packageName).orElse(null);

        if (existing != null) {
            if (existing.getStatus() != UploadPackageStatus.FAILED && existing.getStatus() != UploadPackageStatus.REJECTED) {
                throw new IllegalStateException("Package already exists with status " + existing.getStatus() + ": " + packageName);
            }

            existing.setStatus(UploadPackageStatus.PROCESSING);
            existing.setNote("Re-upload started");
            existing.setOriginalTarPath(null);
            existing.setArchivedTarPath(null);
            existing.setWorkDirPath(null);
            existing.setOuterDirPath(null);
            existing.setDataDirPath(null);
            existing.setManifestRelativePath(null);
            existing.setManifestMd5DataTar(null);
            existing.setImportedAt(Instant.now());
            existing.setArchivedAt(null);
            existing.setSequenceIndex(null);
            existing.setFileCount(null);
            existing.setTotalSizeBytes(null);
            existing.setUploadedBy(username != null ? username : "MANUAL");

            uploadFileRepository.deleteByUploadPackageId(existing.getId());

            return uploadPackageRepository.saveAndFlush(existing);
        }

        UploadPackageEntity pkg = new UploadPackageEntity();
        pkg.setPackageName(packageName);
        pkg.setStatus(UploadPackageStatus.PROCESSING);
        pkg.setNote("Stream upload started");
        pkg.setImportedAt(Instant.now());
        pkg.setUploadedBy(username != null ? username : "MANUAL");

        return uploadPackageRepository.saveAndFlush(pkg);
    }

    public long validateOnly(String packageName, boolean contingency) {
        long incomingSequence = extractSequence(packageName);

        UploadSequenceEntity seq = uploadSequenceRepository.findByIdForUpdate(1L)
                .orElseThrow(() -> new IllegalStateException("Upload sequence row id=1 not found"));

        long current = seq.getLastSequenceIndex();
        long expected = current + 1;

        if (!contingency && incomingSequence != expected) {
            throw new SequenceMismatchException(
                    "Package rejected. Expected sequence "
                            + String.format("%06d", expected)
                            + " but received "
                            + String.format("%06d", incomingSequence)
            );
        }

        if (contingency && incomingSequence <= current) {
            throw new SequenceMismatchException(
                    "Package rejected in contingency mode. Current sequence is "
                            + String.format("%06d", current)
                            + " but received old package "
                            + String.format("%06d", incomingSequence)
            );
        }

        return incomingSequence;
    }
}
