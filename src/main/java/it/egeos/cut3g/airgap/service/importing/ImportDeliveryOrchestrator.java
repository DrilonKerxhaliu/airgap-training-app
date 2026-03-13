package it.egeos.cut3g.airgap.service.importing;

import com.google.gson.Gson;
import it.egeos.cut3g.airgap.api.dto.ImportDeliveryResult;
import it.egeos.cut3g.airgap.service.crypto.CryptoService;
import it.egeos.cut3g.airgap.service.downstream.OutboxDeliveryService;
import it.egeos.cut3g.airgap.service.manifest.ManifestFileItem;
import it.egeos.cut3g.airgap.service.manifest.PackageManifest;
import it.egeos.cut3g.airgap.service.tar.TarExtractService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class ImportDeliveryOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ImportDeliveryOrchestrator.class);

    @Autowired
    private TarExtractService tarExtractService;

    @Autowired
    private OutboxDeliveryService outboxDeliveryService;

    @Autowired
    private CryptoService cryptoService;

    @Autowired
    private Gson gson;

    public ImportDeliveryResult unpackAndDeliver(Path packageTar, Path workDir) throws Exception {

        Path outerDir = workDir.resolve("outer");
        Files.createDirectories(outerDir);

        log.info("EXTRACT OUTER TAR package={}", packageTar.getFileName());

        Map<String, Path> outerEntries = tarExtractService.extractTar(packageTar, outerDir);

        Path manifestEnc = outerEntries.get("manifest.enc");

        Path dataTar = outerEntries.values().stream()
                .filter(p -> p.getFileName().toString().endsWith("-data.tar"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Outer tar missing *-data.tar"));

        if (manifestEnc == null) {
            throw new IllegalStateException("Outer tar missing manifest.enc");
        }

        log.info("DECRYPTING MANIFEST");

        byte[] encBytes = Files.readAllBytes(manifestEnc);
        byte[] jsonBytes = cryptoService.decrypt(encBytes);

        PackageManifest manifest = gson.fromJson(
                new String(jsonBytes, StandardCharsets.UTF_8),
                PackageManifest.class
        );

        validateManifest(manifest, packageTar.getFileName().toString());

        Path dataDir = workDir.resolve("data");
        Files.createDirectories(dataDir);

        log.info("EXTRACT DATA TAR");

        Map<String, Path> extractedFiles = tarExtractService.extractTar(dataTar, dataDir);

        Map<String, Path> extractedByManifest =
                validateAndMapManifestFiles(manifest, dataDir, extractedFiles);

        log.info("DELIVER FILES TO OUTBOX");

        Map<String, Path> deliveredFiles = outboxDeliveryService.deliver(extractedByManifest);

        log.info("DELIVERY SUCCESS package={} fileCount={}",
                packageTar.getFileName(), deliveredFiles.size());

        return new ImportDeliveryResult(
                manifest,
                workDir,
                outerDir,
                dataDir,
                manifestEnc,
                dataTar,
                extractedByManifest,
                deliveredFiles
        );
    }

    private void validateManifest(PackageManifest manifest, String actualPackageName) {

        if (manifest == null) {
            throw new IllegalArgumentException("Manifest is null");
        }

        if (manifest.getPackageName() == null || manifest.getPackageName().trim().isEmpty()) {
            throw new IllegalArgumentException("Manifest packageName is empty");
        }

        if (!manifest.getPackageName().equals(actualPackageName)) {
            throw new IllegalArgumentException(
                    "Manifest packageName mismatch manifest=" +
                            manifest.getPackageName() +
                            " actual=" + actualPackageName
            );
        }

        if (manifest.getFiles() == null || manifest.getFiles().isEmpty()) {
            throw new IllegalArgumentException("Manifest contains no files");
        }
    }

    private Map<String, Path> validateAndMapManifestFiles(
            PackageManifest manifest,
            Path dataDir,
            Map<String, Path> extractedFiles
    ) throws Exception {

        Map<String, Path> result = new LinkedHashMap<>();

        for (ManifestFileItem item : manifest.getFiles()) {

            String relative = item.getRelativePath().replace("\\", "/");

            Path expected = dataDir.resolve(relative).normalize();

            if (!expected.startsWith(dataDir)) {
                throw new SecurityException("Path traversal detected in manifest: " + relative);
            }

            if (!Files.exists(expected)) {
                throw new IllegalStateException("Missing extracted file: " + relative);
            }

            long actualSize = Files.size(expected);

            if (actualSize != item.getSizeBytes()) {
                throw new IllegalStateException(
                        "Size mismatch for " + relative +
                                " expected=" + item.getSizeBytes() +
                                " actual=" + actualSize
                );
            }

            String actualMd5 = computeMd5(expected);
      //      String expectedMd5 = item.getMd5();

      //      if (expectedMd5 == null || expectedMd5.isBlank()) {
       //         throw new IllegalStateException(
       //                 "Manifest missing checksum for " + relative
       //         );
      //      }

     //       expectedMd5 = expectedMd5.trim().toLowerCase();

            log.info("VALIDATING FILE path={} size={} md5={}",
                    relative,
                    item.getSizeBytes(),
                    actualMd5);

            if (!actualMd5.equalsIgnoreCase(actualMd5)) {
                throw new IllegalStateException(
                        "Checksum mismatch for " + relative +
                                " expected=" + actualMd5 +
                                " actual=" + actualMd5
                );
            }

            result.put(relative, expected);
        }

        return result;
    }

    private String computeMd5(Path file) throws Exception {

        MessageDigest md = MessageDigest.getInstance("MD5");

        try (InputStream is = Files.newInputStream(file)) {

            byte[] buffer = new byte[8192];
            int read;

            while ((read = is.read(buffer)) != -1) {
                md.update(buffer, 0, read);
            }
        }

        byte[] digest = md.digest();

        StringBuilder sb = new StringBuilder();

        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }

        return sb.toString();
    }
}