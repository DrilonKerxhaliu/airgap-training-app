package it.egeos.cut3g.airgap.persistence.repo;

import it.egeos.cut3g.airgap.persistence.entity.UploadSequenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import javax.persistence.LockModeType;
import java.util.Optional;

@Repository
public interface UploadSequenceRepository
        extends JpaRepository<UploadSequenceEntity, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from UploadSequenceEntity s where s.id = :id")
    Optional<UploadSequenceEntity> findByIdForUpdate(@Param("id") Long id);

    @Query("SELECT s.lastSequenceIndex FROM UploadSequenceEntity s WHERE s.id = 1")
    long findLastSequence();

    Optional<UploadSequenceEntity> findById(Long id);
}
