package it.egeos.cut3g.airgap.service.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
public class RuntimeConfigService {

    @Value("${airgap.config.path}")
    private String configPath;

    private JsonObject cachedConfig;

    @PostConstruct
    public void init() {
        reload();
    }

    public synchronized void reload() {
        try {
            Path path = Paths.get(configPath);
            if (!Files.exists(path)) {
                throw new RuntimeException("config.json not found at: " + configPath);
            }

            try (FileReader reader = new FileReader(path.toFile())) {
                cachedConfig = JsonParser.parseReader(reader).getAsJsonObject();
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed to load config.json", e);
        }
    }

    private JsonObject server() {
        return cachedConfig.getAsJsonObject("server");
    }

    private JsonObject ui() {
        return cachedConfig.getAsJsonObject("ui");
    }

    public boolean isAutoModeEnabled() {
        return ui().get("autoMode").getAsBoolean();
    }

    public int getAutoPackagingHours() {
        return server()
                .getAsJsonObject("autoPackaging")
                .get("hours")
                .getAsInt();
    }

    public int getSizeCheckIntervalMin() {
        return server()
                .getAsJsonObject("autoPackaging")
                .get("size-check-interval-min")
                .getAsInt();
    }
}
