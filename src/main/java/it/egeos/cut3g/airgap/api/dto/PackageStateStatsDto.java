package it.egeos.cut3g.airgap.api.dto;

import it.egeos.cut3g.airgap.persistence.enums.PackageState;

public class PackageStateStatsDto {

    private PackageState state;
    private long count;

    public PackageStateStatsDto(PackageState state, long count) {
        this.state = state;
        this.count = count;
    }

    public PackageState getState() { return state; }
    public long getCount() { return count; }
}
