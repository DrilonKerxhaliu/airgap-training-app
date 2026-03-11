package it.egeos.cut3g.airgap.persistence.entity;

import it.egeos.cut3g.airgap.persistence.enums.Direction;
import it.egeos.cut3g.airgap.persistence.enums.PackageState;
import it.egeos.cut3g.airgap.persistence.enums.TransactionState;
import it.egeos.cut3g.airgap.persistence.enums.UploadPackageStatus;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import javax.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "t_airgap_transactions",
        indexes = {
                @Index(name = "idx_tx_state", columnList = "state"),
                @Index(name = "idx_tx_package_id", columnList = "package_id"),
                @Index(name = "idx_tx_upload_package_id", columnList = "upload_package_id"),
                @Index(name = "idx_tx_direction", columnList = "direction")
        }
)
public class TransactionEntity {

    @Id
    @Column(length = 36)
    private String id = UUID.randomUUID().toString();

    @Version
    private long version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "package_id")
    private PackageEntity airgapPackage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "upload_package_id")
    private UploadPackageEntity uploadPackage;

    @Enumerated(EnumType.STRING)
    @Column(name = "package_state")
    private PackageState packageState;

    @Enumerated(EnumType.STRING)
    @Column(name = "upload_package_status")
    private UploadPackageStatus uploadPackageStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionState state = TransactionState.STARTED;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, length = 32)
    private Direction direction;

    @Column(name = "start_ts", nullable = false)
    private Instant startTs;

    @Column(name = "end_ts")
    private Instant endTs;

    @Column(name = "note")
    private String note;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    @Column(name = "initiated_by", length = 64, nullable = false)
    private String initiatedBy;

    public String getId() { return id; }

    public long getVersion() {
        return version;
    }

    public TransactionState getState() {
        return state;
    }

    public void setState(TransactionState state) {
        this.state = state;
    }

    public PackageEntity getAirgapPackage() {
        return airgapPackage;
    }

    public void setAirgapPackage(PackageEntity airgapPackage) {
        this.airgapPackage = airgapPackage;
    }

    public UploadPackageEntity getUploadPackage() {
        return uploadPackage;
    }

    public void setUploadPackage(UploadPackageEntity uploadPackage) {
        this.uploadPackage = uploadPackage;
    }

    public PackageState getPackageState() {
        return packageState;
    }

    public void setPackageState(PackageState packageState) {
        this.packageState = packageState;
    }

    public Direction getDirection() {
        return direction;
    }

    public void setDirection(Direction direction) {
        this.direction = direction;
    }

    public Instant getStartTs() {
        return startTs;
    }

    public void setStartTs(Instant startTs) {
        this.startTs = startTs;
    }

    public Instant getEndTs() {
        return endTs;
    }

    public void setEndTs(Instant endTs) {
        this.endTs = endTs;
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

    public String getInitiatedBy() {
        return initiatedBy;
    }

    public void setInitiatedBy(String initiatedBy) {
        this.initiatedBy = initiatedBy;
    }

    public UploadPackageStatus getUploadPackageStatus() {
        return uploadPackageStatus;
    }

    public void setUploadPackageStatus(UploadPackageStatus uploadPackageStatus) {
        this.uploadPackageStatus = uploadPackageStatus;
    }
}