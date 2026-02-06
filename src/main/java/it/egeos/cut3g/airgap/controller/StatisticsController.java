package it.egeos.cut3g.airgap.controller;

import it.egeos.cut3g.airgap.api.ApiResponse;
import it.egeos.cut3g.airgap.api.dto.PackageStateStatsDto;
import it.egeos.cut3g.airgap.api.dto.TransactionStatsDto;
import it.egeos.cut3g.airgap.service.files.ExcelExportService;
import it.egeos.cut3g.airgap.service.files.ExportService;
import it.egeos.cut3g.airgap.service.packaging.StatisticsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/airgap/statistics")
public class StatisticsController {

    @Autowired
    private StatisticsService statisticsService;

    @Autowired
    private ExportService exportService;

    @Autowired
    private ExcelExportService excelExportService;

    @GetMapping("/packages")
    public ApiResponse<List<PackageStateStatsDto>> packageStats() {
        return ApiResponse.ok(
                "OK",
                statisticsService.packageStateStats()
        );
    }

    @GetMapping("/transactions")
    public ApiResponse<List<TransactionStatsDto>> transactionStats() {
        return ApiResponse.ok(
                "OK",
                statisticsService.transactionStats()
        );
    }

    @GetMapping(
            value = "/packages/export",
            produces = "text/csv"
    )
    public ResponseEntity<byte[]> exportPackageStatsCsv() {

        byte[] csv =
                exportService.exportPackageStatsCsv(
                        statisticsService.packageStateStats()
                );

        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=package_stats.csv"
                )
                .body(csv);
    }

    @GetMapping(
            value = "/transactions/export",
            produces = "text/csv"
    )
    public ResponseEntity<byte[]> exportTransactionStatsCsv() {

        byte[] csv =
                exportService.exportTransactionStatsCsv(
                        statisticsService.transactionStats()
                );

        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=transaction_stats.csv"
                )
                .body(csv);
    }

    @GetMapping(
            value = "/packages/export/xls",
            produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    )
    public ResponseEntity<byte[]> exportPackageStatsXls() {

        byte[] xls =
                excelExportService.exportPackageStatsXls(
                        statisticsService.packageStateStats()
                );

        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=package_stats.xlsx"
                )
                .body(xls);
    }

    @GetMapping(
            value = "/transactions/export/xls",
            produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    )
    public ResponseEntity<byte[]> exportTransactionStatsXls() {

        byte[] xls =
                excelExportService.exportTransactionStatsXls(
                        statisticsService.transactionStats()
                );

        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=transaction_stats.xlsx"
                )
                .body(xls);
    }
}

