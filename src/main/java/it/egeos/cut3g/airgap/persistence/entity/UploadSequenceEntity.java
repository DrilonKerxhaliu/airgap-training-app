package it.egeos.cut3g.airgap.persistence.entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "t_airgap_upload_sequence")
public class UploadSequenceEntity {

    @Id
    private Long id;

    @Column(name = "last_sequence_index", nullable = false)
    private long lastSequenceIndex;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public long getLastSequenceIndex() {
        return lastSequenceIndex;
    }

    public void setLastSequenceIndex(long lastSequenceIndex) {
        this.lastSequenceIndex = lastSequenceIndex;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}