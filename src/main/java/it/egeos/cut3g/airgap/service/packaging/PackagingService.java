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

    @Value("${airgap.collect.in}")
    private String collectedIn;

    @Value("${airgap.incoming.packages.dir}")
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

    private final Gson gson = new Gson();

    /**
     * LATEST = on-demand packaging triggered by REST.
     * Uses PESSIMISTIC_WRITE locking via repository query.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<PackageEntity> createLatestOrAutoPackage( String username) {
        log.info("Start creating LATEST or AUTO package! ");

        Instant start = Instant.now();

        // Lock NEW rows to avoid concurrent collisions.
        List<FileItemEntity> newFiles = fileItemRepository.findByStateForUpdate(FileItemState.NEW);
        if (newFiles.isEmpty()) {
            log.info("No NEW files to package.");
            return Optional.empty();
        }

        long progressive = packageRepository.findMaxProgressiveNumber() + 1;

        Instant stop = Instant.now();

        String pkgName = NameUtils.packageName(start, stop, progressive);
        String dataTarName = NameUtils.dataTarName(pkgName);

        // Mark files ACTIVE and attach to package (transactional snapshot)
        PackageEntity pkg = new PackageEntity();
        pkg.setTransactionStartTime(start);
        pkg.setTransactionStopTime(stop);
        pkg.setPackageName(pkgName);
        pkg.setState(PackageState.NEW);
        pkg.setCreatedBy(username != null ? username : "auto");

        for (FileItemEntity f : newFiles) {
            f.setAirgapPackage(pkg);
        }
        pkg.setFiles(newFiles);

        // Build data TAR bytes
        Path inRoot = Paths.get(collectedIn);
        List<String> relPaths = newFiles.stream().map(FileItemEntity::getRelativePath).collect(Collectors.toList());
        byte[] dataTarBytes = tarService.buildDataTar(inRoot, relPaths);

        String md5 = HashUtils.md5Hex(dataTarBytes);

        // Build manifest JSON
        PackageManifest manifest = new PackageManifest();
        manifest.setPackageName(pkgName);
        manifest.setMd5DataTar(md5);
        manifest.setFiles(newFiles.stream()
                .map(f -> new ManifestFileItem(f.getRelativePath(), f.getSizeBytes()))
                .collect(Collectors.toList()));
        byte[] manifestJson = gson.toJson(manifest).getBytes(java.nio.charset.StandardCharsets.UTF_8);

        // Encrypt manifest (AES-256)
        byte[] manifestEnc = cryptoService.encrypt(manifestJson);

        // Write outer TAR to disk [finale package .tar]
        Path outPath = Paths.get(packagesDir).resolve(pkgName);
        tarService.writeOuterTar(outPath, "manifest.enc", manifestEnc, dataTarName, dataTarBytes);

        // Finalize package
        long totalSize = newFiles.stream()
                .mapToLong(FileItemEntity::getSizeBytes)
                .sum();

        pkg.setMd5DataTar(md5);
        pkg.setTotalSizeBytes(totalSize);
        pkg.setPackagePath(outPath.toString());
        pkg.setState(PackageState.CREATED);

        // SAVE on DB , final package
        log.info("About to save package: path={}, md5DataTar={}, size={}",
                pkg.getPackagePath(),
                pkg.getMd5DataTar(),
                pkg.getTotalSizeBytes());

        packageRepository.save(pkg);
        log.info("Saved package with id={}", pkg.getId());
        for (FileItemEntity f : newFiles) {
            f.setState(FileItemState.PACKED);

            FileEventDto dto = new FileEventDto();
            dto.setId(f.getId());
            dto.setState(FileItemState.PACKED);

            fileSseService.publish(dto);


        }
        fileItemRepository.saveAll(newFiles);

        log.info("Created LATEST or AUTO package {} with {} files", pkgName, newFiles.size());
        return Optional.of(pkg);
    }
}
