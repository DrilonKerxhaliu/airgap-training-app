package it.egeos.cut3g.airgap.api.dto;

public class FileContentDto {
    public String relativePath;
    public long sizeBytes;

    public FileContentDto() {}

    public FileContentDto(String relativePath, long sizeBytes) {
        this.relativePath = relativePath;
        this.sizeBytes = sizeBytes;
    }
}