package it.egeos.cut3g.airgap.quartz;

import it.egeos.cut3g.airgap.service.util.RuntimeConfigService;
import it.egeos.cut3g.airgap.service.importing.IncomingPackageImportService;
import it.egeos.cut3g.airgap.service.upstream.UploadDirectoryScannerService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Optional;

@Component
public class AutoUnzipJob {

    private static final Logger log = LoggerFactory.getLogger(AutoUnzipJob.class);

    @Autowired
    private RuntimeConfigService configService;

    @Autowired
    private UploadDirectoryScannerService scanner;

    @Autowired
    private IncomingPackageImportService importService;

    @Scheduled(fixedDelay = 600000) // 10 min
    public void run() {
        log.info("AutoUnzipJob executed");

        if (!configService.isAutoModeEnabled()) {
            log.info("Auto mode is disabled, skipping auto unzip");
            return;
        }

        Optional<Path> pkg = scanner.findNextPackage();

        if (pkg.isEmpty()) {
            log.info("No packages found for auto unzip");
            return;
        }

        try {
            importService.importUploadedPackage(pkg.get(), "auto", false, null, null);
        } catch (Exception e) {
            e.printStackTrace(); // mos crash scheduler
        }
    }
}