package it.egeos.cut3g.airgap.service.packageing;

import com.google.gson.Gson;
import it.egeos.cut3g.airgap.api.Direction;
import it.egeos.cut3g.airgap.persistence.entity.FileItemEntity;
import it.egeos.cut3g.airgap.persistence.entity.PackageEntity;
import it.egeos.cut3g.airgap.persistence.enums.FileItemState;
import it.egeos.cut3g.airgap.persistence.enums.PackageState;
import it.egeos.cut3g.airgap.persistence.repo.FileItemRepository;
import it.egeos.cut3g.airgap.persistence.repo.PackageRepository;
import it.egeos.cut3g.airgap.service.crypto.CryptoService;
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

import javax.transaction.Transactional;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
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

    private final Gson gson = new Gson();

    private final AtomicLong progressiveCounter = new AtomicLong(1);

    /**
     * LATEST = on-demand packaging triggered by REST.
     * Uses PESSIMISTIC_WRITE locking via repository query.
     */
    @Transactional
    public Optional<PackageEntity> createLatestOrAutoPackage(Direction direction) {
        log.info("Start creating LATEST or AUTO package on the direction: {} ", direction.name());

        Instant start = Instant.now();

        // Lock NEW rows to avoid concurrent collisions.
        List<FileItemEntity> newFiles = fileItemRepository.findByStateForUpdate(FileItemState.NEW);
        if (newFiles.isEmpty()) {
            return Optional.empty();
        }

        long progressive = progressiveCounter.getAndIncrement();
        Instant stop = Instant.now();

        String pkgName = NameUtils.packageName(start, stop, progressive);
        String dataTarName = NameUtils.dataTarName(pkgName);

        // Mark files ACTIVE and attach to package (transactional snapshot)
        PackageEntity pkg = new PackageEntity();
        pkg.setDirection(direction);
        pkg.setProgressiveNumber(progressive);
        pkg.setTransactionStartTime(start);
        pkg.setTransactionStopTime(stop);
        pkg.setPackageName(pkgName);
        pkg.setState(PackageState.NEW);

        for (FileItemEntity f : newFiles) {
            f.setState(FileItemState.ACTIVE);
            f.setAirgapPackage(pkg);
        }
        pkg.setFiles(newFiles);

        packageRepository.save(pkg);
        fileItemRepository.saveAll(newFiles);

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

        // Finalize DB
        pkg.setMd5DataTar(md5);
        pkg.setTotalSizeBytes(newFiles.stream().mapToLong(FileItemEntity::getSizeBytes).sum());
        pkg.setPackagePath(outPath.toString());
        pkg.setState(PackageState.CREATED);

        for (FileItemEntity f : newFiles) {
            f.setState(FileItemState.PACKED);
        }
        packageRepository.save(pkg);
        fileItemRepository.saveAll(newFiles);

        log.info("Created LATEST or AUTO package {} with {} files", pkgName, newFiles.size());
        return Optional.of(pkg);
    }
}
