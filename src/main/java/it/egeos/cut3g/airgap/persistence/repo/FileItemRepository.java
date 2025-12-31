package it.egeos.cut3g.airgap.persistence.repo;

import it.egeos.cut3g.airgap.persistence.entity.FileItemEntity;
import it.egeos.cut3g.airgap.persistence.enums.FileItemState;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import javax.persistence.LockModeType;
import java.util.List;

public interface FileItemRepository extends JpaRepository<FileItemEntity, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(" select f from FileItemEntity f where f.state = :state order by f.receivedTime asc ")
    List<FileItemEntity> findByStateForUpdate(
            @Param("state") FileItemState state);

    @Query(" select count(f) from FileItemEntity f where f.state = :state ")
    long countByState(@Param("state") FileItemState state);
}
