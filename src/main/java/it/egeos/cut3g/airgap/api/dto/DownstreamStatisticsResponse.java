package it.egeos.cut3g.airgap.api.dto;

import java.time.Instant;

public class DownstreamStatisticsResponse {
    public long totalPackages;
    public long totalExported;
    public long totalReady;
    public long totalNewFiles;
    public long totalBytesPackaged;
    public long totalBytesExported;
    public Instant lastPackageTime;
}
