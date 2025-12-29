package it.egeos.cut3g.airgap.service.downstream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.*;
import java.util.Map;

@Service
public class OutboxDeliveryService {

    @Value("${airgap.collect.out}")
    private String outDir;

    public void deliver(Map<String, Path> files) throws Exception {
        Path outRoot = Paths.get(outDir);
        Files.createDirectories(outRoot);

        for (Map.Entry<String, Path> e : files.entrySet()) {
            Path dest = outRoot.resolve(e.getKey()).normalize();
            if (!dest.startsWith(outRoot)) {
                throw new Exception("Invalid OUT path: " + e.getKey());
            }
            Files.createDirectories(dest.getParent());
            Files.move(e.getValue(), dest, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
