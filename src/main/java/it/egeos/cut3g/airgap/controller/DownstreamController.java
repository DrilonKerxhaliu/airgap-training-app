package it.egeos.cut3g.airgap.controller;

import it.egeos.cut3g.airgap.api.ApiResponse;
import it.egeos.cut3g.airgap.api.dto.*;
import it.egeos.cut3g.airgap.persistence.entity.PackageEntity;
import it.egeos.cut3g.airgap.service.downstream.DownstreamService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(value = "/airgap/downstream", produces = MediaType.APPLICATION_JSON_VALUE)
public class DownstreamController {

    private final DownstreamService downstreamService;

    public DownstreamController(DownstreamService downstreamService) {
        this.downstreamService = downstreamService;
    }

    @GetMapping("/package/list")
    public ApiResponse<List<PackageDto>> listPackagesReady() {
        return ApiResponse.ok("OK", downstreamService.listReadyForDownload());
    }

    @GetMapping("/package/content/{packageId}")
    public ApiResponse<PackageContentResponse> packageTarContent(@PathVariable String packageId) {
        return ApiResponse.ok("OK", downstreamService.tarContent(packageId));
    }

    @GetMapping("/package/content/latest")
    public ApiResponse<LatestContentResponse> latestCollectionSnapshot() {
        return ApiResponse.ok("OK", downstreamService.getLatestFolderContent());
    }

    @GetMapping(value = "/package/{packageId}/download", produces = MediaType.ALL_VALUE)
    public ResponseEntity<?> downloadPackage(@PathVariable ("packageId") String packageId,
                                              @RequestParam(value = "username", required = false) String username) {
        return downstreamService.downloadExistingPackage(packageId, "MANUAL");
    }

    @GetMapping(value = "/package/latest")
    public ApiResponse<PackageDto> createLatest(@RequestParam(value = "username", required = false) String username) {
        PackageEntity pkg = downstreamService.generateAndDeliverLatest("MANUAL");
        return ApiResponse.ok("OK", PackageDto.from(pkg));
    }

    @PostMapping("/packages/send")
    public ResponseEntity<Void> sendPackages(@RequestBody SendPackagesRequest request,
                                             @RequestParam(value = "username", required = false) String username) {
        downstreamService.sendPackages(request.getPackageIds(), "MANUAL");
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/package/history/list")
    public ApiResponse<List<PackageDto>> historyList() {
        return ApiResponse.ok("OK", downstreamService.historyList());
    }

    @GetMapping("/statistics")
    public ApiResponse<DownstreamStatisticsResponse> statistics() {
        return ApiResponse.ok("OK", downstreamService.statistics());
    }

    @GetMapping("/status")
    public ApiResponse<DownstreamStatusResponse> status() {
        return ApiResponse.ok("OK", downstreamService.status());
    }
}
