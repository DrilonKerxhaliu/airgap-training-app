package it.egeos.cut3g.airgap.persistence.repo;

import it.egeos.cut3g.airgap.persistence.entity.FileItemEntity;
import org.springframework.data.jpa.repository.*;

import javax.persistence.LockModeType;
import java.util.List;

public interface FileItemRepository extends JpaRepository<FileItemEntity, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from FileItemEntity f where f.state = it.egeos.cut3g.airgap.persistence.entity.FileItemEntity$State.NEW order by f.receivedTime asc")
    List<FileItemEntity> findNewFilesForUpdate();

    @Query("select count(f) from FileItemEntity f where f.state = it.egeos.cut3g.airgap.persistence.entity.FileItemEntity$State.NEW")
    long countNewFiles();
}
