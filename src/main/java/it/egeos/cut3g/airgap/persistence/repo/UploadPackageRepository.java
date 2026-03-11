package it.egeos.cut3g.airgap.persistence.repo;

import it.egeos.cut3g.airgap.persistence.entity.UploadPackageEntity;
import it.egeos.cut3g.airgap.persistence.enums.UploadPackageStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UploadPackageRepository
        extends JpaRepository<UploadPackageEntity, String> {
    Optional<UploadPackageEntity> findByPackageName(String packageName);
    boolean existsByPackageName(String packageName);
    List<UploadPackageEntity> findByStatus(UploadPackageStatus status);
}
