package it.egeos.cut3g.airgap.api.dto;

import java.time.Instant;
import java.util.List;

public class LatestContentResponse {
    public Instant snapshotTime;
    public String root;
    public int totalFiles;
    public long totalSizeBytes;
    public List<FolderContentDto> folders;
}
