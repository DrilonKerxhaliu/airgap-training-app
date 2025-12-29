package it.egeos.cut3g.airgap.controller;

import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * JSON-only REST API.
 * Direction is part of path: /airgap/{direction}/...
 */
@RestController
@RequestMapping(value = "/airgap", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
@Validated
public class AirgapController {
    //TODO
}
