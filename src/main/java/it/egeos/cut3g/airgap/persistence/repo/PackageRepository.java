package it.egeos.cut3g.airgap.persistence.repo;

import it.egeos.cut3g.airgap.persistence.entity.PackageEntity;
import it.egeos.cut3g.airgap.persistence.enums.PackageState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;

public interface PackageRepository extends JpaRepository<PackageEntity, String> {
    List<PackageEntity> findByStateOrderByTransactionStopTimeDesc(PackageState state);
    List<PackageEntity> findByStateInOrderByTransactionStopTimeDesc(List<PackageState> states);
    List<PackageEntity> findByStateNotAndTransactionStopTimeBefore(PackageState state, Instant cutoff);

    @Query("select coalesce(max(p.progressiveNumber), 0) from PackageEntity p")
    long findMaxProgressiveNumber();

}
