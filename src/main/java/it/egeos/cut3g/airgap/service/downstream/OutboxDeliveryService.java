package it.egeos.cut3g.airgap.service.downstream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.*;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class OutboxDeliveryService {

    @Value("${airgap.collect.out}")
    private String outDir;

    public Map<String, Path> deliver(Map<String, Path> files) throws Exception {
        Path outRoot = Paths.get(outDir).toAbsolutePath().normalize();
        Files.createDirectories(outRoot);

        Map<String, Path> delivered = new LinkedHashMap<>();

        for (Map.Entry<String, Path> e : files.entrySet()) {
            String relative = e.getKey().replace("\\", "/");
            Path source = e.getValue().toAbsolutePath().normalize();
            Path dest = outRoot.resolve(relative).normalize();

            if (!dest.startsWith(outRoot)) {
                throw new Exception("Invalid OUT path: " + relative);
            }

            Files.createDirectories(dest.getParent());
            Files.move(source, dest, StandardCopyOption.REPLACE_EXISTING);
            delivered.put(relative, dest);
        }

        return delivered;
    }
}