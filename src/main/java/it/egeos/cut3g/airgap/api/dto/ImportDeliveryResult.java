package it.egeos.cut3g.airgap.api.dto;

import it.egeos.cut3g.airgap.service.manifest.PackageManifest;

import java.nio.file.Path;
import java.util.Map;

public class ImportDeliveryResult {

    private final PackageManifest manifest;
    private final Path workDir;
    private final Path outerDir;
    private final Path dataDir;
    private final Path manifestEncPath;
    private final Path dataTarPath;
    private final Map<String, Path> extractedFiles;
    private final Map<String, Path> deliveredFiles;

    public ImportDeliveryResult(PackageManifest manifest,
                                Path workDir,
                                Path outerDir,
                                Path dataDir,
                                Path manifestEncPath,
                                Path dataTarPath,
                                Map<String, Path> extractedFiles,
                                Map<String, Path> deliveredFiles) {
        this.manifest = manifest;
        this.workDir = workDir;
        this.outerDir = outerDir;
        this.dataDir = dataDir;
        this.manifestEncPath = manifestEncPath;
        this.dataTarPath = dataTarPath;
        this.extractedFiles = extractedFiles;
        this.deliveredFiles = deliveredFiles;
    }

    public PackageManifest getManifest() {
        return manifest;
    }

    public Path getWorkDir() {
        return workDir;
    }

    public Path getOuterDir() {
        return outerDir;
    }

    public Path getDataDir() {
        return dataDir;
    }

    public Path getManifestEncPath() {
        return manifestEncPath;
    }

    public Path getDataTarPath() {
        return dataTarPath;
    }

    public Map<String, Path> getExtractedFiles() {
        return extractedFiles;
    }

    public Map<String, Path> getDeliveredFiles() {
        return deliveredFiles;
    }
}
