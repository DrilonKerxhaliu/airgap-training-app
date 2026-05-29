package it.egeos.cut3g.airgap.service.files;

import it.egeos.cut3g.airgap.api.dto.FileEventDto;
import it.egeos.cut3g.airgap.persistence.enums.FileItemState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.annotation.PreDestroy;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Service
public class FileSseService {

    private static final Logger log = LoggerFactory.getLogger(FileSseService.class);

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    private final Map<String, FileEventDto> newFiles = new ConcurrentHashMap<>();

    private final ScheduledExecutorService flushExecutor =
            Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "sse-flush"));

    private final AtomicBoolean dirty = new AtomicBoolean(false);

    public FileSseService() {
        // flush at a fixed rate (real-time enough, but safe for big volumes)
        flushExecutor.scheduleAtFixedRate(this::flushIfDirty, 0, 300, TimeUnit.MILLISECONDS);
    }

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(0L); // no timeout
        emitters.add(emitter);

        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));

        // send initial snapshot to the new subscriber
        try {
            emitter.send(SseEmitter.event()
                    .name("new-files")
                    .data(currentSnapshot()));
        } catch (Exception e) {
            emitters.remove(emitter);
        }

        return emitter;
    }

    public void onFileEvent(FileEventDto event) {
        if (event == null || event.getId() == null) return;

        if (event.getState() == FileItemState.NEW) {
            newFiles.put(event.getId(), event);
        } else {
            newFiles.remove(event.getId());
        }

        markDirty();
    }

    public void removeFromNewByIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) return;
        for (String id : ids) {
            if (id != null) newFiles.remove(id);
        }
        markDirty();
    }

    public void addToNew(List<FileEventDto> events) {
        if (events == null || events.isEmpty()) return;
        for (FileEventDto e : events) {
            if (e != null && e.getId() != null) {
                newFiles.put(e.getId(), e);
            }
        }
        markDirty();
    }

    public void flushNow() {
        try {
            publishSnapshot(currentSnapshot());
        } catch (Exception e) {
            log.debug("Unable to flushNow", e);
        }
    }

    private void markDirty() {
        dirty.set(true);
    }

    private void flushIfDirty() {
        if (!dirty.compareAndSet(true, false)) return;
        try {
            publishSnapshot(currentSnapshot());
        } catch (Exception e) {
            log.debug("SSE flush failed", e);
        }
    }

    private List<FileEventDto> currentSnapshot() {
        // Copy to avoid concurrent modification issues; also sort for stable UI.
        List<FileEventDto> list = new ArrayList<>(newFiles.values());
        return list.stream()
                .sorted(Comparator
                        .comparing((FileEventDto d) -> d.getArrivedAt() != null ? d.getArrivedAt() : Instant.EPOCH)
                        .thenComparing(d -> (d.getFolder()!=null?d.getFolder():"") + "/" + (d.getFilename()!=null?d.getFilename():""))
                        .thenComparing(d -> d.getId() != null ? d.getId() : ""))
                .collect(Collectors.toList());
    }

    private void publishSnapshot(List<FileEventDto> snapshot) {
        log.info("SSE SNAPSHOT size={}", snapshot.size());
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("new-files")
                        .data(snapshot));
            } catch (IOException e) {
                emitters.remove(emitter);
            } catch (Exception e) {
                emitters.remove(emitter);
            }
        }
    }

    @PreDestroy
    public void shutdown() {
        try {
            flushExecutor.shutdownNow();
        } catch (Exception ignored) {}
    }
}
