package it.egeos.cut3g.airgap.persistence.repo;

import it.egeos.cut3g.airgap.api.dto.PackageStateStatsDto;
import it.egeos.cut3g.airgap.persistence.entity.PackageEntity;
import it.egeos.cut3g.airgap.persistence.enums.PackageState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface PackageRepository extends JpaRepository<PackageEntity, String> {

    @Query("select p from PackageEntity p where p.state in :states order by p.transactionStopTime desc nulls last, p.transactionStartTime desc nulls last")
    List<PackageEntity> findByStates(@Param("states") List<PackageState> states);

    @Query("select p from PackageEntity p where p.exportedAt is not null order by p.exportedAt desc")
    List<PackageEntity> findHistory();

    @Query("select max(p.transactionStopTime) from PackageEntity p")
    Instant findLastPackageTime();

    @Query("select coalesce(sum(p.totalSizeBytes),0) from PackageEntity p")
    long sumAllPackagedBytes();

    @Query("select coalesce(sum(p.totalSizeBytes),0) from PackageEntity p where p.exportedAt is not null")
    long sumExportedBytes();

    @Query("select count(p) from PackageEntity p where p.exportedAt is not null")
    long countExported();

    @Query("select p from PackageEntity p order by p.transactionStopTime desc nulls last, p.transactionStartTime desc nulls last")
    List<PackageEntity> findLatestFirst();

    @Query("select coalesce(max(p.progressiveNumber), 0) from PackageEntity p")
    long findMaxProgressiveNumber();

    @Query("select p from PackageEntity p where p.state = :state and p.exportedAt < :threshold")
    List<PackageEntity> findArchivedBefore(@Param("state") PackageState state, @Param("threshold") Instant threshold);

    @Query("select new it.egeos.cut3g.airgap.api.dto.PackageStateStatsDto(p.state, count(p)) from PackageEntity p group by p.state")
    List<PackageStateStatsDto> countPackagesByState();


}
