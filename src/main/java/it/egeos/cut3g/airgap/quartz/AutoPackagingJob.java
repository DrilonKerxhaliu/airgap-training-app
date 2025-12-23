package it.egeos.cut3g.airgap.quartz;

import it.egeos.cut3g.airgap.api.Direction;
import it.egeos.cut3g.airgap.persistence.repo.FileItemRepository;
import it.egeos.cut3g.airgap.service.packageing.PackagingService;
import org.quartz.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.*;

/**
 * AUTO packaging:
 */
@Component
@DisallowConcurrentExecution
public class AutoPackagingJob implements Job {

    @Value("${airgap.collect.in}")
    private String collectedIn;

    @Value("${airgap.auto.max-gb}")
    private int maxGb;

    @Autowired
    private PackagingService packagingService;

    @Value("${airgap.instance.direction}")
    private String instanceDirection;

    @Autowired
    private FileItemRepository fileItemRepository;

    @Override
    public void execute(JobExecutionContext context) {
        long bytesInFolder = folderSize(Paths.get(collectedIn));
        long threshold = ((long) maxGb) * 1024L * 1024L; // 100 Mbyte

        boolean hasNew = fileItemRepository.countNewFiles() > 0;

        if (!hasNew) return;

        // Time-based trigger already happens by Quartz schedule;
        // size-based trigger is checked here.
        if (bytesInFolder >= threshold) {
            packagingService.createLatestPackage(Direction.valueOf(instanceDirection));
        } else {
            // Every scheduled run, we also package if there are NEW files.
            packagingService.createLatestPackage(Direction.valueOf(instanceDirection));
        }
    }

    private long folderSize(Path root) {
        if (!Files.exists(root)) return 0;
        try {
            return Files.walk(root)
                    .filter(Files::isRegularFile)
                    .mapToLong(p -> {
                        try { return Files.size(p); } catch (Exception e) { return 0L; }
                    }).sum();
        } catch (Exception e) {
            return 0L;
        }
    }
}
