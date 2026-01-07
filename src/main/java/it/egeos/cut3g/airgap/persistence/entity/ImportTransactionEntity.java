package it.egeos.cut3g.airgap.persistence.entity;

import it.egeos.cut3g.airgap.persistence.enums.ImportOutcome;

import javax.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "t_airgap_import_tx")
public class ImportTransactionEntity {

    @Id
    @Column(length = 36)
    private String id = UUID.randomUUID().toString();

    @Version
    private long version;

    @Column(nullable = false)
    private long uploadSequenceNumber;

    @Column(nullable = false)
    private Instant receivedTime = Instant.now();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ImportOutcome outcome;

    @Column(nullable = true)
    private String notes;

    public String getId() { return id; }

    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }

    public long getUploadSequenceNumber() { return uploadSequenceNumber; }
    public void setUploadSequenceNumber(long uploadSequenceNumber) { this.uploadSequenceNumber = uploadSequenceNumber; }

    public Instant getReceivedTime() { return receivedTime; }
    public void setReceivedTime(Instant receivedTime) { this.receivedTime = receivedTime; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public ImportOutcome getOutcome() {
        return outcome;
    }

    public void setOutcome(ImportOutcome outcome) {
        this.outcome = outcome;
    }
}
