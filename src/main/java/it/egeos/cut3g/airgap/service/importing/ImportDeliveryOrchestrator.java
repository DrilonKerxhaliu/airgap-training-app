package it.egeos.cut3g.airgap.service.importing;

import com.google.gson.Gson;
import it.egeos.cut3g.airgap.service.crypto.CryptoService;
import it.egeos.cut3g.airgap.service.downstream.OutboxDeliveryService;
import it.egeos.cut3g.airgap.service.manifest.ManifestFileItem;
import it.egeos.cut3g.airgap.service.manifest.PackageManifest;
import it.egeos.cut3g.airgap.service.tar.TarExtractService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@Service
public class ImportDeliveryOrchestrator {

        @Autowired
        private TarExtractService tarExtractService;

        @Autowired
        private OutboxDeliveryService outboxDeliveryService;

        @Autowired
        private CryptoService cryptoService;

        @Autowired
        private Gson gson ;

        /**
         * FULL DELIVERY FLOW:
         * 1. extract outer tar
         * 2. decrypt + parse manifest
         * 3. extract inner data.tar
         * 4. validate extracted files vs manifest
         * 5. deliver to OUT
         */
        public PackageManifest unpackAndDeliver(Path packageTar, Path workDir) throws Exception {

            Path outerDir = workDir.resolve("outer");
            Map<String, Path> outer = tarExtractService.extractTar(packageTar, outerDir);

            Path manifestEnc = outer.get("manifest.enc");
            Path dataTar = outer.values().stream()
                    .filter(p -> p.getFileName().toString().endsWith("-data.tar"))
                    .findFirst()
                    .orElse(null);

            if (manifestEnc == null || dataTar == null) {
                throw new IllegalStateException("Outer tar missing manifest.enc or data.tar");
            }


            byte[] encBytes = Files.readAllBytes(manifestEnc);
            byte[] jsonBytes = cryptoService.decrypt(encBytes);

            PackageManifest manifest = gson.fromJson(
                    new String(jsonBytes, StandardCharsets.UTF_8),
                    PackageManifest.class
            );

            validateManifest(manifest);


            Path dataDir = workDir.resolve("data");
            Map<String, Path> extractedFiles =
                    tarExtractService.extractTar(dataTar, dataDir);

            validateExtractedFiles(manifest, dataDir);

            outboxDeliveryService.deliver(extractedFiles);

            return manifest;
        }

        private void validateManifest(PackageManifest manifest) {
            if (manifest == null) {
                throw new IllegalArgumentException("Manifest is null");
            }
            if (manifest.getFiles() == null || manifest.getFiles().isEmpty()) {
                throw new IllegalArgumentException("Manifest contains no files");
            }
        }

        private void validateExtractedFiles(PackageManifest manifest, Path dataDir) {

            for (ManifestFileItem item : manifest.getFiles()) {

                Path expected = dataDir
                        .resolve(item.getRelativePath())
                        .normalize();

                if (!expected.startsWith(dataDir)) {
                    throw new SecurityException(
                            "Invalid manifest path traversal: " + item.getRelativePath()
                    );
                }

                if (!Files.exists(expected)) {
                    throw new IllegalStateException(
                            "Missing extracted file: " + item.getRelativePath()
                    );
                }

                try {
                    long actualSize = Files.size(expected);
                    if (actualSize != item.getSizeBytes()) {
                        throw new IllegalStateException(
                                "Size mismatch for " + item.getRelativePath() +
                                        " expected=" + item.getSizeBytes() +
                                        " actual=" + actualSize
                        );
                    }
                } catch (IOException e) {
                    throw new RuntimeException("Unable to read extracted file size", e);
                }
            }
        }
    }
