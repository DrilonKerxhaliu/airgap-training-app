package it.egeos.cut3g.airgap.service.downstream;

import it.egeos.cut3g.airgap.persistence.entity.PackageEntity;
import it.egeos.cut3g.airgap.persistence.entity.TransactionEntity;
import it.egeos.cut3g.airgap.persistence.enums.Direction;
import it.egeos.cut3g.airgap.persistence.enums.PackageState;
import it.egeos.cut3g.airgap.persistence.repo.PackageRepository;
import it.egeos.cut3g.airgap.service.files.TransactionService;
import it.egeos.cut3g.airgap.service.files.TransferProtocolService;
import it.egeos.cut3g.airgap.service.util.RuntimeConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class PackageAutoSendService {

    private static final Logger log = LoggerFactory.getLogger(PackageAutoSendService.class);

    private static final long AUTO_SEND_DELAY_MS = Duration.ofSeconds(10).toMillis();
    private static final String AUTO_USERNAME = "AUTO";

    @Autowired
    private RuntimeConfigService runtimeConfigService;

    @Autowired
    private PackageRepository packageRepository;

    @Autowired
    private TransferProtocolService protocolService;

    @Autowired
    private TransactionService transactionService;

    @Async
    public void autoSendAsync(String packageId) {

        try {
            Thread.sleep(AUTO_SEND_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }

        try {
            sendPackageIfNeeded(packageId);
        } catch (Exception e) {
            log.error("AUTO SEND FAILED packageId={}", packageId, e);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendPackageIfNeeded(String packageId) throws Exception {

        runtimeConfigService.reload();

        if (!runtimeConfigService.isAutoModeEnabled()) {
            return;
        }

        PackageEntity pkg = packageRepository.findById(packageId)
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Package not found: " + packageId));

        if (pkg.getState() == PackageState.SENT) {
            return;
        }

        if (pkg.getState() != PackageState.CREATED
                && pkg.getState() != PackageState.NEW) {
            return;
        }

        Path packagePath =
                Paths.get(pkg.getPackagePath())
                        .toAbsolutePath()
                        .normalize();

        if (!Files.exists(packagePath)) {
            throw new IllegalStateException(
                    "Package file not found: " + packagePath);
        }

        log.info(
                "AUTO SEND START package={}",
                pkg.getPackageName()
        );

        pkg.setState(PackageState.PROCESSING);
        packageRepository.save(pkg);

        TransactionEntity tx =
                transactionService.startTransaction(
                        pkg.getId(),
                        Direction.DOWNSTREAM,
                        AUTO_USERNAME
                );

        try {

            protocolService.sendPackage(packagePath, pkg);

            pkg.setState(PackageState.SENT);
            pkg.setExportedAt(Instant.now());

            packageRepository.save(pkg);

            transactionService.closeSuccess(
                    tx.getId(),
                    "SUCCESS: Package transferred automatically"
            );

            log.info(
                    "AUTO SEND SUCCESS package={}",
                    pkg.getPackageName()
            );

        } catch (Exception ex) {

            pkg.setState(PackageState.FAILED);

            packageRepository.save(pkg);

            transactionService.closeFailure(
                    tx.getId(),
                    "FAILED: Transfer error - " + ex.getMessage(),
                    AUTO_USERNAME
            );

            throw ex;
        }
    }
}