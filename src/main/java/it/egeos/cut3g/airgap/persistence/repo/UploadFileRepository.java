package it.egeos.cut3g.airgap.persistence.repo;

import it.egeos.cut3g.airgap.persistence.entity.UploadFileEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface UploadFileRepository
        extends JpaRepository<UploadFileEntity, String> {
    List<UploadFileEntity> findByUploadPackageId(String uploadPackageId);

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void deleteByUploadPackageId(String uploadPackageId);
}