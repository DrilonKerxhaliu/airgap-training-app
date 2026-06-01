package it.egeos.cut3g.airgap.quartz;

import it.egeos.cut3g.airgap.persistence.enums.FileItemState;
import it.egeos.cut3g.airgap.persistence.repo.FileItemRepository;
import it.egeos.cut3g.airgap.service.packaging.PackagingService;
import it.egeos.cut3g.airgap.service.util.RuntimeConfigService;
import org.quartz.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * AUTO packaging:
 */
@Component
@DisallowConcurrentExecution
public class AutoPackagingJob implements Job {

    private static final Logger log = LoggerFactory.getLogger(SizeBasedPackagingJob.class);

    @Autowired
    private PackagingService packagingService;

    @Autowired
    private FileItemRepository fileItemRepository;

    @Autowired
    private RuntimeConfigService configService;

    @Override
    public void execute(JobExecutionContext context) {

        if (!configService.isAutoModeEnabled()) {
            return;
        }

        long newCount = fileItemRepository.countByState(FileItemState.NEW);
        if (newCount == 0) {
            return;
        }
        log.info("AUTO PACKAGE TRIGGERED BY TIME");
        packagingService.createLatestOrAutoPackage("AUTO_TIME");
    }
}

