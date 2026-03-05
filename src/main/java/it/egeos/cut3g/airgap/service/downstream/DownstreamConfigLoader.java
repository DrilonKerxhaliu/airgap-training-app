package it.egeos.cut3g.airgap.service.downstream;

import com.google.gson.Gson;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.FileReader;

@Component
public class DownstreamConfigLoader {

    @Autowired
    private Gson gson;

    private DownstreamConfig config;

    @Value("${airgap.downstream.config-path}")
    private String configPath;

   @PostConstruct
public void load() {

       try (FileReader reader = new FileReader(configPath)) {

           config = gson.fromJson(reader, DownstreamConfig.class);

       } catch (Exception e) {

           config = new DownstreamConfig();
           config.setDestination("file:///tmp/airgap-out");

           System.out.println("Using default destination because config failed");
       }
   }

    public String getDestination() {
        return config.getDestination();
    }
}
