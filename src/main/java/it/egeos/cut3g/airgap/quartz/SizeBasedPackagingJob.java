package it.egeos.cut3g.airgap.quartz;

import it.egeos.cut3g.airgap.persistence.enums.FileItemState;
import it.egeos.cut3g.airgap.persistence.repo.FileItemRepository;
import it.egeos.cut3g.airgap.service.packaging.PackagingService;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@DisallowConcurrentExecution
public class SizeBasedPackagingJob implements Job {

    private static final Logger log = LoggerFactory.getLogger(AutoPackagingJob.class);


    @Value("${airgap.auto.max-mb}")
    private long maxMb;

    @Autowired
    private FileItemRepository fileItemRepository;

    @Autowired
    private PackagingService packagingService;

    @Override
    public void execute(JobExecutionContext context) {

        long totalSize =
                fileItemRepository.sumSizeByState(FileItemState.NEW);

        long threshold = maxMb * 1024L * 1024L;

        if (totalSize >= threshold) {
            log.info("AUTO PACKAGE TRIGGERED BY SIZE totalSize={} threshold={}", totalSize, threshold );
            packagingService.createLatestOrAutoPackage("AUTO_SIZE");
        }
    }
}
