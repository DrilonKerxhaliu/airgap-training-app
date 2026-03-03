package it.egeos.cut3g.airgap.service.downstream;

import it.egeos.cut3g.airgap.api.dto.*;
import it.egeos.cut3g.airgap.exceptions.DownstreamIOException;
import it.egeos.cut3g.airgap.exceptions.NoNewFilesToPackageException;
import it.egeos.cut3g.airgap.exceptions.PackageFileNotFoundException;
import it.egeos.cut3g.airgap.exceptions.PackageNotFoundException;
import it.egeos.cut3g.airgap.persistence.entity.PackageEntity;
import it.egeos.cut3g.airgap.persistence.entity.TransactionEntity;
import it.egeos.cut3g.airgap.persistence.enums.Direction;
import it.egeos.cut3g.airgap.persistence.enums.FileItemState;
import it.egeos.cut3g.airgap.persistence.enums.PackageState;
import it.egeos.cut3g.airgap.persistence.enums.TransactionState;
import it.egeos.cut3g.airgap.persistence.repo.FileItemRepository;
import it.egeos.cut3g.airgap.persistence.repo.PackageRepository;
import it.egeos.cut3g.airgap.persistence.repo.TransactionRepository;
import it.egeos.cut3g.airgap.service.files.FileDiscoveryService;
import it.egeos.cut3g.airgap.service.files.TransactionService;
import it.egeos.cut3g.airgap.service.packaging.PackagingService;
import it.egeos.cut3g.airgap.service.tar.TarListingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
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

    public List<PackageDto> listReadyForDownload() {
        List<PackageEntity> pkgs = packageRepository.findByStates(List.of(PackageState.CREATED));
        return pkgs.stream().map(PackageDto::from).collect(Collectors.toList());
    }

    public PackageContentResponse tarContent(String packageId) {
        PackageEntity pkg = packageRepository.findById(packageId)
                .orElseThrow(() -> new PackageNotFoundException(packageId));

        Path tarPath = Paths.get(pkg.getPackagePath());
        if (!Files.exists(tarPath)) {
            throw new PackageFileNotFoundException(tarPath.toString());
        }

        try{
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

    public LatestContentResponse getLatestFolderContent() {
        return fileDiscoveryService.latestFolderContentGrouped();
    }

    @Transactional
public ResponseEntity<FileSystemResource> downloadExistingPackage(String packageId, String username) {
    PackageEntity pkg = packageRepository.findById(packageId)
            .orElseThrow(() -> new PackageNotFoundException(packageId));

        Path tarPath = Paths.get(pkg.getPackagePath());
        TransactionEntity tx = transactionService.startTransaction(pkg.getId(), Direction.DOWNSTREAM, username);
        try {
            markExported(pkg);
            transactionService.closeSuccess(tx.getId(), "SUCCESS: Downloaded successfully", Direction.DOWNSTREAM);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + pkg.getPackageName() + ".tar\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .contentLength(Files.size(tarPath))
                    .body(new FileSystemResource(tarPath));
        } catch (RuntimeException ex) {
            transactionService.closeFailure(tx.getId(), "FAILED: Download error", Direction.DOWNSTREAM, username);
            throw ex;
        }
        catch (IOException e) {
        throw new DownstreamIOException("Unable to stream tar", e);
        }
    }

    @Transactional
    public PackageEntity generateAndDeliverLatest(String username) {
        TransactionEntity tx = new TransactionEntity();
        try {
            Optional<PackageEntity> created = packagingService.createLatestOrAutoPackage(username);
            if (created.isEmpty()) {
                throw new NoNewFilesToPackageException();
            }
            PackageEntity pkg = created.get();
            tx = transactionService.startTransaction(pkg.getId(), Direction.DOWNSTREAM, username);

            Path tarPath = Paths.get(pkg.getPackagePath());
            if (!Files.exists(tarPath)) {
                throw new PackageFileNotFoundException(tarPath.toString());
            }

            transactionService.closeSuccess(tx.getId(), "SUCCESS: Generated and downloaded latest package", Direction.DOWNSTREAM);
            return pkg;

        } catch (RuntimeException ex) {
            transactionService.closeFailure(tx.getId(), "FAILED: Downstream transaction failed - " + ex.getMessage(), Direction.DOWNSTREAM, username);
            throw ex;
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
            pkg.setState(PackageState.PROCESSING);
            packageRepository.save(pkg);
        }
    }
}
