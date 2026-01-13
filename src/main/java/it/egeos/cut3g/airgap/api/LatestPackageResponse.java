package it.egeos.cut3g.airgap.api;

import it.egeos.cut3g.airgap.api.dto.PackageDto;

public class LatestPackageResponse {

    private boolean success;
    private String message;
    private PackageDto packageInfo;

    public LatestPackageResponse(boolean success, String message, PackageDto packageInfo) {
        this.success = success;
        this.message = message;
        this.packageInfo = packageInfo;
    }

    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
    public PackageDto getPackageInfo() { return packageInfo; }
}
