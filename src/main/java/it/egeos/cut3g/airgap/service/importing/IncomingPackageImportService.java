package it.egeos.cut3g.airgap.service.importing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.*;
import org.springframework.stereotype.Service;
import java.util.regex.Pattern;

/**
 * OFFLINE (or ONLINE) incoming package import pipeline (folder-watcher):
 *
 * NOTE:
 * - Communication with UI remains JSON-only via controllers.
 * - This pipeline is filesystem-driven, as required by air-gap.
 */
@Service
public class IncomingPackageImportService {

    private static final Logger log = LoggerFactory.getLogger(IncomingPackageImportService.class);

    private static final Pattern PKG_PATTERN = Pattern.compile("^PKG_.*_(\\d{6})\\.tar$");

    @Value("${airgap.collect.out}")
    private String collectedOut;

    @Value("${airgap.incoming.packages.archive.dir}")
    private String archiveDir;

    //TODO
}
