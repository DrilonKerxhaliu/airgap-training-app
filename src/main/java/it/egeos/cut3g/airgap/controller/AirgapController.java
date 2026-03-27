package it.egeos.cut3g.airgap.controller;

import com.google.gson.JsonObject;
import it.egeos.cut3g.airgap.service.files.FileSseService;
import it.egeos.cut3g.airgap.service.files.UiConfigSseService;
import it.egeos.cut3g.airgap.service.util.UiConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

/**
 * JSON-only REST API.
 */
@RestController
@RequestMapping(value = "/airgap")
public class AirgapController {

    private static final Logger log = LoggerFactory.getLogger(AirgapController.class);

    @Autowired
    private FileSseService sseService;

    @Autowired
    private UiConfigSseService uiConfigSseService;

    @Autowired
    private UiConfigService configServiceUi;

    @GetMapping(value = "file/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamFiles() {
        return sseService.subscribe();
    }

    @PostMapping("/automode")
    public ResponseEntity<?> setAutoMode(@RequestParam boolean enabled) throws IOException {
        configServiceUi.setAutoMode(enabled);
        return ResponseEntity.ok().build();
    }

    @GetMapping(value = "stream/ui-config", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamUiConfig() { return uiConfigSseService.subscribe(); }
}