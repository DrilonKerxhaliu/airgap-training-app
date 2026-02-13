package it.egeos.cut3g.airgap.controller;

import it.egeos.cut3g.airgap.service.files.FileSseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * JSON-only REST API.
 */
@RestController
@RequestMapping(value = "/airgap")
public class AirgapController {

    private static final Logger log = LoggerFactory.getLogger(AirgapController.class);

    @Autowired
    private FileSseService sseService;

    @GetMapping(value = "file/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamFiles() {
        return sseService.subscribe();
    }
}