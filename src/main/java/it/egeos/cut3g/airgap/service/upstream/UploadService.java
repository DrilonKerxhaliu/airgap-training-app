package it.egeos.cut3g.airgap.service.upstream;

import it.egeos.cut3g.airgap.api.dto.UpstreamStatusResponse;
import it.egeos.cut3g.airgap.exceptions.DownstreamIOException;
import it.egeos.cut3g.airgap.exceptions.PackageFileNotFoundException;
import it.egeos.cut3g.airgap.exceptions.PackageNotFoundException;
import it.egeos.cut3g.airgap.persistence.entity.PackageEntity;
import it.egeos.cut3g.airgap.persistence.entity.TransactionEntity;
import it.egeos.cut3g.airgap.persistence.enums.Direction;
import it.egeos.cut3g.airgap.persistence.enums.PackageState;
import it.egeos.cut3g.airgap.persistence.enums.TransactionState;
import it.egeos.cut3g.airgap.persistence.repo.PackageRepository;
import it.egeos.cut3g.airgap.persistence.repo.TransactionRepository;
import it.egeos.cut3g.airgap.service.files.TransactionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class UploadService {

    private static final Logger log = LoggerFactory.getLogger(UploadService.class);

    @Value("${airgap.incoming.packages.dir}")
    private String incomingDir;

    @Value("${airgap.uploaded.packages.dir}")
    private String uploadedDir;

    @Autowired
    private PackageRepository packageRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private TransactionService transactionService;

    @Transactional
    public PackageEntity uploadPackage(String packageId) {
        PackageEntity pkg = packageRepository.findById(packageId)
                .orElseThrow(() -> new PackageNotFoundException(packageId));


        if (pkg.getState() == PackageState.UPLOADED) {
            throw new IllegalStateException("Package already uploaded: " + packageId);
        }

        Path sourcePath = Paths.get(pkg.getPackagePath());

        if (!Files.exists(sourcePath)) {
            throw new PackageFileNotFoundException(sourcePath.toString());
        }

        TransactionEntity tx = transactionService.startTransaction(pkg.getId(), Direction.UPSTREAM);

        try {
            Path incomingBase = Paths.get(incomingDir).toAbsolutePath().normalize();
            Path sourceAbs = sourcePath.toAbsolutePath().normalize();

            if (!sourceAbs.startsWith(incomingBase)) {
                throw new SecurityException("Invalid source path: " + sourceAbs);
            }
        } catch (Exception e) {
            throw new DownstreamIOException("Invalid source path validation", e);
        }

        try {
            Path targetDir = Paths.get(uploadedDir);
            Files.createDirectories(targetDir);

            Path targetPath = targetDir.resolve(sourcePath.getFileName());

            log.info("Uploading package {} from {} to {}", packageId, sourcePath, targetPath);

            Files.move(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);

            pkg.setPackagePath(targetPath.toString());
            pkg.setState(PackageState.UPLOADED);
            pkg.setExportedAt(Instant.now());

            PackageEntity saved = packageRepository.save(pkg);

            transactionService.closeSuccess(tx, "SUCCESS: Uploaded successfully", Direction.UPSTREAM);

            log.info("Package {} uploaded successfully", packageId);

            return saved;

        } catch (IOException e) {
            transactionService.closeFailure(tx, "FAILED: Upload error", Direction.UPSTREAM);
            log.error("Error while uploading package {}", packageId, e);
            throw new DownstreamIOException("Error while uploading package " + packageId, e);
        }
    }

    public UpstreamStatusResponse status() {
        UpstreamStatusResponse out = new UpstreamStatusResponse();

        out.transactionOngoing = transactionRepository.existsByState(TransactionState.STARTED);
        out.readyForUploadCount = packageRepository.findByStates(List.of(PackageState.CREATED)).size();

        transactionRepository.findTopByOrderByStartTsDesc().ifPresent(tx -> {
            out.lastTransactionStart = tx.getStartTs();

            if (tx.getAirgapPackage() != null) {
                String pkgId = tx.getAirgapPackage().getId();
                out.lastPackageId = pkgId;
                out.lastPackageState = resolvePackageState(pkgId).orElse(null);
            } else {
                out.lastPackageId = null;
                out.lastPackageState = null;
            }
        });

        return out;

    }

    private Optional<String> resolvePackageState(String packageId) {
        if (packageId == null) return Optional.empty();
        return packageRepository.findById(packageId).map(p -> p.getState().name());
    }
}
