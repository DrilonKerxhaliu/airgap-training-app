package it.egeos.cut3g.airgap.service.util;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import it.egeos.cut3g.airgap.service.files.UiConfigSseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

@Service
public class UiConfigService {

    private static final Logger log = LoggerFactory.getLogger(UiConfigService.class);

    @Value("${airgap.config.path}")
    private String configPath;

    private JsonObject currentConfig;

    @Autowired
    private UiConfigSseService sseService;

    @PostConstruct
    public void init() throws IOException {
        load();
        log.info("Config loaded at startup from {}", configPath);
    }

    public synchronized JsonObject load() throws IOException {
        try (Reader reader = Files.newBufferedReader(Paths.get(configPath))) {
            currentConfig = JsonParser.parseReader(reader).getAsJsonObject();
            return currentConfig;
        }
    }

    public synchronized void setAutoMode(boolean enabled) throws IOException {

        JsonObject base = currentConfig != null ? currentConfig : load();
        JsonObject config = base.deepCopy();
        JsonObject ui = config.getAsJsonObject("ui");

        if (ui == null) {
            log.warn("No 'ui' key found in config, cannot set autoMode");
            return;
        }

        JsonElement autoModeEl = ui.get("autoMode");
        boolean current = autoModeEl != null && autoModeEl.getAsBoolean();

        if (current == enabled) {
            return;
        }
        ui.addProperty("autoMode", enabled);
        save(config);
    }

    // Save config (trigger SSE if UI changed)
    public synchronized void save(JsonObject newConfig) throws IOException {
        JsonObject oldUi = currentConfig != null ? currentConfig.getAsJsonObject("ui") : null;
        JsonObject newUi = newConfig.getAsJsonObject("ui");

        Map<String, Object> diff = computeUiDiff(oldUi, newUi);

        // write file
        try (Writer writer = Files.newBufferedWriter(Paths.get(configPath))) {
            new GsonBuilder().setPrettyPrinting().create().toJson(newConfig, writer);
        }

        currentConfig = newConfig;

        if (!diff.isEmpty()) {
            log.info("UI config changed -> broadcasting {}", diff);
            sseService.broadcast(diff);
        }
    }

    // Used by watcher (external file change)
    public synchronized void reloadAndBroadcastIfChanged() throws IOException {
        JsonObject oldConfig = currentConfig;

        try (Reader reader = Files.newBufferedReader(Paths.get(configPath))) {
            JsonObject newConfig = JsonParser.parseReader(reader).getAsJsonObject();

            JsonObject oldUi = oldConfig != null ? oldConfig.getAsJsonObject("ui") : null;
            JsonObject newUi = newConfig.getAsJsonObject("ui");

            Map<String, Object> diff = computeUiDiff(oldUi, newUi);

            currentConfig = newConfig;

            if (!diff.isEmpty()) {
                log.info("External UI config change detected -> {}", diff);
                sseService.broadcast(diff);
            }
        }
    }

    // Diff only changed UI fields
    private Map<String, Object> computeUiDiff(JsonObject oldUi, JsonObject newUi) {
        Map<String, Object> changes = new HashMap<>();

        for (String key : newUi.keySet()) {
            JsonElement oldVal = oldUi != null ? oldUi.get(key) : null;
            JsonElement newVal = newUi.get(key);

            if (newVal == null) continue;

            if (oldVal == null || !oldVal.equals(newVal)) {
                if (newVal.isJsonPrimitive() && newVal.getAsJsonPrimitive().isBoolean()) {
                    changes.put(key, newVal.getAsBoolean());
                } else {
                    changes.put(key, newVal.toString());
                }
            }
        }

        return changes;
    }
}