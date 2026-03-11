package it.egeos.cut3g.airgap.service.upstream;

import it.egeos.cut3g.airgap.api.dto.PackageDto;
import it.egeos.cut3g.airgap.api.dto.UploadPackageDto;
import it.egeos.cut3g.airgap.api.dto.UpstreamStatusResponse;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

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
    private IncomingPackageImportService incomingPackageImportService;

    @Autowired
    private UploadPackageRepository uploadPackageRepository;

    public UploadPackageEntity uploadPackage(String packageName, String username) {
        Path uploadedRoot = Paths.get(uploadedDir).toAbsolutePath().normalize();
        Path tarPath = uploadedRoot.resolve(packageName).normalize();

        if (!tarPath.startsWith(uploadedRoot)) {
            throw new SecurityException("Package path outside upload dir: " + tarPath);
        }

        if (!Files.exists(tarPath)) {
            throw new IllegalArgumentException("Package file not found: " + tarPath);
        }

        log.info("UPLOAD REQUEST packageName={} path={}", packageName, tarPath);

        return incomingPackageImportService.importUploadedPackage(tarPath, username);
    }

    public UpstreamStatusResponse status() {
        UpstreamStatusResponse out = new UpstreamStatusResponse();

        out.transactionOngoing = transactionRepository.existsByState(TransactionState.STARTED);

        List<UploadPackageEntity> received = uploadPackageRepository.findByStatus(UploadPackageStatus.RECEIVED);
        out.readyForUploadCount = received.size();

        Optional<UploadPackageEntity> last = uploadPackageRepository.findAll().stream()
                .max((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()));

        last.ifPresent(pkg -> {
            out.lastPackageId = pkg.getId();
            out.lastPackageState = pkg.getStatus().name();
            out.lastTransactionStart = pkg.getCreatedAt();
        });
        return out;
    }

    public List<UploadPackageDto> listReadyForDownload() {
        List<UploadPackageEntity> pkgs = uploadPackageRepository.findAll();
        return pkgs.stream().map(UploadPackageDto::from).collect(Collectors.toList());
    }
}
