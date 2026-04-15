package it.egeos.cut3g.airgap.service.packaging;

import it.egeos.cut3g.airgap.api.dto.PackageStateStatsDto;
import it.egeos.cut3g.airgap.api.dto.StatsPackageDto;
import it.egeos.cut3g.airgap.api.dto.TimeBucketDto;
import it.egeos.cut3g.airgap.api.dto.TransactionStatsDto;
import it.egeos.cut3g.airgap.persistence.repo.PackageRepository;
import it.egeos.cut3g.airgap.persistence.repo.TransactionRepository;
import it.egeos.cut3g.airgap.persistence.repo.UploadPackageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class StatisticsService {

    @Autowired
    private PackageRepository packageRepository;

    @Autowired
    private UploadPackageRepository uploadPackageRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    public List<StatsPackageDto> packageStateStats() {
        return packageRepository.countPackagesByState();
    }

    public List<TransactionStatsDto> transactionStats() {
        return transactionRepository.countTransactionsByState();
    }

    public List<PackageStateStatsDto> getStateDistribution(String direction, Instant from, Instant to) {

        List<Object[]> raw;

        if ("downstream".equalsIgnoreCase(direction)) {
            raw = packageRepository.countByStateBetweenRaw(from, to);
        } else if ("upstream".equalsIgnoreCase(direction)) {
            raw = uploadPackageRepository.countByStateBetweenRaw(from, to);
        } else {
            throw new IllegalArgumentException("Invalid direction");
        }

        return raw.stream()
                .map(r -> new PackageStateStatsDto(
                        (String) r[0],
                        ((Number) r[1]).longValue()
                ))
                .collect(Collectors.toList());
    }


    public List<PackageStateStatsDto> getCriticalStates(String direction, Instant from, Instant to) {

        List<String> critical = List.of("FAILED", "REJECTED", "DELETED");
        List<Object[]> raw;

        if ("downstream".equalsIgnoreCase(direction)) {
            raw = packageRepository.countCriticalStatesBetweenRaw(critical, from, to);
        } else if ("upstream".equalsIgnoreCase(direction)) {
            raw = uploadPackageRepository.countCriticalStatesBetweenRaw(critical, from, to);
        } else {
            throw new IllegalArgumentException("Invalid direction");
        }

        return raw.stream()
                .map(r -> new PackageStateStatsDto(
                        (String) r[0],
                        ((Number) r[1]).longValue()
                ))
                .collect(Collectors.toList());
    }

    public List<TimeBucketDto> getHeatmap(String direction, String state, Instant from, Instant to) {

        List<Object[]> raw;

        if ("downstream".equalsIgnoreCase(direction)) {
            raw = packageRepository.heatmapRaw(state, from, to);
        } else if ("upstream".equalsIgnoreCase(direction)) {
            raw = uploadPackageRepository.heatmapRaw(state, from, to);
        } else {
            throw new IllegalArgumentException("Invalid direction");
        }

        return raw.stream()
                .map(r -> {
                    java.sql.Date date = (java.sql.Date) r[0];

                    Instant instant = date.toLocalDate()
                            .atStartOfDay(java.time.ZoneOffset.UTC)
                            .toInstant();

                    return new TimeBucketDto(
                            instant,
                            ((Number) r[1]).longValue()
                    );
                })
                .collect(Collectors.toList());
    }
}
