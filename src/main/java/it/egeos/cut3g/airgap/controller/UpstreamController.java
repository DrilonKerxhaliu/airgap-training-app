package it.egeos.cut3g.airgap.controller;

import it.egeos.cut3g.airgap.api.ApiResponse;
import it.egeos.cut3g.airgap.api.dto.*;
import it.egeos.cut3g.airgap.persistence.entity.PackageEntity;
import it.egeos.cut3g.airgap.persistence.entity.TransactionEntity;
import it.egeos.cut3g.airgap.persistence.entity.UploadPackageEntity;
import it.egeos.cut3g.airgap.persistence.enums.Direction;
import it.egeos.cut3g.airgap.persistence.enums.PackageState;
import it.egeos.cut3g.airgap.persistence.enums.UploadPackageStatus;
import it.egeos.cut3g.airgap.persistence.repo.PackageRepository;
import it.egeos.cut3g.airgap.persistence.repo.UploadPackageRepository;
import it.egeos.cut3g.airgap.service.downstream.DownstreamService;
import it.egeos.cut3g.airgap.service.files.ArchiveCleanupService;
import it.egeos.cut3g.airgap.service.importing.IncomingPackageImportService;
import it.egeos.cut3g.airgap.service.upstream.UploadService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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
    private ArchiveCleanupService archiveCleanupService;

    @Autowired
    private UploadPackageRepository uploadPackageRepository;

    @PostMapping("/package/{packageName}/upload")
    public ResponseEntity<?> uploadPackage(@PathVariable String packageName,
                                           @RequestParam(value = "username", required = false) String username,
                                           @RequestParam(value = "contingency", defaultValue = "false") boolean contingency ) {

        UploadPackageEntity pkg = uploadService.uploadPackage(packageName, username, contingency);

        return ResponseEntity.ok(
                java.util.Map.of(
                        "uploadPackageId", pkg.getId(),
                        "packageName", pkg.getPackageName(),
                        "status", pkg.getStatus().name(),
                        "fileCount", pkg.getFileCount() != null ? pkg.getFileCount() : 0,
                        "totalSizeBytes", pkg.getTotalSizeBytes() != null ? pkg.getTotalSizeBytes() : 0
                )
        );
    }

    @PostMapping(value = "/package/dragdrop", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadPackageFile(
            @RequestPart("file") MultipartFile zipFile,
            @RequestParam(value = "username", required = false) String username,
            @RequestParam(value = "contingency", defaultValue = "false") boolean contingency ) throws IOException {

        UploadPackageEntity pkg = uploadService.dragAndDrop(zipFile, username, contingency);

        return ResponseEntity.ok(
                java.util.Map.of(
                        "uploadPackageId", pkg.getId(),
                        "packageName", pkg.getPackageName(),
                        "sequenceIndex", pkg.getSequenceIndex(),
                        "status", pkg.getStatus().name(),
                        "fileCount", pkg.getFileCount() != null ? pkg.getFileCount() :0,
                        "totalSizeBytes", pkg.getTotalSizeBytes() != null ? pkg.getTotalSizeBytes() :0 )
        );
    }

    @GetMapping("/package/list")
    public ApiResponse<List<UploadPackageDto>> listPackagesReady() {
        return ApiResponse.ok("OK", uploadService.listOfUploadPackages());
    }

    @GetMapping("/package/history/list")
    public ApiResponse<List<UploadPackageDto>> history() {
        List<UploadPackageDto> list = uploadPackageRepository.findAll().stream()
                .filter(p -> (p.getStatus() == UploadPackageStatus.IMPORTED) || (p.getStatus() == UploadPackageStatus.ARCHIVED))
                .map(UploadPackageDto::from)
                .collect(Collectors.toList());
        return ApiResponse.ok("OK", list);
    }

    @GetMapping("/package/content/{packageId}")
    public ApiResponse<PackageContentResponse> content(@PathVariable String packageId) throws IOException {
        return ApiResponse.ok("OK", uploadService.tarContent(packageId));
    }

    @GetMapping("/statistics")
    public ApiResponse<UpstreamStatisticsResponse> statistics() {
        long total = uploadPackageRepository.count();
        long uploaded = uploadPackageRepository.findAll().stream()
                .filter(p -> (p.getStatus() == UploadPackageStatus.IMPORTED) || (p.getStatus() == UploadPackageStatus.ARCHIVED))
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

    @DeleteMapping("/delete/package/{packageId}")
    public ApiResponse<Void> deletePackageManually(
            @PathVariable String packageId,
            @RequestParam(value = "username", required = false) String username
    ) {

        archiveCleanupService.manualCleanupArchivedPkg(packageId, username);

        return ApiResponse.ok(
                "Package deleted manually",
                null
        );
    }

}

