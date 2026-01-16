package it.egeos.cut3g.airgap.persistence.repo;

import it.egeos.cut3g.airgap.persistence.entity.TransactionEntity;
import it.egeos.cut3g.airgap.persistence.enums.TransactionState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<TransactionEntity, UUID> {

    boolean existsByState(TransactionState state);

    Optional<TransactionEntity> findTopByStateOrderByStartTsDesc(TransactionState state);

    Optional<TransactionEntity> findTopByOrderByStartTsDesc();

    @Query("select count(t) from TransactionEntity t where t.state = :state")
    long countByState(@Param("state") TransactionState state);
}
