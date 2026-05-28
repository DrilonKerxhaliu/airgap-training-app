package it.egeos.cut3g.airgap.service.downstream;

import it.egeos.cut3g.airgap.persistence.entity.PackageEntity;
import it.egeos.cut3g.airgap.persistence.enums.PackageState;
import it.egeos.cut3g.airgap.persistence.repo.PackageRepository;
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
import java.util.List;

@Service
public class PackageAutoSendService {

    private static final Logger log = LoggerFactory.getLogger(PackageAutoSendService.class);

    private static final long AUTO_SEND_DELAY_MS = 10_000;
    private static final String AUTO_USERNAME = "AUTO";

    @Autowired
    private RuntimeConfigService runtimeConfigService;

    @Autowired
    private PackageRepository packageRepository;

    @Autowired
    private DownstreamService downstreamService;

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

        boolean autoMode = runtimeConfigService.isAutoModeEnabled();

        if (!autoMode) {
            log.info("AUTO MODE DISABLED -> skip send packageId={}", packageId);
            return;
        }

        PackageEntity pkg = packageRepository.findById(packageId)
                .orElseThrow(() -> new IllegalStateException("Package not found: " + packageId));

        if (pkg.getState() == PackageState.SENT) {
            log.info("Package already SENT -> {}", pkg.getPackageName());
            return;
        }

        if (pkg.getState() != PackageState.CREATED
                && pkg.getState() != PackageState.NEW) {

            log.warn("Package state not eligible for auto send: {} state={}",
                    pkg.getPackageName(),
                    pkg.getState());
            return;
        }

        Path packagePath = Paths.get(pkg.getPackagePath()).toAbsolutePath().normalize();

        if (!Files.exists(packagePath)) {
            throw new IllegalStateException("Package file not found: " + packagePath);
        }

        log.info("AUTO SEND START package={}", pkg.getPackageName());

        downstreamService.sendPackages(List.of(packageId), AUTO_USERNAME);

        log.info("AUTO SEND SUCCESS package={}", pkg.getPackageName());
    }
}