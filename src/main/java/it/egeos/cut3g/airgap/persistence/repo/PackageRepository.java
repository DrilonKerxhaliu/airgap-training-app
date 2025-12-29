package it.egeos.cut3g.airgap.persistence.repo;

import it.egeos.cut3g.airgap.api.Direction;
import it.egeos.cut3g.airgap.persistence.entity.PackageEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface PackageRepository extends JpaRepository<PackageEntity, String> {
    List<PackageEntity> findByDirectionAndStateOrderByTransactionStopTimeDesc(Direction direction, PackageEntity.State state);
    List<PackageEntity> findByDirectionAndStateInOrderByTransactionStopTimeDesc(Direction direction, List<PackageEntity.State> states);
    List<PackageEntity> findByStateNotAndTransactionStopTimeBefore(PackageEntity.State state, Instant cutoff);
    long countByDirection(Direction direction);
}
