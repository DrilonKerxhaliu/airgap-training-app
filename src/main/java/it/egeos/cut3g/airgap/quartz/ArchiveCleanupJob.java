package it.egeos.cut3g.airgap.quartz;

import it.egeos.cut3g.airgap.service.files.ArchiveCleanupService;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@DisallowConcurrentExecution
public class ArchiveCleanupJob implements Job {

    @Autowired
    private ArchiveCleanupService cleanupService;

    @Override
    public void execute(JobExecutionContext context) {
        cleanupService.cleanupArchivedPackages();
    }
}
