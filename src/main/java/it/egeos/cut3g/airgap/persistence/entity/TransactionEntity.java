package it.egeos.cut3g.airgap.persistence.entity;

import it.egeos.cut3g.airgap.persistence.enums.TransactionState;
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
                @Index(name = "idx_tx_package_id", columnList = "package_id")
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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionState state = TransactionState.STARTED;

    @Column(name = "start_ts", nullable = false)
    private Instant startTs;

    @Column(name = "end_ts", nullable = true)
    private Instant endTs;

    @Column(name = "note", nullable = true)
    private String note;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    public String getId() { return id; }

    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }

    public TransactionState getState() {
        return state;
    }

    public void setState(TransactionState state) {
        this.state = state;
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

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public PackageEntity getAirgapPackage() { return airgapPackage; }
    public void setAirgapPackage(PackageEntity airgapPackage) { this.airgapPackage = airgapPackage; }

}

