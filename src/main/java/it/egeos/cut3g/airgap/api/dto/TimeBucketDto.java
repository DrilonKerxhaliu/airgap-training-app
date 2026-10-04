package it.egeos.cut3g.airgap.api.dto;

import java.time.Instant;

public class TimeBucketDto {

    private Instant time;
    private long count;

    public TimeBucketDto(Instant time, long count) {
        this.time = time;
        this.count = count;
    }

    public Instant getTime() { return time; }
    public long getCount() { return count; }
}
