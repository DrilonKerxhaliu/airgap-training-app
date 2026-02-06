package it.egeos.cut3g.airgap.service.packaging;

import it.egeos.cut3g.airgap.api.dto.PackageStateStatsDto;
import it.egeos.cut3g.airgap.api.dto.TransactionStatsDto;
import it.egeos.cut3g.airgap.persistence.repo.PackageRepository;
import it.egeos.cut3g.airgap.persistence.repo.TransactionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class StatisticsService {

    @Autowired
    private PackageRepository packageRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    public List<PackageStateStatsDto> packageStateStats() {
        return packageRepository.countPackagesByState();
    }

    public List<TransactionStatsDto> transactionStats() {
        return transactionRepository.countTransactionsByState();
    }
}
