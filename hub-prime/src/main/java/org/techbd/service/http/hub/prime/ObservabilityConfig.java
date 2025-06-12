package org.techbd.service.http.hub.prime;

import io.micrometer.observation.ObservationRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ObservabilityConfig {
    @Bean
    public ObservationRegistry observationRegistry() {
        return ObservationRegistry.create();
    }
}