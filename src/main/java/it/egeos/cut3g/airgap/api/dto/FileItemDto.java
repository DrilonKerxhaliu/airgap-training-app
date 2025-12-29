package it.egeos.cut3g.airgap.api.dto;

public class FileItemDto {
    private String id;
    private String relativePath;
    private long sizeBytes;
    private String state;

    public FileItemDto() {}

    public FileItemDto(String id, String relativePath, long sizeBytes, String state) {
        this.id = id;
        this.relativePath = relativePath;
        this.sizeBytes = sizeBytes;
        this.state = state;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getRelativePath() { return relativePath; }
    public void setRelativePath(String relativePath) { this.relativePath = relativePath; }

    public long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(long sizeBytes) { this.sizeBytes = sizeBytes; }

    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
}
