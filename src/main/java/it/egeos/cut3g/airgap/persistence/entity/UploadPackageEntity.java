package it.egeos.cut3g.airgap.persistence.entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.Instant;
import java.util.UUID;

import it.egeos.cut3g.airgap.persistence.enums.UploadPackageStatus;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import javax.persistence.*;

@Entity
@Table(name = "t_airgap_upload_package")
public class UploadPackageEntity {

    @Id
    @Column(length = 36)
    private String id = UUID.randomUUID().toString();

    @Column(name = "package_name", nullable = false, unique = true)
    private String packageName;

    @Column(name = "sequence_index", nullable = false, unique = true)
    private long sequenceIndex;

    @Column(name = "original_tar_path", nullable = false, length = 1024)
    private String originalTarPath;

    @Column(name = "archived_tar_path", length = 1024)
    private String archivedTarPath;

    @Column(name = "work_dir_path", length = 1024)
    private String workDirPath;

    @Column(name = "outer_dir_path", length = 1024)
    private String outerDirPath;

    @Column(name = "data_dir_path", length = 1024)
    private String dataDirPath;

    @Column(name = "manifest_relative_path", length = 512)
    private String manifestRelativePath;

    @Column(name = "manifest_md5_data_tar", length = 128)
    private String manifestMd5DataTar;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private UploadPackageStatus status;

    @Column(length = 2000)
    private String note;

    @Column(name = "file_count")
    private Integer fileCount;

    @Column(name = "total_size_bytes")
    private Long totalSizeBytes;

    @Column(name = "imported_at")
    private Instant importedAt;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "removed_at")
    private Instant removedAt;

    @Column(name = "uploaded_by", length = 64, nullable = false)
    private String uploadedBy;

    public String getId() {
        return id;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public long getSequenceIndex() {
        return sequenceIndex;
    }

    public void setSequenceIndex(long sequenceIndex) {
        this.sequenceIndex = sequenceIndex;
    }

    public String getOriginalTarPath() {
        return originalTarPath;
    }

    public void setOriginalTarPath(String originalTarPath) {
        this.originalTarPath = originalTarPath;
    }

    public String getArchivedTarPath() {
        return archivedTarPath;
    }

    public void setArchivedTarPath(String archivedTarPath) {
        this.archivedTarPath = archivedTarPath;
    }

    public String getWorkDirPath() {
        return workDirPath;
    }

    public void setWorkDirPath(String workDirPath) {
        this.workDirPath = workDirPath;
    }

    public String getOuterDirPath() {
        return outerDirPath;
    }

    public void setOuterDirPath(String outerDirPath) {
        this.outerDirPath = outerDirPath;
    }

    public String getDataDirPath() {
        return dataDirPath;
    }

    public void setDataDirPath(String dataDirPath) {
        this.dataDirPath = dataDirPath;
    }

    public String getManifestRelativePath() {
        return manifestRelativePath;
    }

    public void setManifestRelativePath(String manifestRelativePath) {
        this.manifestRelativePath = manifestRelativePath;
    }

    public String getManifestMd5DataTar() {
        return manifestMd5DataTar;
    }

    public void setManifestMd5DataTar(String manifestMd5DataTar) {
        this.manifestMd5DataTar = manifestMd5DataTar;
    }

    public UploadPackageStatus getStatus() {
        return status;
    }

    public void setStatus(UploadPackageStatus status) {
        this.status = status;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public Integer getFileCount() {
        return fileCount;
    }

    public void setFileCount(Integer fileCount) {
        this.fileCount = fileCount;
    }

    public Long getTotalSizeBytes() {
        return totalSizeBytes;
    }

    public void setTotalSizeBytes(Long totalSizeBytes) {
        this.totalSizeBytes = totalSizeBytes;
    }

    public Instant getImportedAt() {
        return importedAt;
    }

    public void setImportedAt(Instant importedAt) {
        this.importedAt = importedAt;
    }

    public Instant getArchivedAt() {
        return archivedAt;
    }

    public void setArchivedAt(Instant archivedAt) {
        this.archivedAt = archivedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getRemovedAt() {
        return removedAt;
    }

    public void setRemovedAt(Instant removedAt) {
        this.removedAt = removedAt;
    }

    public String getUploadedBy() {
        return uploadedBy;
    }
    public  void setUploadedBy(String uploadedBy) {this.uploadedBy = uploadedBy;}
}