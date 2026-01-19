package it.egeos.cut3g.airgap.service.files;

import it.egeos.cut3g.airgap.api.dto.*;
import it.egeos.cut3g.airgap.api.dto.LatestContentResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.NOT_FOUND;


@Service
public class FileDiscoveryService {

    private static final Logger log = LoggerFactory.getLogger(FileDiscoveryService.class);

    @Value("${airgap.collect.in}")
    private String collectIn;

    public LatestContentResponse latestFolderContentGrouped() {
    Path inRoot = Paths.get(collectIn);

    if (!Files.exists(inRoot)) {
        throw new ResponseStatusException(NOT_FOUND, "Collection folder not found: " + inRoot);
    }

    try (Stream<Path> stream = Files.walk(inRoot)) {

        Map<String, List<FileContentDto>> grouped = stream
                .filter(Files::isRegularFile)
                .map(p -> {
                    String relPath = inRoot.relativize(p).toString().replace('\\', '/');
                    long size;
                    try {
                        size = Files.size(p);
                    } catch (IOException e) {
                        size = -1;
                    }
                    return new FileContentDto(relPath, size);
                })
                .collect(Collectors.groupingBy(f -> {
                    int idx = f.relativePath.indexOf('/');
                    return idx > 0 ? f.relativePath.substring(0, idx) : "/"; // root files
                }));

        List<FolderContentDto> folders = grouped.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> {
                    FolderContentDto f = new FolderContentDto();
                    f.folder = e.getKey();
                    f.files = e.getValue().stream()
                            .sorted(Comparator.comparing(a -> a.relativePath))
                            .collect(Collectors.toList());
                    return f;
                })
                .collect(Collectors.toList());

        LatestContentResponse resp = new LatestContentResponse();
        resp.snapshotTime = Instant.now();
        resp.root = inRoot.toString();
        resp.folders = folders;
        resp.totalFiles = folders.stream().mapToInt(f -> f.files.size()).sum();
        resp.totalSizeBytes = folders.stream()
                .flatMap(f -> f.files.stream())
                .mapToLong(f -> f.sizeBytes)
                .sum();

        return resp;

    } catch (IOException e) {
        throw new ResponseStatusException(
                INTERNAL_SERVER_ERROR,
                "Unable to list latest folder content: " + e.getMessage(),
                e
        );

    }
}


}
