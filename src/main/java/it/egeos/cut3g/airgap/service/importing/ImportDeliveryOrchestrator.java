package it.egeos.cut3g.airgap.service.importing;

import it.egeos.cut3g.airgap.service.downstream.OutboxDeliveryService;
import it.egeos.cut3g.airgap.service.tar.TarExtractService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.Map;

@Service
public class ImportDeliveryOrchestrator {

    @Autowired
    private TarExtractService tarExtractService;

    @Autowired
    private OutboxDeliveryService outboxDeliveryService;

    public void unpackAndDeliver(Path packageTar, Path workDir) throws Exception {

        // 1. Unpack package tar
        Path outerDir = workDir.resolve("outer");
        Map<String, Path> outer = tarExtractService.extractTar(packageTar, outerDir);

        Path dataTar = outer.get("data.tar");
        if (dataTar == null) {
            throw new IllegalStateException("data.tar missing in package");
        }

        // 2. Unpack data.tar
        Path dataDir = workDir.resolve("data");
        Map<String, Path> files = tarExtractService.extractTar(dataTar, dataDir);

        // 3. Deliver to OUT
        outboxDeliveryService.deliver(files);
    }
}
