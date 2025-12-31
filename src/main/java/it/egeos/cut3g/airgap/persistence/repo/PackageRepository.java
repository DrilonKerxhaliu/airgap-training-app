package it.egeos.cut3g.airgap.persistence.repo;

import it.egeos.cut3g.airgap.api.Direction;
import it.egeos.cut3g.airgap.persistence.entity.PackageEntity;
import it.egeos.cut3g.airgap.persistence.enums.PackageState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface PackageRepository extends JpaRepository<PackageEntity, String> {
    List<PackageEntity> findByDirectionAndStateOrderByTransactionStopTimeDesc(Direction direction, PackageState state);
    List<PackageEntity> findByDirectionAndStateInOrderByTransactionStopTimeDesc(Direction direction, List<PackageState> states);
    List<PackageEntity> findByStateNotAndTransactionStopTimeBefore(PackageState state, Instant cutoff);
    long countByDirection(Direction direction);
}
