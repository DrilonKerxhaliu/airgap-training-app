package it.egeos.cut3g.airgap.api.dto;

import it.egeos.cut3g.airgap.persistence.entity.PackageEntity;
import it.egeos.cut3g.airgap.persistence.enums.PackageState;

import java.time.Instant;

public class PackageDto {

    public String id;
    public String packageName;
    public String packagePath;
    public String md5DataTar;
    public long totalSizeBytes;
    public PackageState state;
    public Instant transactionStartTime;
    public Instant transactionStopTime;
    public Instant exportedAt;
    public String createdBy;

    public static PackageDto from(PackageEntity p) {
        PackageDto dto = new PackageDto();
        dto.id = p.getId();
        dto.packageName = p.getPackageName();
        dto.packagePath = p.getPackagePath();
        dto.md5DataTar = p.getMd5DataTar();
        dto.totalSizeBytes = p.getTotalSizeBytes();
        dto.exportedAt = (p.getExportedAt());
        dto.state = p.getState();
        dto.transactionStartTime = p.getTransactionStartTime();
        dto.transactionStopTime = p.getTransactionStopTime();
        dto.createdBy = p.getCreatedBy();
        return dto;
    }
}

