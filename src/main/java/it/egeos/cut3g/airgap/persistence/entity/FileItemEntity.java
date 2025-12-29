package it.egeos.cut3g.airgap.persistence.entity;

import javax.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "t_airgap_file_item", indexes = {
        @Index(name = "idx_airgap_file_state", columnList = "state"),
        @Index(name = "idx_airgap_file_received", columnList = "receivedTime")
})
public class FileItemEntity {

    public enum State {
        NEW,
        ACTIVE,
        PACKED
    }

    @Id
    @Column(length = 36)
    private String id = UUID.randomUUID().toString();

    @Version
    private long version;

    @Column(nullable = false)
    private String relativePath;

    @Column(nullable = false)
    private long sizeBytes;

    @Column(nullable = false)
    private Instant receivedTime = Instant.now();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private State state = State.NEW;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "package_id")
    private PackageEntity airgapPackage;

    public String getId() { return id; }

    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }

    public String getRelativePath() { return relativePath; }
    public void setRelativePath(String relativePath) { this.relativePath = relativePath; }

    public long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(long sizeBytes) { this.sizeBytes = sizeBytes; }

    public Instant getReceivedTime() { return receivedTime; }
    public void setReceivedTime(Instant receivedTime) { this.receivedTime = receivedTime; }

    public State getState() { return state; }
    public void setState(State state) { this.state = state; }

    public PackageEntity getAirgapPackage() { return airgapPackage; }
    public void setAirgapPackage(PackageEntity airgapPackage) { this.airgapPackage = airgapPackage; }
}
