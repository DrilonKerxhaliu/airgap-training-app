package it.egeos.cut3g.airgap.service.downstream;

import it.egeos.cut3g.airgap.api.dto.*;
import it.egeos.cut3g.airgap.exceptions.DownstreamIOException;
import it.egeos.cut3g.airgap.exceptions.NoNewFilesToPackageException;
import it.egeos.cut3g.airgap.exceptions.PackageFileNotFoundException;
import it.egeos.cut3g.airgap.exceptions.PackageNotFoundException;
import it.egeos.cut3g.airgap.persistence.entity.PackageEntity;
import it.egeos.cut3g.airgap.persistence.entity.TransactionEntity;
import it.egeos.cut3g.airgap.persistence.enums.FileItemState;
import it.egeos.cut3g.airgap.persistence.enums.PackageState;
import it.egeos.cut3g.airgap.persistence.enums.TransactionState;
import it.egeos.cut3g.airgap.persistence.repo.FileItemRepository;
import it.egeos.cut3g.airgap.persistence.repo.PackageRepository;
import it.egeos.cut3g.airgap.persistence.repo.TransactionRepository;
import it.egeos.cut3g.airgap.service.files.FileDiscoveryService;
import it.egeos.cut3g.airgap.service.packageing.PackagingService;
import it.egeos.cut3g.airgap.service.tar.TarExtractService;
import it.egeos.cut3g.airgap.service.tar.TarListingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
import java.util.stream.Stream;

import static org.springframework.http.HttpStatus.*;

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
public ResponseEntity<FileSystemResource> downloadExistingPackage(String packageId) {
    PackageEntity pkg = packageRepository.findById(packageId)
            .orElseThrow(() -> new PackageNotFoundException(packageId));

        Path tarPath = Paths.get(pkg.getPackagePath());
        TransactionEntity tx = startTransaction(pkg.getId());
        try {
            markExported(pkg);
            closeTransactionSuccess(tx, "Downloaded existing package");
            return buildDownloadResponse(tarPath);
        } catch (RuntimeException ex) {
            closeTransactionFailure(tx, ex.getMessage());
            throw ex;
        }
        catch (IOException e) {
        throw new DownstreamIOException("Unable to stream tar", e);
        }
    }

    @Transactional
    public ResponseEntity<FileSystemResource> generateAndDeliverLatest() {
        TransactionEntity tx = new TransactionEntity();
        try {
            Optional<PackageEntity> created = packagingService.createLatestOrAutoPackage();
            if (created.isEmpty()) {
                throw new NoNewFilesToPackageException();
            }
            PackageEntity pkg = created.get();
            tx = startTransaction(pkg.getId());
            tx.setAirgapPackage(pkg);
            transactionRepository.save(tx);

            Path tarPath = Paths.get(pkg.getPackagePath());
            if (!Files.exists(tarPath)) {
                throw new PackageFileNotFoundException(tarPath.toString());
            }

            markExported(pkg);
            closeTransactionSuccess(tx, "Generated and downloaded latest package");
            return buildDownloadResponse(tarPath);

        } catch (RuntimeException ex) {
            closeTransactionFailure(tx, ex.getMessage());
            throw ex;
        } catch (IOException e) {
            throw new RuntimeException(e);
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

    private TransactionEntity startTransaction(String packageId) {
        TransactionEntity tx = new TransactionEntity();
        PackageEntity pkg = packageRepository.getReferenceById(packageId);
            tx.setAirgapPackage(pkg);
            tx.setStartTs(Instant.now());
            tx.setState(TransactionState.STARTED);
            tx.setStartTs(Instant.now());
            transactionRepository.save(tx);
            log.info("Downstream TX STARTED id={} packageId={}", tx.getId(), packageId);
        return tx;
    }

    private void closeTransactionSuccess(TransactionEntity tx, String note) {
        tx.setState(TransactionState.COMPLETED);
        tx.setEndTs(Instant.now());
        tx.setNote(note);
        transactionRepository.save(tx);
        log.info("Downstream TX COMPLETED id={} packageId={}",
                tx.getId(), tx.getAirgapPackage() != null ? tx.getAirgapPackage().getId() : null
        );
    }

    private void closeTransactionFailure(TransactionEntity tx, String note) {
        try {
            tx.setState(TransactionState.FAILED);
            tx.setEndTs(Instant.now());
            tx.setNote(note);
            transactionRepository.save(tx);
        } catch (Exception e) {
            log.error("CRITICAL: Unable to persist FAILED transaction state for txId={}", tx.getId(), e);
        }
    }

    private void markExported(PackageEntity pkg) {
        // Exported, downloaded by downstream
        if (pkg.getExportedAt() == null) {
            pkg.setExportedAt(Instant.now());
            packageRepository.save(pkg);
        }
    }

    private ResponseEntity<FileSystemResource> buildDownloadResponse(Path tarPath) throws IOException {
        FileSystemResource res = new FileSystemResource(tarPath.toFile());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDisposition(ContentDisposition.attachment().filename(tarPath.getFileName().toString()).build());
        headers.setContentLength(res.contentLength());

        return ResponseEntity.ok()
                .headers(headers)
                .body(res);
    }
}
