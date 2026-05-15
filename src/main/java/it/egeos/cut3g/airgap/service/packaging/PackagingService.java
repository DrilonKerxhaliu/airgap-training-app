package it.egeos.cut3g.airgap.service.packaging;

import com.google.gson.Gson;
import it.egeos.cut3g.airgap.api.dto.FileEventDto;
import it.egeos.cut3g.airgap.persistence.entity.FileItemEntity;
import it.egeos.cut3g.airgap.persistence.entity.PackageEntity;
import it.egeos.cut3g.airgap.persistence.enums.FileItemState;
import it.egeos.cut3g.airgap.persistence.enums.PackageState;
import it.egeos.cut3g.airgap.persistence.repo.FileItemRepository;
import it.egeos.cut3g.airgap.persistence.repo.PackageRepository;
import it.egeos.cut3g.airgap.service.crypto.CryptoService;
import it.egeos.cut3g.airgap.service.files.FileSseService;
import it.egeos.cut3g.airgap.service.manifest.ManifestFileItem;
import it.egeos.cut3g.airgap.service.manifest.PackageManifest;
import it.egeos.cut3g.airgap.service.tar.TarService;
import it.egeos.cut3g.airgap.service.util.HashUtils;
import it.egeos.cut3g.airgap.service.util.NameUtils;
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
import java.util.*;
import java.util.stream.Collectors;

/**
 * Creates packages (LATEST or AUTO).
 */
@Service
public class PackagingService {

    private static final Logger log = LoggerFactory.getLogger(PackagingService.class);
    private final Gson gson = new Gson();
    @Value("${airgap.collect.in}")
    private String collectedIn;
    @Value("${airgap.save.original.packages.dir}")
    private String packagesDir;
    @Autowired
    private FileItemRepository fileItemRepository;
    @Autowired
    private PackageRepository packageRepository;
    @Autowired
    private TarService tarService;
    @Autowired
    private CryptoService cryptoService;
    @Autowired
    private FileSseService fileSseService;

    /**
     * LATEST = on-demand packaging triggered by REST.
     * Uses PESSIMISTIC_WRITE locking via repository query.
     */

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<PackageEntity> createLatestOrAutoPackage(String username) {
        log.info("Start creating LATEST or AUTO package! ");
        // 1) Reserve NEW files -> ACTIVE (commit)
        List<FileItemEntity> reserved = reserveNewFiles();
        if (reserved.isEmpty()) {
            log.info("No NEW files to package.");
            return Optional.empty();
        }

        List<String> reservedIds = reserved.stream().map(FileItemEntity::getId).collect(Collectors.toList());

        // remove them from SSE NEW snapshot for all operators (real-time)
        fileSseService.removeFromNewByIds(reservedIds);
        fileSseService.flushNow();

        // 2) Build the package (commit). If it fails -> rollback ACTIVE->NEW (commit)
        try {
            PackageEntity pkg = buildPackageFromActiveFiles(reservedIds, username);
            return Optional.of(pkg);
        } catch (RuntimeException ex) {
            log.warn("Packaging failed, rolling back files to NEW: {}", ex.getMessage());
            rollbackActiveToNew(reservedIds);
            throw ex;
        }
    }

    /**
     * Transaction 1: lock NEW rows and reserve them (NEW -> ACTIVE).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<FileItemEntity> reserveNewFiles() {
        List<FileItemEntity> newFiles = fileItemRepository.findByStateForUpdate(FileItemState.NEW);
        if (newFiles.isEmpty()) return List.of();

        for (FileItemEntity f : newFiles) {
            f.setState(FileItemState.ACTIVE);
        }

        return fileItemRepository.saveAll(newFiles);
    }

    /**
     * Transaction 2: use reserved ACTIVE rows to create the package, then ACTIVE -> PACKED.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PackageEntity buildPackageFromActiveFiles(List<String> reservedIds, String username) {
        Instant start = Instant.now();

        // Lock by IDs to ensure no concurrent changes
        List<FileItemEntity> activeFiles = fileItemRepository.findByIdsForUpdate(reservedIds);
        activeFiles = activeFiles.stream().filter(f -> f.getState() == FileItemState.ACTIVE).collect(Collectors.toList());

        if (activeFiles.isEmpty()) {
            // Another process may have taken them, or rollback happened.
            throw new IllegalStateException("No ACTIVE files reserved for packaging");
        }

        long progressive = packageRepository.findMaxProgressiveNumber() + 1;
        Instant stop = Instant.now();

        String pkgName = NameUtils.packageName(start, stop, progressive);
        String dataTarName = NameUtils.dataTarName(pkgName);

        // Create package
        PackageEntity pkg = new PackageEntity();
        pkg.setTransactionStartTime(start);
        pkg.setTransactionStopTime(stop);
        pkg.setPackageName(pkgName);
        pkg.setState(PackageState.NEW);
        pkg.setCreatedBy(username != null ? username : "auto");

        for (FileItemEntity f : activeFiles) {
            f.setAirgapPackage(pkg);
        }
        pkg.setFiles(activeFiles);

        // Build data TAR bytes
        Path inRoot = Paths.get(collectedIn);
        List<String> relPaths = activeFiles.stream().map(FileItemEntity::getRelativePath).distinct().collect(Collectors.toList());

        Path packageDir = Paths.get(packagesDir);
        Path tempDir = packageDir.resolve(".tmp");
        Path tempDataTar = tempDir.resolve(dataTarName);
        Path outPath = packageDir.resolve(pkgName);

        try {

            Files.createDirectories(tempDir);
            Files.createDirectories(packageDir);

            // build data temp tar

            tarService.buildDataTar(inRoot, relPaths, tempDataTar);

            String md5 = HashUtils.md5Hex(tempDataTar);

            // Build manifest JSON
            PackageManifest manifest = new PackageManifest();
            manifest.setPackageName(pkgName);
            manifest.setMd5DataTar(md5);
            manifest.setFiles(activeFiles.stream().map(f -> new ManifestFileItem(f.getRelativePath(), f.getSizeBytes())).collect(Collectors.toList()));
            byte[] manifestJson = gson.toJson(manifest).getBytes(java.nio.charset.StandardCharsets.UTF_8);

            // Encrypt manifest (AES-256)
            byte[] manifestEnc = cryptoService.encrypt(manifestJson);

            // Write outer TAR to disk [final package .tar]

            tarService.writeOuterTar(outPath, "manifest.enc", manifestEnc, dataTarName, tempDataTar);

            long totalSize = activeFiles.stream().mapToLong(FileItemEntity::getSizeBytes).sum();

            pkg.setMd5DataTar(md5);
            pkg.setTotalSizeBytes(totalSize);
            pkg.setPackagePath(outPath.toString());
            pkg.setState(PackageState.CREATED);

            packageRepository.save(pkg);
            log.info("Saved package with id={}", pkg.getId());
            // ACTIVE -> PACKED (remove from NEW list already, no SSE needed)
            for (FileItemEntity f : activeFiles) {
                f.setState(FileItemState.PACKED);
            }
            fileItemRepository.saveAll(activeFiles);

            // REMOVE from SSE snapshot
            List<String> packedIds = activeFiles.stream().map(FileItemEntity::getId).collect(Collectors.toList());

            fileSseService.removeFromNewByIds(packedIds);
            fileSseService.flushNow();

            log.info("Created package {} with {} files", pkgName, activeFiles.size());
            return pkg;

        } catch (Exception ex) {

            try {
                Files.deleteIfExists(outPath);
            } catch (Exception ignored) {
            }

            throw new RuntimeException("Unable to create package: " + ex.getMessage(), ex);

        } finally {
            // delete temp tar
            try {
                Files.deleteIfExists(tempDataTar);
            } catch (Exception e) {
                log.warn("Unable to delete temp data tar {}", tempDataTar, e);
            }
        }
    }

    /**
     * Transaction 3: rollback ACTIVE -> NEW (only for the files in this attempt).
     * Also re-add them to SSE NEW snapshot.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void rollbackActiveToNew(List<String> reservedIds) {
        if (reservedIds == null || reservedIds.isEmpty()) return;

        List<FileItemEntity> files = fileItemRepository.findByIdsForUpdate(reservedIds);
        List<FileEventDto> toPublish = new ArrayList<>();

        for (FileItemEntity f : files) {
            if (f.getState() == FileItemState.ACTIVE) {
                f.setState(FileItemState.NEW);
                f.setAirgapPackage(null);

                FileEventDto dto = new FileEventDto();
                dto.setId(f.getId());
                String relativePath = f.getRelativePath() != null ? f.getRelativePath() : "";
                String[] parts = relativePath.split("/", 2);
                dto.setFolder(parts.length > 0 ? parts[0] : "");
                dto.setFilename(parts.length > 1 ? parts[1] : "");
                dto.setSizeBytes(f.getSizeBytes());
                dto.setArrivedAt(f.getReceivedTime());
                dto.setState(FileItemState.NEW);
                toPublish.add(dto);
            }
        }

        fileItemRepository.saveAll(files);

        // Update SSE snapshot and flush now
        fileSseService.addToNew(toPublish);
        fileSseService.flushNow();
    }
}
