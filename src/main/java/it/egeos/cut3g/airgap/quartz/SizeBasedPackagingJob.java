package it.egeos.cut3g.airgap.quartz;

import it.egeos.cut3g.airgap.persistence.enums.FileItemState;
import it.egeos.cut3g.airgap.persistence.repo.FileItemRepository;
import it.egeos.cut3g.airgap.service.packaging.PackagingService;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@DisallowConcurrentExecution
public class SizeBasedPackagingJob implements Job {

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

        long threshold = maxMb;

        if (totalSize >= threshold) {
            packagingService.createLatestOrAutoPackage("auto");
        }
    }
}
