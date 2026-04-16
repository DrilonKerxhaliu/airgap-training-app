package it.egeos.cut3g.airgap.service.files;

import it.egeos.cut3g.airgap.api.dto.PackageStateStatsDto;
import it.egeos.cut3g.airgap.api.dto.StatsPackageDto;
import it.egeos.cut3g.airgap.api.dto.TransactionStatsDto;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
public class ExportService {

        public byte[] exportPackageStatsCsv(
                List<StatsPackageDto> stats
        ) {
            StringBuilder sb = new StringBuilder();
            sb.append("package_state,count\n");

            for (StatsPackageDto s : stats) {
                sb.append(s.getState())
                        .append(',')
                        .append(s.getCount())
                        .append('\n');
            }

            return sb.toString().getBytes(StandardCharsets.UTF_8);
        }

        public byte[] exportTransactionStatsCsv(
                List<TransactionStatsDto> stats
        ) {
            StringBuilder sb = new StringBuilder();
            sb.append("transaction_state,package_state,count\n");

            for (TransactionStatsDto s : stats) {
                sb.append(s.getTransactionState())
                        .append(',')
                        .append(s.getPackageState())
                        .append(',')
                        .append(s.getCount())
                        .append('\n');
            }

            return sb.toString().getBytes(StandardCharsets.UTF_8);
        }
    }
