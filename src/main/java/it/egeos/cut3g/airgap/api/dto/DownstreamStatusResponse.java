package it.egeos.cut3g.airgap.api.dto;

import java.time.Instant;

public class DownstreamStatusResponse {
    public boolean transactionOngoing;
    public long newFilesCount;
    public long readyForDownloadCount;
    public Instant lastTransactionStart;
    public String lastPackageId;
    public String lastPackageState;
}