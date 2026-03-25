package it.egeos.cut3g.airgap.service.files;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import it.egeos.cut3g.airgap.persistence.entity.PackageEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.Reader;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class TransferProtocolService {

    @Value("${airgap.config.path}")
    private String configPath;

    private JsonObject config;

    @Autowired
    private FileTransferService fileTransferService;

    @Autowired
    private FtpTransferService ftpTransferService;

    @PostConstruct
    public void loadConfig() throws Exception {
        try (Reader reader = Files.newBufferedReader(Path.of(configPath))) {
            config = JsonParser.parseReader(reader).getAsJsonObject();
        }

        getServer();
        getDownstreamConfig();
        getUpstreamConfig();
    }

    private JsonObject getServer() {
        return getRequiredObject(config, "server");
    }

    private JsonObject getDownstreamConfig() {
        return getRequiredObject(getServer(), "downstream");
    }

    private JsonObject getUpstreamConfig() {
        return getRequiredObject(getServer(), "upstream");
    }

    private JsonObject getRequiredObject(JsonObject parent, String key) {
        JsonElement el = parent.get(key);

        if (el == null || !el.isJsonObject()) {
            throw new IllegalStateException("Missing or invalid object in config.json: " + key);
        }

        return el.getAsJsonObject();
    }

    private String getRequiredString(JsonObject parent, String key) {
        JsonElement el = parent.get(key);

        if (el == null || el.isJsonNull()) {
            throw new IllegalStateException("Missing value in config.json: " + key);
        }

        return el.getAsString();
    }

    private URI getDownstreamUri() {
        String uri = getRequiredString(getDownstreamConfig(), "download_package_path");
        return URI.create(uri);
    }

    private Path getUpstreamPath() {
        String uri = getRequiredString(getUpstreamConfig(), "upload_package_path");

        URI parsed = URI.create(uri);

        if (!"file".equalsIgnoreCase(parsed.getScheme())) {
            throw new IllegalArgumentException("Upstream must use file:// protocol");
        }

        return Path.of(parsed).toAbsolutePath().normalize();
    }

    public void sendPackage(Path packageFile, PackageEntity pkg) throws Exception {

        URI uri = getDownstreamUri();

        if (uri.getScheme() == null) {
            throw new IllegalArgumentException("Invalid downstream URI (missing scheme)");
        }

        switch (uri.getScheme().toLowerCase()) {

            case "file":
                Path destination = Path.of(uri).toAbsolutePath().normalize();
                fileTransferService.copyFile(packageFile, destination);
                break;

            case "ftp":
                ftpTransferService.transfer(pkg, uri);
                break;

            default:
                throw new IllegalArgumentException("Unsupported protocol: " + uri.getScheme());
        }
    }

    public void exportUnzipped(Path unzipDir) throws Exception {

        Path destination = getUpstreamPath();

        Files.createDirectories(destination);

        fileTransferService.copyDirectory(unzipDir, destination);
    }
}