package it.egeos.cut3g.airgap.api.dto;

public class UpstreamStatisticsResponse {

    private long totalPackages;
    private long uploadedPackages;

    public long getTotalPackages() {
        return totalPackages;
    }

    public void setTotalPackages(long totalPackages) {
        this.totalPackages = totalPackages;
    }

    public long getUploadedPackages() {
        return uploadedPackages;
    }

    public void setUploadedPackages(long uploadedPackages) {
        this.uploadedPackages = uploadedPackages;
    }
}