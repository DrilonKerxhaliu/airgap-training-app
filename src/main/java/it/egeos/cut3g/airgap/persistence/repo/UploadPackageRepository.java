package it.egeos.cut3g.airgap.persistence.repo;

import it.egeos.cut3g.airgap.persistence.entity.UploadPackageEntity;
import it.egeos.cut3g.airgap.persistence.enums.UploadPackageStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface UploadPackageRepository
        extends JpaRepository<UploadPackageEntity, String> {
    Optional<UploadPackageEntity> findByPackageName(String packageName);
    boolean existsByPackageName(String packageName);
    List<UploadPackageEntity> findByStatus(UploadPackageStatus status);

    @Query("select p from UploadPackageEntity p where p.status = :status and p.archivedAt < :threshold")
    List<UploadPackageEntity> findArchivedBefore(@Param("status") UploadPackageStatus status, @Param("threshold") Instant threshold);
}
