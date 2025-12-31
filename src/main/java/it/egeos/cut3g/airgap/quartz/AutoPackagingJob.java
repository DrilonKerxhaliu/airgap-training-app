package it.egeos.cut3g.airgap.quartz;

import it.egeos.cut3g.airgap.api.Direction;
import it.egeos.cut3g.airgap.persistence.enums.FileItemState;
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

    @Value("${airgap.instance.direction}")
    private String direction;

    @Value("${airgap.auto.max-mb}")
    private int maxMb;

    @Autowired
    private PackagingService packagingService;

    @Value("${airgap.collect.in}")
    private String collectIn;

    @Autowired
    private FileItemRepository fileItemRepository;

    @Override
    public void execute(JobExecutionContext context) {
        long bytesInFolder = folderSize(Paths.get(collectIn));
        long threshold = ((long) maxMb) * 1024L * 1024L; // 100 Mbyte

        boolean hasNew = fileItemRepository.countByState(FileItemState.NEW) > 0;

        if (!hasNew) return;

        // Time-based trigger already happens by Quartz schedule;
        // size-based trigger is checked here.
        if (bytesInFolder >= threshold) {
            packagingService.createLatestOrAutoPackage(Direction.valueOf(direction));
        } else {
            // Every scheduled run, we also package if there are NEW files.
            packagingService.createLatestOrAutoPackage(Direction.valueOf(direction));
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
