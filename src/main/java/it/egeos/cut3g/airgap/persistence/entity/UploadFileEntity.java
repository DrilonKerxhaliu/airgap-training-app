package it.egeos.cut3g.airgap.persistence.entity;

import javax.persistence.*;
import java.time.Instant;
import java.util.UUID;

import it.egeos.cut3g.airgap.persistence.enums.UploadFileStatus;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "t_airgap_upload_file")
public class UploadFileEntity {

    @Id
    @Column(length = 36)
    private String id = UUID.randomUUID().toString();

    @Version
    private long version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "upload_package_id", nullable = false)
    private UploadPackageEntity uploadPackage;

    @Column(name = "relative_path", nullable = false, length = 1024)
    private String relativePath;

    @Column(name = "extracted_absolute_path", length = 1024)
    private String extractedAbsolutePath;

    @Column(name = "delivered_absolute_path", length = 1024)
    private String deliveredAbsolutePath;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "checksum_md5", length = 128)
    private String checksumMd5;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private UploadFileStatus status;

    @Column(length = 2000)
    private String note;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public String getId() {
        return id;
    }

    public long getVersion() {
        return version;
    }

    public UploadPackageEntity getUploadPackage() {
        return uploadPackage;
    }

    public void setUploadPackage(UploadPackageEntity uploadPackage) {
        this.uploadPackage = uploadPackage;
    }

    public String getRelativePath() {
        return relativePath;
    }

    public void setRelativePath(String relativePath) {
        this.relativePath = relativePath;
    }

    public String getExtractedAbsolutePath() {
        return extractedAbsolutePath;
    }

    public void setExtractedAbsolutePath(String extractedAbsolutePath) {
        this.extractedAbsolutePath = extractedAbsolutePath;
    }

    public String getDeliveredAbsolutePath() {
        return deliveredAbsolutePath;
    }

    public void setDeliveredAbsolutePath(String deliveredAbsolutePath) {
        this.deliveredAbsolutePath = deliveredAbsolutePath;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public String getChecksumMd5() {
        return checksumMd5;
    }

    public void setChecksumMd5(String checksumMd5) {
        this.checksumMd5 = checksumMd5;
    }

    public UploadFileStatus getStatus() {
        return status;
    }

    public void setStatus(UploadFileStatus status) {
        this.status = status;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
