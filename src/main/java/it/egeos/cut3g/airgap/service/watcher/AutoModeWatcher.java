package it.egeos.cut3g.airgap.service.watcher;

import it.egeos.cut3g.airgap.service.util.UiConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class AutoModeWatcher {

    private static final Logger log = LoggerFactory.getLogger(AutoModeWatcher.class);

    private static final long DEBOUNCE_MS = 500;

    @Value("${airgap.config.path}")
    private String configPath;

    @Autowired
    private UiConfigService uiConfigService;

    private WatchService watchService;
    private ExecutorService executor;
    private final AtomicLong lastProcessed = new AtomicLong(0);

    @PostConstruct
    public void watchConfigFile() {
        executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "config-watcher");
            t.setDaemon(true);
            return t;
        });

        executor.submit(() -> {
            try {
                watchService = FileSystems.getDefault().newWatchService();

                Path configFile = Paths.get(configPath);
                Path dir = configFile.getParent();

                dir.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY);
                log.info("Watching config.json for changes: {}", configPath);

                while (!Thread.currentThread().isInterrupted()) {
                    WatchKey key = watchService.take();

                    for (WatchEvent<?> event : key.pollEvents()) {
                        Path changed = (Path) event.context();

                        if (changed != null && changed.equals(configFile.getFileName())) {
                            long now = System.currentTimeMillis();
                            long last = lastProcessed.get();

                            // Debounce: skip if another event was processed recently
                            if (now - last < DEBOUNCE_MS) {
                                log.debug("Skipping duplicate config change event");
                                continue;
                            }

                            // Wait for write to complete (macOS fires event before write finishes)
                            Thread.sleep(200);

                            lastProcessed.set(System.currentTimeMillis());
                            log.info("config.json modified externally");
                            uiConfigService.reloadAndBroadcastIfChanged();
                        }
                    }
                    key.reset();
                }
            } catch (InterruptedException e) {
                log.info("AutoModeWatcher interrupted, shutting down");
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.error("Error in AutoModeWatcher", e);
            }
        });
    }

    @PreDestroy
    public void stopWatcher() {
        log.info("Shutting down AutoModeWatcher");
        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException e) {
                log.warn("Error closing WatchService", e);
            }
        }
        if (executor != null) {
            executor.shutdownNow();
        }
    }
}
