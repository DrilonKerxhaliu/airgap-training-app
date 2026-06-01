package it.egeos.cut3g.airgap.service.util;

import it.egeos.cut3g.airgap.persistence.enums.PackageState;
import it.egeos.cut3g.airgap.persistence.repo.PackageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Component
public class PackageConsistencyCheck {

    private static final Logger log =
            LoggerFactory.getLogger(PackageConsistencyCheck.class);

    private final PackageRepository packageRepository;

    public PackageConsistencyCheck(
            PackageRepository packageRepository) {

        this.packageRepository = packageRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void reconcileMissingPackages() {

        log.info("Checking package consistency...");

        packageRepository.findAll()
                .forEach(pkg -> {

                    // ignore already deleted
                    if (pkg.getState() == PackageState.DELETED) {
                        return;
                    }

                    if (pkg.getPackagePath() == null) {

                        pkg.setState(PackageState.DELETED);

                        packageRepository.save(pkg);

                        return;
                    }

                    Path path =
                            Paths.get(pkg.getPackagePath())
                                    .toAbsolutePath()
                                    .normalize();

                    if (!Files.exists(path)) {

                        log.warn(
                                "Package missing: {} path={}",
                                pkg.getPackageName(),
                                path
                        );

                        pkg.setState(PackageState.DELETED);

                        packageRepository.save(pkg);
                    }
                });

        log.info("Package consistency check completed");
    }
}