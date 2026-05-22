package it.egeos.cut3g.airgap.service.tar;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.io.IOUtils;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class TarService {

    public Path buildDataTar(Path rootIn, List<String> relativePaths, Path outputDataTarPath) {
        try {
            Path normalizedRoot = rootIn.toAbsolutePath().normalize();
            Files.createDirectories(outputDataTarPath.getParent());

            Set<String> uniqueRelativePaths = new LinkedHashSet<>(relativePaths);

            try (OutputStream fos = Files.newOutputStream(
                    outputDataTarPath,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );
                 BufferedOutputStream bos = new BufferedOutputStream(fos);
                 TarArchiveOutputStream tarOut = new TarArchiveOutputStream(bos)) {

                tarOut.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
                tarOut.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX);

                for (String rel : uniqueRelativePaths) {
                    if (rel == null || rel.isBlank()) {
                        continue;
                    }

                    String tarEntryName = normalizeTarEntryName(rel);
                    Path filePath = normalizedRoot.resolve(tarEntryName).normalize();

                    if (!filePath.startsWith(normalizedRoot)) {
                        throw new IllegalStateException("Invalid path outside root: " + rel);
                    }

                    if (!Files.exists(filePath)) {
                        throw new IllegalStateException("Missing file while creating data TAR: " + tarEntryName);
                    }

                    if (!Files.isRegularFile(filePath)) {
                        continue;
                    }

                    long size = Files.size(filePath);
                    if (size <= 0) {
                        throw new IllegalStateException("Refusing to package zero-byte file: " + tarEntryName);
                    }

                    TarArchiveEntry entry = new TarArchiveEntry(filePath.toFile(), tarEntryName);
                    entry.setSize(size);

                    tarOut.putArchiveEntry(entry);
                    try (InputStream in = Files.newInputStream(filePath)) {
                        IOUtils.copyLarge(in, tarOut);
                    }
                    tarOut.closeArchiveEntry();
                }

                tarOut.finish();
            }

            return outputDataTarPath;

        } catch (Exception e) {
            throw new RuntimeException("Unable to create data TAR", e);
        }
    }

    public void writeOuterTar(Path outTarPath,
                              String encryptedManifestName,
                              byte[] encryptedManifestBytes,
                              String dataTarName,
                              Path dataTarPath) {
        try {
            Files.createDirectories(outTarPath.getParent());

            try (OutputStream fos = Files.newOutputStream(
                    outTarPath,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );
                 BufferedOutputStream bos = new BufferedOutputStream(fos);
                 TarArchiveOutputStream tarOut = new TarArchiveOutputStream(bos)) {

                tarOut.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
                tarOut.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX);

                // manifest.enc
                TarArchiveEntry manifestEntry = new TarArchiveEntry(encryptedManifestName);
                manifestEntry.setSize(encryptedManifestBytes.length);
                tarOut.putArchiveEntry(manifestEntry);
                tarOut.write(encryptedManifestBytes);
                tarOut.closeArchiveEntry();

                // data tar
                TarArchiveEntry dataEntry = new TarArchiveEntry(dataTarName);
                dataEntry.setSize(Files.size(dataTarPath));
                tarOut.putArchiveEntry(dataEntry);

                try (InputStream in = Files.newInputStream(dataTarPath)) {
                    IOUtils.copyLarge(in, tarOut);
                }

                tarOut.closeArchiveEntry();
                tarOut.finish();
            }
        } catch (Exception e) {
            throw new RuntimeException("Unable to write outer TAR", e);
        }
    }

    private String normalizeTarEntryName(String rel) {
        return rel.replace("\\", "/").replaceFirst("^/+", "");
    }
}