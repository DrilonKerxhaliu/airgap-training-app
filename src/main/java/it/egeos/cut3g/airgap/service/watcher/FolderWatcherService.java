package it.egeos.cut3g.airgap.service.watcher;

import it.egeos.cut3g.airgap.persistence.entity.FileItemEntity;
import it.egeos.cut3g.airgap.persistence.enums.FileItemState;
import it.egeos.cut3g.airgap.persistence.repo.FileItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.nio.file.*;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.*;

@Service
public class FolderWatcherService {

    private static final Logger log = LoggerFactory.getLogger(FolderWatcherService.class);

    @Value("${airgap.collect.in}")
    private String collectInDir;

    @Value("${airgap.file.stable.seconds:10}")
    private long stableSeconds;

    private final FileItemRepository fileItemRepository;

    private final ExecutorService watcherExecutor = Executors.newSingleThreadExecutor();
    private final ScheduledExecutorService stabilityExecutor =
            Executors.newSingleThreadScheduledExecutor();

    /**
     * Tracks INSERT files waiting to become stable.
     */
    private final Map<Path, FileSnapshot> activeFiles = new ConcurrentHashMap<>();

    public FolderWatcherService(FileItemRepository fileItemRepository) {
        this.fileItemRepository = fileItemRepository;}

    // start watcher

    @PostConstruct
    public void start() {
        watcherExecutor.submit(this::watchLoop);
        stabilityExecutor.scheduleAtFixedRate(
                this::checkStability,
                stableSeconds,
                stableSeconds,
                TimeUnit.SECONDS
        );
        log.info("FolderWatcher started on {}", collectInDir);
    }

    // watch filesystem

    private void watchLoop() {
        try (WatchService watchService = FileSystems.getDefault().newWatchService()) {

            Path root = Paths.get(collectInDir);
            root.register(watchService,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_MODIFY
            );

            while (true) {
                WatchKey key = watchService.take();

                for (WatchEvent<?> event : key.pollEvents()) {
                    if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
                        continue;
                    }

                    Path relative = (Path) event.context();
                    Path fullPath = root.resolve(relative);

                    if (!Files.isRegularFile(fullPath)) {
                        continue;
                    }

                    registerInsert(fullPath);
                }

                if (!key.reset()) break;
            }

        } catch (Exception e) {
            log.error("FolderWatcher stopped", e);
        }
    }

    // INSERT to NEW logic

    private void registerInsert(Path file) {
        try {
            long size = Files.size(file);
            Instant now = Instant.now();

            activeFiles.compute(file, (p, snap) -> {
                if (snap == null) {
                    markDbState(file, FileItemState.INSERT);
                    return new FileSnapshot(size, now);
                }
                snap.update(size, now);
                return snap;
            });

        } catch (Exception ignored) {
        }
    }

    /**
     * Periodic check: if file unchanged for stableSeconds → NEW
     */
    private void checkStability() {
        Instant now = Instant.now();

        for (Map.Entry<Path, FileSnapshot> e : activeFiles.entrySet()) {
            Path file = e.getKey();
            FileSnapshot snap = e.getValue();

            if (snap.isStable(now, stableSeconds)) {
                markDbState(file, FileItemState.NEW);
                activeFiles.remove(file);
                log.info("File {} became STABLE → NEW", file.getFileName());
            }
        }
    }

    // DB state update

    private void markDbState(Path file, FileItemState state) {
        String relativePath = file.getFileName().toString();

        FileItemEntity entity =
                fileItemRepository.findByRelativePath(relativePath)
                        .orElseGet(() -> {
                            FileItemEntity f = new FileItemEntity();
                            f.setRelativePath(relativePath);
                            return f;
                        });

        entity.setSizeBytes(safeSize(file));
        entity.setState(state);

        fileItemRepository.save(entity);
    }

    private long safeSize(Path p) {
        try {
            return Files.size(p);
        } catch (Exception e) {
            return 0L;
        }
    }

    // snapshot file internal check

    private static class FileSnapshot {
        long lastSize;
        Instant lastChange;

        FileSnapshot(long size, Instant ts) {
            this.lastSize = size;
            this.lastChange = ts;
        }

        void update(long size, Instant ts) {
            if (this.lastSize != size) {
                this.lastSize = size;
                this.lastChange = ts;
            }
        }

        boolean isStable(Instant now, long stableSeconds) {
            return now.minusSeconds(stableSeconds).isAfter(lastChange);
        }
    }
}
