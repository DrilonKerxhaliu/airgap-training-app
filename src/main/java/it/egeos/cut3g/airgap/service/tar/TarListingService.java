package it.egeos.cut3g.airgap.service.tar;

import it.egeos.cut3g.airgap.api.dto.FileContentDto;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.springframework.stereotype.Component;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
public class TarListingService {

        public List<FileContentDto> listFilesFromSubTars(Path tarPath) throws IOException {
            List<FileContentDto> result = new ArrayList<>();

            Path tempRoot = Files.createTempDirectory("airgap_nested_tar_");

            try (InputStream is = Files.newInputStream(tarPath);
                 TarArchiveInputStream tis = new TarArchiveInputStream(new BufferedInputStream(is))) {

                TarArchiveEntry entry;

                while ((entry = tis.getNextTarEntry()) != null) {
                    if (entry.isDirectory()) continue;

                    String entryName = entry.getName().toLowerCase();

                    if (!entryName.endsWith(".tar")) {
                        continue;
                    }
                    Path subTarPath = tempRoot.resolve(entry.getName()).normalize();
                    Files.createDirectories(subTarPath.getParent());
                    Files.copy(tis, subTarPath, StandardCopyOption.REPLACE_EXISTING);
                    result.addAll(readTarFiles(subTarPath));
                }

            } finally {
                deleteDirectoryQuietly(tempRoot);
            }

            return result;
        }

        private List<FileContentDto> readTarFiles(Path tarFile) throws IOException {
            List<FileContentDto> out = new ArrayList<>();

            try (InputStream is = Files.newInputStream(tarFile);
                 TarArchiveInputStream tis = new TarArchiveInputStream(new BufferedInputStream(is))) {

                TarArchiveEntry e;
                while ((e = tis.getNextTarEntry()) != null) {
                    if (e.isDirectory()) continue;
                    out.add(new FileContentDto("/" + e.getName(), Math.max(e.getSize(), 0)));
                }
            }
            return out;
        }

        private void deleteDirectoryQuietly(Path dir) {
            try {
                if (!Files.exists(dir)) return;

                Files.walk(dir)
                        .sorted(Comparator.reverseOrder())
                        .forEach(p -> {
                            try {
                                Files.deleteIfExists(p);
                            } catch (IOException ignored) {
                            }
                        });
            } catch (IOException ignored) {
            }
        }
    }

