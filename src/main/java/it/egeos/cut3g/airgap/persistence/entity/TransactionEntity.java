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
    @GeneratedValue
    private UUID id;

    @Version
    private long version;

    /**
     * Example: PKG_20250101_000123.tar
     */
    @Column(name = "package_id", nullable = false)
    private String packageId;

    /**
     * Transaction lifecycle state
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionState state = TransactionState.STARTED;

    /**
     * Business timestamps
     */
    @Column(name = "start_ts", nullable = false, updatable = false)
    private Instant startTs;

    @Column(name = "end_ts")
    private Instant endTs;

    /**
     * Text for errors
     */
    @Column(name = "note")
    private String note;

    /**
     * Audit timestamps
     */
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    public UUID getId() {
        return id;
    }

    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }

    public String getPackageId() {
        return packageId;
    }

    public void setPackageId(String packageId) {
        this.packageId = packageId;
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
}

