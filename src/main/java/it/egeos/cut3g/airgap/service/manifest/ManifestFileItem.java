package it.egeos.cut3g.airgap.service.manifest;

public class ManifestFileItem {
    private String relativePath;
    private long sizeBytes;

    public ManifestFileItem() {
    }

    public ManifestFileItem(String relativePath, long sizeBytes) {
        this.relativePath = relativePath;
        this.sizeBytes = sizeBytes;
    }

    public String getRelativePath() {
        return relativePath;
    }

    public void setRelativePath(String relativePath) {
        this.relativePath = relativePath;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }
}
