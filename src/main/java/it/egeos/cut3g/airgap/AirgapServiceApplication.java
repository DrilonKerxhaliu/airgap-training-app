package it.egeos.cut3g.airgap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AirgapServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AirgapServiceApplication.class, args);
    }
}
