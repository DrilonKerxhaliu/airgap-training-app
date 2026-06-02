package it.egeos.cut3g.airgap.service.upstream;

import it.egeos.cut3g.airgap.api.dto.*;
import it.egeos.cut3g.airgap.exceptions.DownstreamIOException;
import it.egeos.cut3g.airgap.exceptions.PackageFileNotFoundException;
import it.egeos.cut3g.airgap.exceptions.PackageNotFoundException;
import it.egeos.cut3g.airgap.persistence.entity.PackageEntity;
import it.egeos.cut3g.airgap.persistence.entity.TransactionEntity;
import it.egeos.cut3g.airgap.persistence.entity.UploadPackageEntity;
import it.egeos.cut3g.airgap.persistence.enums.Direction;
import it.egeos.cut3g.airgap.persistence.enums.PackageState;
import it.egeos.cut3g.airgap.persistence.enums.TransactionState;
import it.egeos.cut3g.airgap.persistence.enums.UploadPackageStatus;
import it.egeos.cut3g.airgap.persistence.repo.PackageRepository;
import it.egeos.cut3g.airgap.persistence.repo.TransactionRepository;
import it.egeos.cut3g.airgap.persistence.repo.UploadPackageRepository;
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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.*;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

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
    private TarListingService tarListingService;

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

        return incomingPackageImportService.importUploadedPackage(tarPath, username, contingency);
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

        return incomingPackageImportService.importUploadedPackage(targetPath, username, contingency);
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
        return pkgs.stream().map(UploadPackageDto::from).collect(Collectors.toList());
    }

    public PackageContentResponse tarContent(String packageId) {
        UploadPackageEntity pkg = uploadPackageRepository.findById(packageId).orElseThrow(() -> new PackageNotFoundException(packageId));

        Path tarPath = null;
        if (pkg.getArchivedTarPath() != null) {
            tarPath = Paths.get(pkg.getArchivedTarPath());
            if (!Files.exists(tarPath)) {
                throw new PackageFileNotFoundException(tarPath.toString());
            }
        } else {
            tarPath = Paths.get(pkg.getOriginalTarPath());
            if (!Files.exists(tarPath)) {
                throw new PackageFileNotFoundException(tarPath.toString());
            }
        }

        try {
            List<FileContentDto> files = tarListingService.listFilesFromSubTars(tarPath);
            PackageContentResponse resp = new PackageContentResponse();
            resp.packageId = pkg.getId();
            resp.packageName = pkg.getPackageName();
            resp.files = files;

            return resp;

        } catch (IOException e) {
            throw new DownstreamIOException("Unable to extract/read tar content: " + e.getMessage(), e);

        }
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

        log.info("STREAM UPLOAD START file={} target={}", filename, targetPath);

        long totalBytes = 0;
        long nextLogBytes = 100L * 1024 * 1024; // 100 MB

        try (InputStream in = inputStream; OutputStream out = Files.newOutputStream(targetPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {

            int firstByte = in.read();

            if (firstByte == -1) {
                throw new IOException("Empty stream");
            }

            out.write(firstByte);
            totalBytes++;

            byte[] buffer = new byte[1024 * 1024]; // 1 MB

            int read;

            while ((read = in.read(buffer)) != -1) {

                out.write(buffer, 0, read);

                totalBytes += read;

                if (totalBytes >= nextLogBytes) {

                    log.info("STREAM UPLOAD PROGRESS file={} mb={}", filename, totalBytes / 1024 / 1024);

                    nextLogBytes += (100L * 1024 * 1024);
                }
            }

            out.flush();
        }

        log.info("STREAM UPLOAD COMPLETE file={} sizeBytes={}", filename, totalBytes);

        return incomingPackageImportService.importUploadedPackage(targetPath, username, contingency);
    }
}
