package it.egeos.cut3g.airgap.service.upstream;

import it.egeos.cut3g.airgap.persistence.repo.UploadPackageRepository;
import it.egeos.cut3g.airgap.persistence.repo.UploadSequenceRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.*;
import java.util.Comparator;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class UploadDirectoryScannerService {

    private static final Logger log = LoggerFactory.getLogger(UploadDirectoryScannerService.class);

    @Value("${airgap.uploaded.packages.dir}")
    private String uploadedDir;

    @Autowired
    private UploadSequenceRepository uploadSequenceRepository;

    private static final Pattern PKG_PATTERN =
            Pattern.compile("PKG_.*_(\\d{6})\\.tar");

    public Optional<Path> findNextPackage() {
        try {
            Path dir = Paths.get(uploadedDir);

            if (!Files.exists(dir)) {
                return Optional.empty();
            }

            long lastSeq = uploadSequenceRepository
                    .findLastSequence();
            long expected = lastSeq + 1;

            return Files.list(dir)
                    .filter(p -> p.getFileName().toString().endsWith(".tar"))
                    .sorted(Comparator.naturalOrder())
                    .filter(p -> matchSequence(p.getFileName().toString(), expected))
                    .findFirst();

        } catch (Exception e) {
            throw new RuntimeException("Scan error uploadedDir", e);
        }
    }

    private boolean matchSequence(String name, long expected) {
        Matcher m = PKG_PATTERN.matcher(name);
        if (m.matches()) {
            long seq = Long.parseLong(m.group(1));
            log.info("Found package with sequence: {}", seq);
            if (seq == expected) {
                log.info("Found package with expected sequence: {}", seq);
                return seq == expected;
            } else {
                log.info("Package sequence {} does not match expected {}", seq, expected);
                return false;
            }
        }
        return false;
    }
}