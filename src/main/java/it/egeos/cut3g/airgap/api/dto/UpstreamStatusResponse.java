package it.egeos.cut3g.airgap.api.dto;

import java.time.Instant;

public class UpstreamStatusResponse {
    public boolean transactionOngoing;
    public long readyForUploadCount;
    public Instant lastTransactionStart;
    public String lastPackageId;
    public String lastPackageState;
}
