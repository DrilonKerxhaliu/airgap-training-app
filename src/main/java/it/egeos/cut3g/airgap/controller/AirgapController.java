package it.egeos.cut3g.airgap.controller;

import it.egeos.cut3g.airgap.api.ApiResponse;
import it.egeos.cut3g.airgap.api.dto.ImportTransactionDto;
import it.egeos.cut3g.airgap.persistence.entity.TransactionEntity;
import it.egeos.cut3g.airgap.persistence.enums.Direction;
import it.egeos.cut3g.airgap.service.importing.IncomingPackageImportService;
import it.egeos.cut3g.airgap.service.packageing.PackagingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.Optional;

/**
 * JSON-only REST API.
 */
@RestController
@RequestMapping(value = "/airgap", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
public class AirgapController {

    private static final Logger log = LoggerFactory.getLogger(AirgapController.class);

    @Autowired
    private PackagingService packagingService;

    @Autowired
    private IncomingPackageImportService incomingPackageImportService;

    /**
     * Manual LATEST trigger
     * GET /airgap/packages/latest
     */

    /*
    @Deprecated
    @PostMapping("/packages/latest")
    public ResponseEntity<?> createLatestPackage() {

        log.info("Manual LATEST trigger requested");

        Optional<PackageEntity> pkgOpt = packagingService.createLatestOrAutoPackage();

        if (pkgOpt.isEmpty()) {
            return ResponseEntity.ok(new ApiResponse<>(
                    false,
                    "No NEW files to package",
                    null
            ));
        }

        PackageEntity pkg = pkgOpt.get();

        return ResponseEntity.ok(new ApiResponse(
                true,
                "Package created successfully",
                PackageDto.from(pkg)
        ));
    }

     */

    @PostMapping("/import/package/{packageId}")
public ApiResponse<ImportTransactionDto> importPackage(
        @PathVariable String packageId) {

    TransactionEntity tx =
            incomingPackageImportService.importIncomingPackage(packageId);

    return ApiResponse.ok(
            tx.getState().name(),
            ImportTransactionDto.from(tx, Direction.IMPORT)
    );
}


}
