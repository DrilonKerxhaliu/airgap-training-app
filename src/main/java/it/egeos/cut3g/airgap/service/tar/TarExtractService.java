package it.egeos.cut3g.airgap.service.tar;

import org.apache.commons.compress.archivers.tar.*;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.util.*;

@Service
public class TarExtractService {

    public Map<String, Path> extractTar(Path tarFile, Path targetDir) throws IOException {
        Files.createDirectories(targetDir);

        Map<String, Path> extracted = new HashMap<>();

        try (TarArchiveInputStream tis =
                     new TarArchiveInputStream(
                             new BufferedInputStream(Files.newInputStream(tarFile)))) {

            TarArchiveEntry entry;
            while ((entry = tis.getNextTarEntry()) != null) {
                if (entry.isDirectory()) continue;

                Path out = targetDir.resolve(entry.getName()).normalize();
                if (!out.startsWith(targetDir)) {
                    throw new IOException("Invalid TAR entry: " + entry.getName());
                }

                Files.createDirectories(out.getParent());
                Files.copy(tis, out, StandardCopyOption.REPLACE_EXISTING);
                extracted.put(entry.getName(), out);
            }
        }
        return extracted;
    }
}
