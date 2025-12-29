package it.egeos.cut3g.airgap.service.files;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Watches /COLLECTED_DATA/IN and registers stable files in DB.
 * "Stable" here means: exists, not directory, and size can be read.
 */
@Service
public class FileDiscoveryService {

    private static final Logger log = LoggerFactory.getLogger(FileDiscoveryService.class);

    @Value("${airgap.collect.in}")
    private String collectedIn;

    //TODO
}
