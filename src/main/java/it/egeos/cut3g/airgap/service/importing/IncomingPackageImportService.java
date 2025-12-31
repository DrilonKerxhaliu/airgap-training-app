package it.egeos.cut3g.airgap.service.importing;

import com.google.gson.Gson;
import it.egeos.cut3g.airgap.api.Direction;
import it.egeos.cut3g.airgap.persistence.entity.ImportTransactionEntity;
import it.egeos.cut3g.airgap.persistence.entity.PackageEntity;
import it.egeos.cut3g.airgap.persistence.enums.PackageState;
import it.egeos.cut3g.airgap.persistence.repo.ImportTransactionRepository;
import it.egeos.cut3g.airgap.persistence.repo.PackageRepository;
import it.egeos.cut3g.airgap.service.crypto.CryptoService;
import it.egeos.cut3g.airgap.service.manifest.PackageManifest;
import it.egeos.cut3g.airgap.service.util.HashUtils;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.*;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class IncomingPackageImportService {

    private static final Logger log = LoggerFactory.getLogger(IncomingPackageImportService.class);

    private static final Pattern PKG_PATTERN = Pattern.compile("^PKG_.*_(\\d{6})\\.tar$");

    @Value("${airgap.incoming.packages.dir}")
    private String incomingPackagesDir;

    @Value("${airgap.incoming.packages.archive.dir}")
    private String archiveDir;

    @Value("${airgap.unpack.work.dir}")
    private String unpackWorkDir;

    @Autowired
    private CryptoService cryptoService;

    @Autowired
    private ImportDeliveryOrchestrator importDeliveryOrchestrator;

    @Autowired
    private PackageRepository packageRepository;

    @Autowired
    private ImportTransactionRepository importTransactionRepository;

    private final Gson gson = new Gson();

    /**
     * Imports a single incoming package.tar.
     * FULL FLOW:
     * - read outer tar
     * - decrypt manifest
     * - verify md5(data.tar)
     * - unpack + deliver to downstream.out
     * - audit + archive
     */
    @Transactional
    public boolean importIncomingPackage(Direction direction, Path pkgTarPath) {

        String pkgName = pkgTarPath.getFileName().toString();
        long progressive = parseProgressive(pkgName);

        // Sequence check (optional, but recommended)
        Optional<ImportTransactionEntity> lastTx =
                importTransactionRepository.findTopByDirectionOrderByUploadSequenceNumberDesc(direction);

        if (lastTx.isPresent()) {
            long expected = lastTx.get().getUploadSequenceNumber() + 1;
            if (progressive > 0 && progressive != expected) {
                log.warn("REJECTED {} due to sequence hole. expected={}, got={}", pkgName, expected, progressive);
                markPackageState(direction, pkgName, pkgTarPath, PackageState.REJECTED, "Sequence hole");
                return false;
            }
        }

        try {
            // Read package tar (manifest.enc + data.tar)
            OuterTarContent outer = readOuterTar(pkgTarPath);

            // Decrypt manifest
            byte[] manifestJson = cryptoService.decrypt(outer.manifestEncBytes);
            PackageManifest manifest =
                    gson.fromJson(new String(manifestJson, java.nio.charset.StandardCharsets.UTF_8),
                            PackageManifest.class);

            if (manifest == null || manifest.getMd5DataTar() == null) {
                markPackageState(direction, pkgName, pkgTarPath, PackageState.REJECTED, "manifest is empty");
                return false;
            }

            // Verify MD5(data.tar)
            String md5 = HashUtils.md5Hex(outer.dataTarBytes); // this should be taken from DB .. TODO
            if (!manifest.getMd5DataTar().equalsIgnoreCase(md5)) {
                markPackageState(direction, pkgName, pkgTarPath, PackageState.REJECTED, "MD5 mismatch");
                return false;
            }

            // VERIFIED — now UNPACK & DELIVER
            Path workDir = Paths.get(unpackWorkDir).resolve(pkgName);
            importDeliveryOrchestrator.unpackAndDeliver(pkgTarPath, workDir);

            // Audit ACCEPTED
            // .. TODO

            markPackageState(direction, pkgName, pkgTarPath, PackageState.IMPORTED, "Imported OK");
            cleanupAndArchiveAfterSuccess(pkgTarPath, workDir);
            log.info("IMPORTED package {} successfully", pkgName);
            return true;

        } catch (Exception ex) {
            markPackageState(direction, pkgName, pkgTarPath, PackageState.FAILED, ex.getMessage());
            log.error("FAILED importing {}", pkgName, ex);
            return false;
        }
    }

    // =========================================================
    // Helper methods
    // =========================================================

    private long parseProgressive(String pkgName) {
        Matcher m = PKG_PATTERN.matcher(pkgName);
        if (m.matches()) {
            try {
                return Long.parseLong(m.group(1));
            } catch (Exception ignored) {
            }
        }
        return 0L;
    }

    private void markPackageState(Direction direction, String pkgName, Path pkgPath,
                                  PackageState state, String notes) {

        PackageEntity p = new PackageEntity();
        Instant now = Instant.now();

        p.setDirection(direction);
        p.setProgressiveNumber(parseProgressive(pkgName) > 0
                ? parseProgressive(pkgName)
                : now.toEpochMilli());
        p.setTransactionStartTime(now);
        p.setTransactionStopTime(now);
        p.setPackageName(pkgName);
        p.setPackagePath(pkgPath.toString());
       // p.setMd5DataTar("N/A");
        p.setTotalSizeBytes(safeSize(pkgPath));
        p.setState(state);
        p.setNotes(notes);
        try {
            packageRepository.save(p);
        } catch (Exception ignored) {
            // TODO
        }
    }

    private long safeSize(Path p) {
        try {
            return Files.size(p);
        } catch (Exception e) {
            return 0L;
        }
    }

    private OuterTarContent readOuterTar(Path outerTar) throws IOException {
        byte[] manifestEnc = null;
        byte[] dataTar = null;

        try (InputStream fis = Files.newInputStream(outerTar);
             TarArchiveInputStream tarIn = new TarArchiveInputStream(fis)) {

            TarArchiveEntry entry;
            while ((entry = tarIn.getNextTarEntry()) != null) {
                if (entry.isDirectory()) continue;

                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                IOUtils.copy(tarIn, bos);
                byte[] bytes = bos.toByteArray();

                if ("manifest.enc".equals(entry.getName())) {
                    manifestEnc = bytes;
                } else if (entry.getName().endsWith("-data.tar")) {
                    dataTar = bytes;
                }
            }
        }

        if (manifestEnc == null || dataTar == null) {
            throw new IllegalArgumentException("Outer tar missing manifest.enc or *-data.tar");
        }
        return new OuterTarContent(manifestEnc, dataTar);
    }

    private static class OuterTarContent {
        final byte[] manifestEncBytes;
        final byte[] dataTarBytes;

        OuterTarContent(byte[] manifestEncBytes, byte[] dataTarBytes) {
            this.manifestEncBytes = manifestEncBytes;
            this.dataTarBytes = dataTarBytes;
        }
    }

    private void cleanupAndArchiveAfterSuccess(Path pkgTarPath, Path workDir) {

        // remove files and .tar from work dir after success
        try {
            if (Files.exists(workDir)) {
                Files.walk(workDir)
                        .sorted(Comparator.reverseOrder())
                        .forEach(p -> {
                            try {
                                Files.deleteIfExists(p);
                            } catch (Exception e) {
                                log.warn("Unable to delete work path {}", p, e);
                            }
                        });
            }
        } catch (Exception e) {
            log.warn("WORK cleanup failed for {}", workDir, e);
        }

        // move package.tar into archive after success
        try {
            Path archiveDirPath = Paths.get(archiveDir);
            Files.createDirectories(archiveDirPath);

            Path target = archiveDirPath.resolve(pkgTarPath.getFileName());
            if (Files.exists(target)) {
                target = archiveDirPath.resolve(
                        pkgTarPath.getFileName().toString()
                                .replace(".tar", "_" + System.currentTimeMillis() + ".tar"));
            }

            Files.move(pkgTarPath, target, StandardCopyOption.ATOMIC_MOVE);

        } catch (Exception e) {
            log.warn("Unable to archive {}", pkgTarPath, e);
        }
    }

}
