package it.egeos.cut3g.airgap.persistence.repo;

import it.egeos.cut3g.airgap.persistence.entity.FileItemEntity;
import it.egeos.cut3g.airgap.persistence.enums.FileItemState;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import javax.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface FileItemRepository extends JpaRepository<FileItemEntity, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(" select f from FileItemEntity f where f.state = :state order by f.receivedTime asc ")
    List<FileItemEntity> findByStateForUpdate(@Param("state") FileItemState state);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(" select f from FileItemEntity f where f.id in :ids ")
    List<FileItemEntity> findByIdsForUpdate(@Param("ids") List<String> ids);


    @Query(" select count(f) from FileItemEntity f where f.state = :state ")
    long countByState(@Param("state") FileItemState state);

    @Query(" select coalesce(sum(f.sizeBytes), 0) from FileItemEntity f where f.state = :state ")
    long sumSizeByState(@Param("state") FileItemState state);

    Optional<FileItemEntity> findByRelativePath(String relativePath);

    @Query("select f from FileItemEntity f where f.airgapPackage.id = :packageId order by f.relativePath asc")
    List<FileItemEntity> findByPackageId(@Param("packageId") String packageId);

    @Modifying
    @Query("update FileItemEntity f set f.state = :state where f.airgapPackage.id = :packageId ")
    int updateStateByPackageId(@Param("packageId") String packageId, @Param("state") FileItemState state);
}
