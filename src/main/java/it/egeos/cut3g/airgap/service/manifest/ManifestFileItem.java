package it.egeos.cut3g.airgap.service.manifest;

public class ManifestFileItem {
    private String relativePath;
    private long sizeBytes;
    private String md5;

    public ManifestFileItem() {
    }

    public ManifestFileItem(String relativePath, long sizeBytes) {
        this.relativePath = relativePath;
        this.sizeBytes = sizeBytes;
        this.md5 = md5;
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

    public String getMd5() {
        return md5;
    }

    public void setMd5(String md5) {
        this.md5 = md5;
    }
}
