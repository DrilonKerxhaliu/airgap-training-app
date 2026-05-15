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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
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

    private final ExecutorService watcherExecutor =
            Executors.newSingleThreadExecutor(r -> new Thread(r, "folder-watcher"));

    private final ScheduledExecutorService stabilityExecutor =
            Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "file-stability"));

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
        this.rootPath = Paths.get(collectInDir).toAbsolutePath().normalize();

        try {
            Files.createDirectories(rootPath);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Cannot create/watch directory " + rootPath, e);
        }

        log.info("Starting FolderWatcher on {}", rootPath);

        // initial scan (VERY IMPORTANT)
        initialScan();

        // start watcher loop (with auto-restart)
        watcherExecutor.submit(this::watchForever);

        // start stability checker
        stabilityExecutor.scheduleAtFixedRate(
                this::checkStability,
                stableSeconds,
                stableSeconds,
                TimeUnit.SECONDS
        );
    }

    private void initialScan() {
        try {
            Files.walk(rootPath)
                    .filter(Files::isRegularFile)
                    .forEach(this::registerInsert);
            log.info("Initial scan completed for {}", rootPath);
        } catch (IOException e) {
            log.warn("Initial scan failed for {}", rootPath, e);
        }
    }


    // watch filesystem

    private void watchForever() {
        while (true) {
            try {
                watchLoop();
            } catch (Exception e) {
                log.error("FolderWatcher crashed – restarting in 5s", e);
                sleep(5);
            }
        }
    }

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

                    if (Files.isDirectory(fullPath)) {
                        registerRecursive(fullPath, watchService);
                        continue;
                    }

                    if (!Files.isRegularFile(fullPath)) continue;

                    registerInsert(fullPath);
                }

                if (!key.reset()) {
                    log.warn("WatchKey invalid for {}", watchedDir);
                    break;
                }
            }

        } catch (Exception e) {
            log.error("FolderWatcher stopped", e);
        }
    }

    // INSERT to NEW logic

    private void registerInsert(Path file) {
        try {
            long fsSize = Files.size(file);
            Instant now = Instant.now();
            String relativePath = rootPath.relativize(file).toString();

            FileItemEntity existing =
                    fileItemRepository.findByRelativePath(relativePath).orElse(null);

            if (fsSize <= 0) {
                log.warn("Ignoring zero-byte file for now: {}", rootPath.relativize(file));
                insertingFiles.put(file, new FileSnapshot(fsSize, now));
                return;
            }

            if (existing == null) {
                log.info("New file detected: {}", relativePath);
                markDbState(file, FileItemState.INSERT);
                insertingFiles.put(file, new FileSnapshot(fsSize, now));
                return;
            }

            if (existing.getState() == FileItemState.INSERT) {
                log.info("File stuck in INSERT, re-processing: {}", relativePath);
                markDbState(file, FileItemState.INSERT);
                insertingFiles.put(file, new FileSnapshot(fsSize, now));
                return;
            }

            Long dbSize = existing.getSizeBytes();
            if (dbSize != null && dbSize == fsSize) {
                log.debug("File unchanged, skipping: {}", relativePath);
                return;
            }

            log.info("File updated, re-processing: {} (oldSize={}, newSize={})",
                    relativePath, dbSize, fsSize);

            markDbState(file, FileItemState.INSERT);
            insertingFiles.put(file, new FileSnapshot(fsSize, now));

        } catch (IOException e) {
            log.warn("Unable to inspect file {}", file, e);
        }
    }


    /**
     * Periodic check: if file unchanged for stableSeconds → NEW
     */
    private void checkStability() {
        Instant now = Instant.now();

        insertingFiles.forEach((file, snap) -> {
            try {
                if (!Files.exists(file) || !Files.isRegularFile(file)) {
                    insertingFiles.remove(file);
                    return;
                }

                long currentSize = Files.size(file);

                if (currentSize <= 0) {
                    snap.update(currentSize, now);
                    return;
                }

                if (currentSize != snap.lastSize) {
                    snap.update(currentSize, now);
                    return;
                }

                if (snap.isStable(now, stableSeconds)) {
                    insertingFiles.remove(file);
                    markDbState(file, FileItemState.NEW);
                    log.info("File {} became STABLE → NEW", rootPath.relativize(file));
                }

            } catch (Exception e) {
                insertingFiles.remove(file);
                log.warn("Unable to check stability for {}", file, e);
            }
        });
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
            dto.setId(saved.getId());
            dto.setFolder(extractFolder(relativePath));
            dto.setFilename(extractFilename(relativePath));
            dto.setSizeBytes(saved.getSizeBytes());
            dto.setArrivedAt(saved.getReceivedTime());
            dto.setState(saved.getState());

            fileSseService.onFileEvent(dto);

            log.info("Saved entity ID={}", saved.getId());

        } catch (IOException e) {
            log.warn("Unable to update DB state for {}", file, e);
        }
    }

    //Periodic rescan
    @Scheduled(fixedDelayString =
            "${airgap.collect.rescan-ms}")
    public void periodicRescan() {
        initialScan();
    }

    private void registerRecursive(Path root, WatchService ws)
            throws IOException {

        if (!Files.exists(root)) {
            return;
        }

        Files.walk(root)
                .filter(Files::isDirectory)
                .forEach(dir -> {
                    try {
                        dir.register(ws,
                                StandardWatchEventKinds.ENTRY_CREATE,
                                StandardWatchEventKinds.ENTRY_MODIFY);
                        log.debug("Watching directory {}", dir);
                    } catch (IOException e) {
                        log.warn("Unable to watch {}", dir, e);
                    }
                });
    }

    private void sleep(int seconds) {
        try {
            Thread.sleep(seconds * 1000L);
        } catch (InterruptedException ignored) {
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

    private String extractFilename(String relativePath) {
        String normalized = relativePath.replace("\\", "/");
        int idx = normalized.lastIndexOf("/");

        return idx >= 0
                ? normalized.substring(idx + 1)
                : normalized;
    }

    private String extractFolder(String relativePath) {
        String normalized = relativePath.replace("\\", "/");
        int idx = normalized.lastIndexOf("/");

        return idx >= 0
                ? normalized.substring(0, idx)
                : "";
    }
}
