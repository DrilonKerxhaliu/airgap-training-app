package it.egeos.cut3g.airgap.service.downstream;

import it.egeos.cut3g.airgap.api.dto.*;
import it.egeos.cut3g.airgap.exceptions.DownstreamIOException;
import it.egeos.cut3g.airgap.exceptions.PackageFileNotFoundException;
import it.egeos.cut3g.airgap.exceptions.PackageNotFoundException;
import it.egeos.cut3g.airgap.persistence.entity.PackageEntity;
import it.egeos.cut3g.airgap.persistence.entity.TransactionEntity;
import it.egeos.cut3g.airgap.persistence.entity.UploadPackageEntity;
import it.egeos.cut3g.airgap.persistence.enums.Direction;
import it.egeos.cut3g.airgap.persistence.enums.FileItemState;
import it.egeos.cut3g.airgap.persistence.enums.PackageState;
import it.egeos.cut3g.airgap.persistence.enums.TransactionState;
import it.egeos.cut3g.airgap.persistence.repo.FileItemRepository;
import it.egeos.cut3g.airgap.persistence.repo.PackageRepository;
import it.egeos.cut3g.airgap.persistence.repo.TransactionRepository;
import it.egeos.cut3g.airgap.service.files.FileDiscoveryService;
import it.egeos.cut3g.airgap.service.files.TransactionService;
import it.egeos.cut3g.airgap.service.files.TransferProtocolService;
import it.egeos.cut3g.airgap.service.packaging.PackagingService;
import it.egeos.cut3g.airgap.service.tar.TarListingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;


import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DownstreamService {

    private static final Logger log = LoggerFactory.getLogger(DownstreamService.class);

    @Autowired
    private PackagingService packagingService;

    @Autowired
    private PackageRepository packageRepository;

    @Autowired
    private FileItemRepository fileItemRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private TarListingService tarListingService;

    @Autowired
    private FileDiscoveryService fileDiscoveryService;

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private TransferProtocolService protocolService;

    public List<PackageDto> listReadyForDownload() {
        List<PackageEntity> pkgs = packageRepository.findAll();
        return pkgs.stream()
            .sorted(Comparator.comparing(
                PackageEntity::getTransactionStopTime, 
                Comparator.nullsLast(Comparator.reverseOrder())))
            .map(PackageDto::from)
            .collect(Collectors.toList());
    }

    public PackageContentResponse tarContent(String packageId) {
        PackageEntity pkg = packageRepository.findById(packageId).orElseThrow(() -> new PackageNotFoundException(packageId));

        List<FileContentDto> files = fileItemRepository.findByPackageId(packageId).stream().map(file -> new FileContentDto(file.getRelativePath(), file.getSizeBytes())).collect(Collectors.toList());

        PackageContentResponse resp = new PackageContentResponse();
        resp.packageId = pkg.getId();
        resp.packageName = pkg.getPackageName();
        resp.files = files;

        return resp;
    }

    public LatestContentResponse getLatestFolderContent() {
        return fileDiscoveryService.latestFolderContentGrouped();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ResponseEntity<FileSystemResource> downloadExistingPackage(String packageId, String username) {
        PackageEntity pkg = packageRepository.findById(packageId)
                .orElseThrow(() -> new PackageNotFoundException(packageId));

        validatePackageAvailability(pkg);
        Path tarPath = Paths.get(pkg.getPackagePath());
        TransactionEntity tx = transactionService.startTransaction(pkg.getId(), Direction.DOWNSTREAM, username);
        try {
            markExported(pkg);
            transactionService.closeSuccess(tx.getId(), "SUCCESS: Downloaded successfully");
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + pkg.getPackageName() + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .contentLength(Files.size(tarPath))
                    .body(new FileSystemResource(tarPath));
        } catch (RuntimeException ex) {
            transactionService.closeFailure(tx.getId(), "FAILED: Download error", username);
            throw ex;
        } catch (IOException e) {
            throw new DownstreamIOException("Unable to stream tar", e);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PackageEntity generateAndDeliverLatest(String username) {
        TransactionEntity tx = null;
        try {
            Optional<PackageEntity> created = packagingService.createLatestOrAutoPackage(username);
            if (created.isEmpty()) {

                transactionService.noPackageTransaction(
                        Direction.DOWNSTREAM,
                        "No NEW files available for packaging",
                        username
                );

                log.info("No NEW files to package");
                PackageEntity failed = new PackageEntity();
                failed.setState(PackageState.FAILED);
                return failed;
            }
            PackageEntity pkg = created.get();
            tx = transactionService.startTransaction(pkg.getId(), Direction.DOWNSTREAM, username);

            Path tarPath = Paths.get(pkg.getPackagePath());
            if (!Files.exists(tarPath)) {
                throw new PackageFileNotFoundException(tarPath.toString());
            }

            transactionService.closeSuccess(tx.getId(), "SUCCESS: Generated and downloaded latest package");
            return pkg;

        } catch (Exception ex) {
        if (tx != null && tx.getId() != null) {
            transactionService.closeFailure(tx.getId(), "FAILED: Downstream transaction failed - " + ex.getMessage(), username);
            } else {
             log.warn("No TX to close (failure before TX creation): {}", ex.getMessage());
            }
            throw ex;
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendPackages(List<String> packageIds, String username) {

        for (String id : packageIds) {

            PackageEntity pkg = packageRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Package not found: " + id));

            validatePackageAvailability(pkg);

            pkg.setState(PackageState.PROCESSING);

            packageRepository.save(pkg);

            transferAsync(pkg, username);
        }
    }

    public List<PackageDto> historyList() {
        return packageRepository.findHistory()
                .stream()
                .map(PackageDto::from)
                .collect(Collectors.toList());
    }

    public DownstreamStatisticsResponse statistics() {
        DownstreamStatisticsResponse out = new DownstreamStatisticsResponse();
        out.totalPackages = packageRepository.count();
        out.totalExported = packageRepository.countExported();
        out.totalReady = packageRepository.findByStates(List.of(PackageState.CREATED)).size();
        out.totalNewFiles = fileItemRepository.countByState(FileItemState.NEW);
        out.totalBytesPackaged = packageRepository.sumAllPackagedBytes();
        out.totalBytesExported = packageRepository.sumExportedBytes();
        out.lastPackageTime = packageRepository.findLastPackageTime();
        return out;
    }

    public DownstreamStatusResponse status() {
        DownstreamStatusResponse out = new DownstreamStatusResponse();

        out.transactionOngoing = transactionRepository.existsByState(TransactionState.STARTED);
        out.newFilesCount = fileItemRepository.countByState(FileItemState.NEW);
        out.readyForDownloadCount = packageRepository.findByStates(List.of(PackageState.CREATED)).size();

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

    // ----------------------------
    // Helpers
    // ----------------------------

    private Optional<String> resolvePackageState(String packageId) {
        if (packageId == null) return Optional.empty();
        return packageRepository.findById(packageId).map(p -> p.getState().name());
    }

    private void markExported(PackageEntity pkg) {
        // Exported, downloaded by downstream
        if (pkg.getExportedAt() == null) {
            pkg.setExportedAt(Instant.now());
            pkg.setState(PackageState.SENT);
            packageRepository.save(pkg);
        }
    }

    @Async
    public void transferAsync(PackageEntity pkg, String username) {
        TransactionEntity tx = transactionService.startTransaction(pkg.getId(), Direction.DOWNSTREAM, username);
        try {
            Path packagePath = Path.of(pkg.getPackagePath()).toAbsolutePath().normalize();
            protocolService.sendPackage(packagePath, pkg);
            pkg.setState(PackageState.SENT);
            pkg.setExportedAt(Instant.now());
            transactionService.closeSuccess(tx.getId(), "SUCCESS: Package transferred");
        } catch (Exception ex) {
            pkg.setState(PackageState.FAILED);
            transactionService.closeFailure(tx.getId(), "FAILED: Transfer error - " + ex.getMessage(), username);
        }
        packageRepository.save(pkg);
    }

    private void  validatePackageAvailability(PackageEntity pkg) {

        if (pkg.getPackagePath() == null ||
                pkg.getPackagePath().isBlank()) {

            log.warn(
                    "Package path missing for package={}",
                    pkg.getPackageName()
            );

            pkg.setState(PackageState.DELETED);

            packageRepository.save(pkg);

            throw new PackageFileNotFoundException(
                    pkg.getPackagePath()
            );
        }

        Path packagePath = Paths.get(pkg.getPackagePath())
                .toAbsolutePath()
                .normalize();

        if (!Files.exists(packagePath)) {

            log.warn(
                    "Package file missing -> package={} path={}",
                    pkg.getPackageName(),
                    packagePath
            );

            pkg.setState(PackageState.DELETED);

            packageRepository.save(pkg);

            throw new PackageFileNotFoundException(
                    pkg.getPackagePath()
            );
        }
    }
}
