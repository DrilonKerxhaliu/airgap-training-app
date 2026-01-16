package it.egeos.cut3g.airgap.persistence.entity;

import it.egeos.cut3g.airgap.persistence.enums.PackageState;

import javax.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "t_airgap_package")
public class PackageEntity {

    @Id
    @Column(length = 36)
    private String id = UUID.randomUUID().toString();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PackageState state = PackageState.NEW;

    @Column(name = "progressive_number", nullable = false, unique = true, insertable = false, updatable = false)
    private Long progressiveNumber;

    @Column(name = "transaction_start_time", nullable = false)
    private Instant transactionStartTime;

    @Column(name = "transaction_stop_time", nullable = false)
    private Instant transactionStopTime;

    @Column(name = "exported_at", nullable = true)
    private Instant exportedAt;

    @Column(name = "package_name", nullable = false)
    private String packageName;

    @Column(name = "package_path", nullable = false)
    private String packagePath;

    @Column(name = "md5_data_tar", nullable = false)
    private String md5DataTar;

    @Column(name = "total_size_bytes", nullable = false)
    private long totalSizeBytes;

    @Column(name = "notes", nullable = true)
    private String notes;

    @OneToMany(mappedBy = "airgapPackage", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<FileItemEntity> files = new ArrayList<>();

    public String getId() { return id; }

    public PackageState getState() {
        return state;
    }

    public void setState(PackageState state) {
        this.state = state;
    }

    public long getProgressiveNumber() { return progressiveNumber; }
    public void setProgressiveNumber(long progressiveNumber) { this.progressiveNumber = progressiveNumber; }

    public Instant getTransactionStartTime() { return transactionStartTime; }
    public void setTransactionStartTime(Instant transactionStartTime) { this.transactionStartTime = transactionStartTime; }

    public Instant getTransactionStopTime() { return transactionStopTime; }
    public void setTransactionStopTime(Instant transactionStopTime) { this.transactionStopTime = transactionStopTime; }

    public String getPackageName() { return packageName; }
    public void setPackageName(String packageName) { this.packageName = packageName; }

    public Instant getExportedAt() {
        return exportedAt;
    }

    public void setExportedAt(Instant exportedAt) {
        this.exportedAt = exportedAt;
    }

    public String getPackagePath() { return packagePath; }
    public void setPackagePath(String packagePath) { this.packagePath = packagePath; }

    public String getMd5DataTar() { return md5DataTar; }
    public void setMd5DataTar(String md5DataTar) { this.md5DataTar = md5DataTar; }

    public long getTotalSizeBytes() { return totalSizeBytes; }
    public void setTotalSizeBytes(long totalSizeBytes) { this.totalSizeBytes = totalSizeBytes; }

    public List<FileItemEntity> getFiles() { return files; }
    public void setFiles(List<FileItemEntity> files) { this.files = files; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
