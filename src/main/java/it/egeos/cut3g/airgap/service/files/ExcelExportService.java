package it.egeos.cut3g.airgap.service.files;

import it.egeos.cut3g.airgap.api.dto.PackageStateStatsDto;
import it.egeos.cut3g.airgap.api.dto.TransactionStatsDto;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

@Service
public class ExcelExportService {

    public byte[] exportPackageStatsXls(
            List<PackageStateStatsDto> stats
    ) {
        try (Workbook workbook = new XSSFWorkbook()) {

            Sheet sheet = workbook.createSheet("Package States");

            // Header
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Package State");
            header.createCell(1).setCellValue("Count");

            int rowIdx = 1;
            for (PackageStateStatsDto s : stats) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(s.getState().name());
                row.createCell(1).setCellValue(s.getCount());
            }

            autoSize(sheet, 2);

            return toBytes(workbook);
        } catch (Exception e) {
            throw new RuntimeException("Unable to generate XLS", e);
        }
    }

    public byte[] exportTransactionStatsXls(
            List<TransactionStatsDto> stats
    ) {
        try (Workbook workbook = new XSSFWorkbook()) {

            Sheet sheet = workbook.createSheet("Transactions");

            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Transaction State");
            header.createCell(1).setCellValue("Package State");
            header.createCell(2).setCellValue("Count");

            int rowIdx = 1;
            for (TransactionStatsDto s : stats) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(s.getTransactionState().name());
                row.createCell(1).setCellValue(
                        s.getPackageState() != null
                                ? s.getPackageState().name()
                                : "N/A"
                );
                row.createCell(2).setCellValue(s.getCount());
            }

            autoSize(sheet, 3);

            return toBytes(workbook);
        } catch (Exception e) {
            throw new RuntimeException("Unable to generate XLS", e);
        }
    }

    private byte[] toBytes(Workbook workbook) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        workbook.write(bos);
        return bos.toByteArray();
    }

    private void autoSize(Sheet sheet, int columns) {
        for (int i = 0; i < columns; i++) {
            sheet.autoSizeColumn(i);
        }
    }
}

