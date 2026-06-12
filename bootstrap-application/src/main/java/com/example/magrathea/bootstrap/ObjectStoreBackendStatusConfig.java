package com.example.magrathea.bootstrap;

import com.example.magrathea.bootstrap.ObjectStoreBackendStatus.Backend;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.util.Arrays;

/**
 * Exposes and logs the selected object-store backend.
 */
@Configuration
public class ObjectStoreBackendStatusConfig {

    private static final Logger log = LoggerFactory.getLogger(ObjectStoreBackendStatusConfig.class);

    @Bean
    public ObjectStoreBackendStatus objectStoreBackendStatus(
            Environment environment,
            @Value("${magrathea.object-store.backend:}") String configuredBackend) {
        Backend propertyBackend = Backend.fromProperty(configuredBackend);
        boolean storageEngineProfileActive = Arrays.asList(environment.getActiveProfiles()).contains("storage-engine");
        Backend selectedBackend = storageEngineProfileActive ? Backend.STORAGE_ENGINE : propertyBackend;

        boolean explicitProperty = configuredBackend != null && !configuredBackend.isBlank();
        if (explicitProperty && storageEngineProfileActive && propertyBackend != Backend.STORAGE_ENGINE) {
            throw new IllegalStateException(
                "magrathea.object-store.backend=in-memory conflicts with active storage-engine profile");
        }
        if (explicitProperty && !storageEngineProfileActive && propertyBackend == Backend.STORAGE_ENGINE) {
            throw new IllegalStateException(
                "magrathea.object-store.backend=storage-engine requires the storage-engine Spring profile");
        }
        return new ObjectStoreBackendStatus(selectedBackend);
    }

    @Bean
    public ApplicationListener<ApplicationReadyEvent> objectStoreBackendLogger(ObjectStoreBackendStatus status) {
        return event -> log.info("Selected object-store backend: {}", status.backend().propertyValue());
    }
}
