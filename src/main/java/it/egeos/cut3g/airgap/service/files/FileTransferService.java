package it.egeos.cut3g.airgap.service.files;

import it.egeos.cut3g.airgap.persistence.entity.PackageEntity;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class FileTransferService {

    public void transfer(PackageEntity pkg, URI uri) throws Exception {

        Path source = Path.of(pkg.getPackagePath());

        Path destinationDir = Path.of(uri);

        Files.createDirectories(destinationDir);

        Path destination = destinationDir.resolve(source.getFileName());

        try {
            Files.copy(source, destination);
        } catch (Exception ex) {
            throw new Exception("File copy failed for package " + pkg.getPackageName() + ": " + ex.getMessage(), ex);
        }

        // Verify the copy succeeded
        if (!Files.exists(destination) || Files.size(destination) != Files.size(source)) {
            throw new Exception("File verification failed: destination file missing or size mismatch for " + pkg.getPackageName());
        }
    }
}
