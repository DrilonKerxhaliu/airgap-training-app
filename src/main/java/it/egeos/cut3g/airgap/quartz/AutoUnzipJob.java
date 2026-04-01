package it.egeos.cut3g.airgap.quartz;

import it.egeos.cut3g.airgap.service.util.RuntimeConfigService;
import it.egeos.cut3g.airgap.service.importing.IncomingPackageImportService;
import it.egeos.cut3g.airgap.service.upstream.UploadDirectoryScannerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Optional;

@Component
public class AutoUnzipJob {

    @Autowired
    private RuntimeConfigService configService;

    @Autowired
    private UploadDirectoryScannerService scanner;

    @Autowired
    private IncomingPackageImportService importService;

    @Scheduled(fixedDelay = 600000) // 10 min
    public void run() {

        if (!configService.isAutoModeEnabled()) {
            return;
        }

        Optional<Path> pkg = scanner.findNextPackage();

        if (pkg.isEmpty()) {
            return;
        }

        try {
            importService.importUploadedPackage(pkg.get(), "auto");
        } catch (Exception e) {
            e.printStackTrace(); // mos crash scheduler
        }
    }
}