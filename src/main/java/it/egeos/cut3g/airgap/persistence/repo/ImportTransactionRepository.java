package it.egeos.cut3g.airgap.persistence.repo;

import it.egeos.cut3g.airgap.api.Direction;
import it.egeos.cut3g.airgap.persistence.entity.ImportTransactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ImportTransactionRepository extends JpaRepository<ImportTransactionEntity, String> {
    Optional<ImportTransactionEntity> findTopByDirectionOrderByUploadSequenceNumberDesc(Direction direction);
}
