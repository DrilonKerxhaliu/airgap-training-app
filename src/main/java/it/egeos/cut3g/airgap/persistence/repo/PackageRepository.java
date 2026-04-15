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

    @Query("SELECT new it.egeos.cut3g.airgap.api.dto.PackageStateStatsDto(p.state, COUNT(p)) FROM PackageEntity p group by p.state")
    List<PackageStateStatsDto> countPackagesByState();

    @Query(value = "SELECT p.state, COUNT(*) FROM t_airgap_package p WHERE p.transaction_start_time >= :from AND p.transaction_stop_time <= :to GROUP BY p.state", nativeQuery = true)
    List<Object[]> countByStateBetweenRaw(@Param("from") Instant from, @Param("to") Instant to);

    @Query(value = "SELECT p.state, COUNT(*) FROM t_airgap_package p WHERE p.state IN (:states) AND p.transaction_start_time >= :from AND p.transaction_stop_time <= :to GROUP BY p.state", nativeQuery = true)
    List<Object[]> countCriticalStatesBetweenRaw(@Param("states") List<String> states, @Param("from") Instant from, @Param("to") Instant to);

    @Query(value = "SELECT DATE(p.transaction_stop_time), COUNT(*) FROM t_airgap_package p WHERE p.state = :state AND p.transaction_start_time >= :from AND p.transaction_stop_time <= :to GROUP BY DATE(p.transaction_stop_time) ORDER BY DATE(p.transaction_stop_time)", nativeQuery = true)
    List<Object[]> heatmapRaw(@Param("state") String state, @Param("from") Instant from, @Param("to") Instant to);

}
