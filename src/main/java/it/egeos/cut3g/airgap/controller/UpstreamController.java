package it.egeos.cut3g.airgap.controller;

import it.egeos.cut3g.airgap.api.ApiResponse;
import it.egeos.cut3g.airgap.api.dto.*;
import it.egeos.cut3g.airgap.exceptions.PackageNotFoundException;
import it.egeos.cut3g.airgap.persistence.entity.PackageEntity;
import it.egeos.cut3g.airgap.persistence.entity.TransactionEntity;
import it.egeos.cut3g.airgap.persistence.enums.Direction;
import it.egeos.cut3g.airgap.persistence.enums.PackageState;
import it.egeos.cut3g.airgap.persistence.repo.PackageRepository;
import it.egeos.cut3g.airgap.service.downstream.DownstreamService;
import it.egeos.cut3g.airgap.service.importing.IncomingPackageImportService;
import it.egeos.cut3g.airgap.service.upstream.UploadService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping(value = "/airgap/upstream", produces = MediaType.APPLICATION_JSON_VALUE)
public class UpstreamController {

    @Autowired
    private UploadService uploadService;

    @Autowired
    private PackageRepository packageRepository;

    @Autowired
    private DownstreamService downstreamService;

    @Autowired
    private IncomingPackageImportService incomingPackageImportService;

    @PutMapping("/package/{packageId}")
    public ApiResponse<PackageDto> uploadPackage(@PathVariable String packageId,
                                                 @RequestParam(value = "username", required = false) String username) {
        PackageEntity pkg = uploadService.uploadPackage(packageId, username);
        return ApiResponse.ok("OK", PackageDto.from(pkg));
    }

    @GetMapping("/package/history/list")
    public ApiResponse<List<PackageDto>> history() {
        List<PackageDto> list = packageRepository.findAll().stream()
                .filter(p -> p.getState() == PackageState.UPLOADED)
                .map(PackageDto::from)
                .collect(Collectors.toList());
        return ApiResponse.ok("OK", list);
    }

    @GetMapping("/package/content/{packageId}")
    public ApiResponse<PackageContentResponse> content(@PathVariable String packageId) throws IOException {
        return ApiResponse.ok("OK", downstreamService.tarContent(packageId));
    }

    @GetMapping("/statistics")
    public ApiResponse<UpstreamStatisticsResponse> statistics() {
        long total = packageRepository.count();
        long uploaded = packageRepository.findAll().stream()
                .filter(p -> p.getState() == PackageState.UPLOADED)
                .count();

        UpstreamStatisticsResponse stats = new UpstreamStatisticsResponse();
        stats.setTotalPackages(total);
        stats.setUploadedPackages(uploaded);

        return ApiResponse.ok("OK",stats);
    }

    @GetMapping("/status")
    public ApiResponse<UpstreamStatusResponse> status() {
        return ApiResponse.ok("OK", uploadService.status());
    }

    @PostMapping("/import/package/{packageId}")
    public ApiResponse<ImportTransactionDto> importPackage(
            @PathVariable String packageId,
            @RequestParam(value = "username", required = false) String username) {

        TransactionEntity tx =
                incomingPackageImportService.importIncomingPackage(packageId, username);

        return ApiResponse.ok(
                tx.getState().name(),
                ImportTransactionDto.from(tx, Direction.IMPORT)
        );

    }
}

