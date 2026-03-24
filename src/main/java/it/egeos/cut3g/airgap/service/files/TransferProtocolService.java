package it.egeos.cut3g.airgap.service.files;

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
    }

    private URI getDownstreamUri() {
        String uri = config.getAsJsonObject("downstream")
                .get("download_package_path")
                .getAsString();
        return URI.create(uri);
    }

    private Path getUpstreamPath() {
        String uri = config.getAsJsonObject("upstream")
                .get("upload_package_path")
                .getAsString();

        return Path.of(URI.create(uri)).toAbsolutePath().normalize();
    }

    public void sendPackage(Path packageFile, PackageEntity pkg) throws Exception {

        URI uri = getDownstreamUri();

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

        fileTransferService.copyDirectory(unzipDir, destination);
    }
}