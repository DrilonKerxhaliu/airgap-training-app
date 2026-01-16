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
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
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

    @Value("${airgap.collect.in}")
    private String collectIn;

    // GET /airgap/downstream/package/list
    public List<PackageDto> listReadyForDownload() {
        List<PackageEntity> pkgs = packageRepository.findByStates(List.of(PackageState.CREATED));
        return pkgs.stream().map(PackageDto::from).collect(Collectors.toList());
    }

    // GET /airgap/downstream/package/content/{Package_ID}
    public PackageContentResponse tarContent(String packageId) {
        PackageEntity pkg = packageRepository.findById(packageId)
                .orElseThrow(() -> new PackageNotFoundException(packageId));

        Path tarPath = Paths.get(pkg.getPackagePath());
        //Unzip tar to get files
        if (!Files.exists(tarPath)) {
            throw new PackageFileNotFoundException(tarPath.toString());
        }

        try {
            PackageContentResponse pkgResp = new PackageContentResponse();
            pkgResp.packageId = pkg.getId();
            pkgResp.files = tarListingService.listEntries(tarPath);
            return pkgResp;
        } catch (IOException e) {
            throw new DownstreamIOException("Unable to read tar content: " + e.getMessage(), e);
        }
    }

    // GET /airgap/downstream/package/content/latest
    public LatestContentResponse getLatestFolderContent() {
        return fileDiscoveryService.latestFolderContentGrouped();
    }

    // GET /airgap/downstream/package/{Package_ID}
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

    // GET /airgap/downstream/package/latest
    @Transactional
    public ResponseEntity<FileSystemResource> generateAndDeliverLatest() {
        TransactionEntity tx = startTransaction("latest");

        try {
            Optional<PackageEntity> created = packagingService.createLatestOrAutoPackage();
            if (created.isEmpty()) {
                closeTransactionSuccess(tx, "No NEW files to package");
                throw new NoNewFilesToPackageException();
            }
            PackageEntity pkg = created.get();
            tx.setPackageId(pkg.getId());
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

    // GET /airgap/downstream/package/history/list
    public List<PackageDto> historyList() {
        return packageRepository.findHistory()
                .stream()
                .map(PackageDto::from)
                .collect(Collectors.toList());
    }

    // GET /airgap/downstream/statistics
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

    // GET /airgap/downstream/status
    public DownstreamStatusResponse status() {
        DownstreamStatusResponse out = new DownstreamStatusResponse();

        out.transactionOngoing = transactionRepository.existsByState(TransactionState.STARTED);
        out.newFilesCount = fileItemRepository.countByState(FileItemState.NEW);
        out.readyForDownloadCount = packageRepository.findByStates(List.of(PackageState.CREATED)).size();

        transactionRepository.findTopByOrderByStartTsDesc().ifPresent(tx -> {
            out.lastTransactionStart = tx.getStartTs();
            out.lastPackageId = tx.getPackageId();
            out.lastPackageState = resolvePackageState(tx.getPackageId()).orElse(null);
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
        tx.setPackageId(packageId);
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
        log.info("Downstream TX COMPLETED id={} packageId={}", tx.getId(), tx.getPackageId());
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

        log.error("Downstream TX FAILED id={} packageId={} note={}",
                tx.getId(), tx.getPackageId(), note);
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
