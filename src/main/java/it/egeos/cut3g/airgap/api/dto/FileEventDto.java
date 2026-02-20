package it.egeos.cut3g.airgap.api.dto;

import it.egeos.cut3g.airgap.persistence.enums.FileItemState;

import java.time.Instant;

public class FileEventDto {

        private String id;
        private String folder;
        private String filename;
        private long sizeBytes;
        private Instant arrivedAt;
        private FileItemState state;

        // getters / setters

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getFolder() {
        return folder;
    }

    public void setFolder(String folder) {
        this.folder = folder;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public Instant getArrivedAt() {
        return arrivedAt;
    }

    public void setArrivedAt(Instant arrivedAt) {
        this.arrivedAt = arrivedAt;
    }

    public FileItemState getState() {
        return state;
    }

    public void setState(FileItemState state) {
        this.state = state;
    }
}
