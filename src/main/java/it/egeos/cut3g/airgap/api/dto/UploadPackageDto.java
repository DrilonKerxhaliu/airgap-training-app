package it.egeos.cut3g.airgap.api.dto;

import it.egeos.cut3g.airgap.persistence.entity.PackageEntity;
import it.egeos.cut3g.airgap.persistence.entity.UploadPackageEntity;
import it.egeos.cut3g.airgap.persistence.enums.PackageState;
import it.egeos.cut3g.airgap.persistence.enums.UploadPackageStatus;

import java.time.Instant;

public class UploadPackageDto {

    public String id;
    public String packageName;
    public String packagePath;
    public String md5DataTar;
    public Long totalSizeBytes;
    public UploadPackageStatus state;
    public Instant importedAt;
    public String uploadedBy;

    public static UploadPackageDto from(UploadPackageEntity p) {
        UploadPackageDto dto = new UploadPackageDto();
        dto.id = p.getId();
        dto.packageName = p.getPackageName();
        dto.packagePath = p.getOriginalTarPath();
        dto.md5DataTar = p.getManifestMd5DataTar();
        dto.totalSizeBytes = p.getTotalSizeBytes();
        dto.importedAt = (p.getImportedAt());
        dto.state = p.getStatus();
        dto.uploadedBy = p.getUploadedBy();
        return dto;
    }
}

