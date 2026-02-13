package it.egeos.cut3g.airgap.service.watcher;

import it.egeos.cut3g.airgap.api.dto.FileEventDto;
import it.egeos.cut3g.airgap.persistence.entity.FileItemEntity;
import it.egeos.cut3g.airgap.persistence.enums.FileItemState;
import it.egeos.cut3g.airgap.persistence.repo.FileItemRepository;
import it.egeos.cut3g.airgap.service.files.FileSseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.*;

@Service
public class FolderWatcherService {

    private static final Logger log = LoggerFactory.getLogger(FolderWatcherService.class);

    @Value("${airgap.collect.in}")
    private String collectInDir;

    @Value("${airgap.file.stable.seconds}")
    private long stableSeconds;

    @Autowired
    private FileItemRepository fileItemRepository;

    @Autowired
    private FileSseService fileSseService;

    private final ExecutorService watcherExecutor = Executors.newSingleThreadExecutor();
    private final ScheduledExecutorService stabilityExecutor =
            Executors.newSingleThreadScheduledExecutor();

    /**
     * Tracks INSERT files waiting to become stable.
     */
    private Path rootPath;

    /**
     * files in INSERT state
     */
    private final Map<Path, FileSnapshot> insertingFiles = new ConcurrentHashMap<>();

    public FolderWatcherService(FileItemRepository fileItemRepository) {
        this.fileItemRepository = fileItemRepository;
    }

    // start watcher

    @PostConstruct
    public void start() {
        this.rootPath = Paths.get(collectInDir);

        try {
            Files.createDirectories(rootPath);
        } catch (Exception e) {
            log.error("Unable to create/watch directory {}", collectInDir, e);
            return;
        }

        try {
            Files.walk(rootPath)
                    .filter(Files::isRegularFile)
                    .forEach(this::registerInsert);
        } catch (IOException e) {
            log.warn("Initial scan failed for {}", rootPath, e);
        }

        watcherExecutor.submit(this::watchLoop);
        stabilityExecutor.scheduleAtFixedRate(
                this::checkStability,
                stableSeconds,
                stableSeconds,
                TimeUnit.SECONDS
        );
        log.info("FolderWatcher started on {}", rootPath);
    }


    // watch filesystem

    private void watchLoop() {
        try (WatchService watchService = FileSystems.getDefault().newWatchService()) {

            registerRecursive(rootPath, watchService);

            while (true) {
                WatchKey key = watchService.take();
                Path watchedDir = (Path) key.watchable();

                for (WatchEvent<?> event : key.pollEvents()) {
                    if (event.kind() == StandardWatchEventKinds.OVERFLOW) continue;

                    Path relative = (Path) event.context();
                    Path fullPath = watchedDir.resolve(relative).normalize();

                    if (!Files.isRegularFile(fullPath)) continue;

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

            insertingFiles.compute(file, (p, snap) -> {
                if (snap == null) {
                    markDbState(file, FileItemState.INSERT);
                    return new FileSnapshot(size, now);
                }
                snap.update(size, now);
                return snap;
            });

        } catch (IOException ignored) {
        }
    }

    /**
     * Periodic check: if file unchanged for stableSeconds → NEW
     */
    private void checkStability() {
        Instant now = Instant.now();

        for (Map.Entry<Path, FileSnapshot> e : insertingFiles.entrySet()) {
            Path file = e.getKey();
            FileSnapshot snap = e.getValue();

            if (snap.isStable(now, stableSeconds)) {
                markDbState(file, FileItemState.NEW);
                insertingFiles.remove(file);
                log.info("File {} became STABLE → NEW", rootPath.relativize(file));
            }
        }
    }

    // DB state update

    private void markDbState(Path file, FileItemState state) {
        try {
            String relativePath = rootPath.relativize(file).toString();

            FileItemEntity entity =
                    fileItemRepository.findByRelativePath(relativePath)
                            .orElseGet(() -> {
                                FileItemEntity f = new FileItemEntity();
                                f.setRelativePath(relativePath);
                                return f;
                            });

            if (state == FileItemState.NEW) {
                entity.setSizeBytes(Files.size(file)); // FINAL SIZE
            }

            entity.setState(state);
            log.info("Saving DB state: path={}, state={}", relativePath, state);
            FileItemEntity saved = fileItemRepository.save(entity);

            FileEventDto dto = new FileEventDto();
            dto.setId(entity.getId());
            dto.setFolder(entity.getRelativePath().split("/")[0]);
            dto.setFilename(entity.getRelativePath().split("/")[1]);
            dto.setSizeKb(entity.getSizeBytes() / 1024);
            dto.setArrivedAt(entity.getReceivedTime());
            dto.setState(entity.getState());

            fileSseService.publish(dto);



            log.info("Saved entity ID={}", saved.getId());

        } catch (IOException e) {
            log.warn("Unable to update DB state for {}", file, e);
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

   private void registerRecursive(Path root, WatchService ws) throws IOException {
    if (root == null) {
        log.error("registerRecursive called with null rootPath");
        return;
    }
    if (!Files.exists(root)) {
        log.warn("Root directory {} does not exist", root);
        return;
    }

    Files.walk(root)
            .filter(Files::isDirectory)
            .forEach(dir -> {
                try {
                    dir.register(ws,
                            StandardWatchEventKinds.ENTRY_CREATE,
                            StandardWatchEventKinds.ENTRY_MODIFY);
                } catch (IOException e) {
                    log.warn("Unable to register {}", dir, e);
                }
            });
}


}
