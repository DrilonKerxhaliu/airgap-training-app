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
import java.util.ArrayList;
import java.util.List;

@Component
public class TarListingService {

    public List<FileContentDto> listEntries(Path tarPath) throws IOException {
        List<FileContentDto> out = new ArrayList<>();

        try (InputStream is = Files.newInputStream(tarPath);
             TarArchiveInputStream tis = new TarArchiveInputStream(new BufferedInputStream(is))) {

            TarArchiveEntry e;
            while ((e = tis.getNextTarEntry()) != null) {
                if (e.isDirectory()) continue;
                out.add(new FileContentDto(e.getName(), Math.max(e.getSize(), 0)));
            }
        }
        return out;
    }
}
