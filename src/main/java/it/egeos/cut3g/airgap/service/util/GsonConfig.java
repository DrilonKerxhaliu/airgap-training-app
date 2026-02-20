package it.egeos.cut3g.airgap.service.util;

import com.google.gson.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.lang.reflect.Type;
import java.time.Instant;

@Configuration
public class GsonConfig {

    @Bean
    public Gson gson() {
        return new GsonBuilder()
                .registerTypeAdapter(Instant.class, new InstantTypeAdapter())
                .create();
    }

    static class InstantTypeAdapter
            implements JsonSerializer<Instant>, JsonDeserializer<Instant> {

        @Override
        public JsonElement serialize(
                Instant src,
                Type typeOfSrc,
                JsonSerializationContext context
        ) {
            // ISO-8601 string (recommended)
            return new JsonPrimitive(src.toString());
        }

        @Override
        public Instant deserialize(
                JsonElement json,
                Type typeOfT,
                JsonDeserializationContext context
        ) throws JsonParseException {
            return Instant.parse(json.getAsString());
        }
    }
}
