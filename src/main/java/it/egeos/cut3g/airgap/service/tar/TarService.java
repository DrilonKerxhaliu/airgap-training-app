package it.egeos.cut3g.airgap.service.tar;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.io.IOUtils;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.util.List;

/**
 * Creates TAR archives using Apache Commons Compress.
 */
@Service
public class TarService {

    public byte[] buildDataTar(Path rootIn, List<String> relativePaths) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             TarArchiveOutputStream tarOut = new TarArchiveOutputStream(bos)) {

            tarOut.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);

            for (String rel : relativePaths) {
                Path p = rootIn.resolve(rel).normalize();
                if (!Files.exists(p) || Files.isDirectory(p)) {
                    continue;
                }
                TarArchiveEntry entry = new TarArchiveEntry(p.toFile(), rel);
                entry.setSize(Files.size(p));
                tarOut.putArchiveEntry(entry);
                try (InputStream in = Files.newInputStream(p)) {
                    IOUtils.copy(in, tarOut);
                }
                tarOut.closeArchiveEntry();
            }
            tarOut.finish();
            return bos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Unable to create data TAR", e);
        }
    }

    public void writeOuterTar(Path outTarPath, String encryptedManifestName, byte[] encryptedManifestBytes,
                             String dataTarName, byte[] dataTarBytes) {
        try {
            Files.createDirectories(outTarPath.getParent());
            try (OutputStream fos = Files.newOutputStream(outTarPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                 TarArchiveOutputStream tarOut = new TarArchiveOutputStream(fos)) {

                tarOut.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);

                // manifest.enc
                TarArchiveEntry manifestEntry = new TarArchiveEntry(encryptedManifestName);
                manifestEntry.setSize(encryptedManifestBytes.length);
                tarOut.putArchiveEntry(manifestEntry);
                tarOut.write(encryptedManifestBytes);
                tarOut.closeArchiveEntry();

                // data tar
                TarArchiveEntry dataEntry = new TarArchiveEntry(dataTarName);
                dataEntry.setSize(dataTarBytes.length);
                tarOut.putArchiveEntry(dataEntry);
                tarOut.write(dataTarBytes);
                tarOut.closeArchiveEntry();

                tarOut.finish();
            }
        } catch (Exception e) {
            throw new RuntimeException("Unable to write outer TAR", e);
        }
    }
}
