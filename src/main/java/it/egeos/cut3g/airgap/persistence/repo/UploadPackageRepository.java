package it.egeos.cut3g.airgap.persistence.repo;

import it.egeos.cut3g.airgap.persistence.entity.UploadPackageEntity;
import it.egeos.cut3g.airgap.persistence.enums.UploadPackageStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface UploadPackageRepository
        extends JpaRepository<UploadPackageEntity, String> {
    Optional<UploadPackageEntity> findByPackageName(String packageName);
    boolean existsByPackageName(String packageName);
    List<UploadPackageEntity> findByStatus(UploadPackageStatus status);
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void deleteById(String uploadPackageId);

    @Query("select p from UploadPackageEntity p where p.status = :status and p.archivedAt < :threshold")
    List<UploadPackageEntity> findArchivedBefore(@Param("status") UploadPackageStatus status, @Param("threshold") Instant threshold);

    @Query(value = "SELECT p.status, COUNT(*) FROM airgap.t_airgap_upload_package p WHERE COALESCE(p.imported_at, p.archived_at, p.removed_at, p.created_at) BETWEEN :from AND :to GROUP BY p.status", nativeQuery = true)
    List<Object[]> countByStateBetweenRaw(@Param("from") Instant from, @Param("to") Instant to);

    @Query(value = "SELECT p.status, COUNT(*) FROM airgap.t_airgap_upload_package p WHERE p.status IN (:states) AND COALESCE(p.imported_at, p.archived_at, p.removed_at, p.created_at) BETWEEN :from AND :to GROUP BY p.status", nativeQuery = true)
    List<Object[]> countCriticalStatesBetweenRaw(@Param("states") List<String> states, @Param("from") Instant from, @Param("to") Instant to);

    @Query(value = "SELECT DATE(COALESCE(p.imported_at, p.archived_at, p.removed_at, p.created_at)), COUNT(*) FROM airgap.t_airgap_upload_package p WHERE p.status = :state AND COALESCE(p.imported_at, p.archived_at, p.removed_at, p.created_at) BETWEEN :from AND :to GROUP BY DATE(COALESCE(p.imported_at, p.archived_at, p.removed_at, p.created_at)) ORDER BY DATE(COALESCE(p.imported_at, p.archived_at, p.removed_at, p.created_at))", nativeQuery = true)
    List<Object[]> heatmapRaw(@Param("state") String state, @Param("from") Instant from, @Param("to") Instant to);

}
