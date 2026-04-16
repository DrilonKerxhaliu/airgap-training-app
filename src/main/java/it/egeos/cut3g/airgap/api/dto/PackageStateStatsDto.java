package it.egeos.cut3g.airgap.api.dto;

import it.egeos.cut3g.airgap.persistence.enums.PackageState;

public class PackageStateStatsDto {

    private String state;
    private long count;

    public PackageStateStatsDto() {}

    public PackageStateStatsDto(String state, long count) {
        this.state = state;
        this.count = count;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public long getCount() {
        return count;
    }

    public void setCount(long count) {
        this.count = count;
    }
}
