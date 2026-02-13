package it.egeos.cut3g.airgap.service.files;

import it.egeos.cut3g.airgap.api.dto.FileEventDto;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class FileSseService {

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(0L); // no timeout
        emitters.add(emitter);

        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));

        return emitter;
    }

    public void publish(FileEventDto event) {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(
                        SseEmitter.event()
                                .name("file-event")
                                .data(event)
                );
            } catch (Exception e) {
                emitters.remove(emitter);
            }
        }
    }
}
