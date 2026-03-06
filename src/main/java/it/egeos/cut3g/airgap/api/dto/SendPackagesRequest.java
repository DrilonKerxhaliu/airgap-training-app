package it.egeos.cut3g.airgap.api.dto;

import java.util.List;

public class SendPackagesRequest {

    private List<String> packageIds;

    public List<String> getPackageIds() {
        return packageIds;
    }

    public void setPackageIds(List<String> packageIds) {
        this.packageIds = packageIds;
    }
}
