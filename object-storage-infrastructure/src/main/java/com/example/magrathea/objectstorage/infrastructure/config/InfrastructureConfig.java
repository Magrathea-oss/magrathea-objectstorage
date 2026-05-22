package com.example.magrathea.objectstorage.infrastructure.config;

import com.example.magrathea.objectstorage.infrastructure.adapter.persistence.InMemoryMultipartUploadRepository;
import com.example.magrathea.objectstorage.infrastructure.adapter.persistence.InMemoryObjectRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Boot configuration for ObjectStorage infrastructure beans.
 */
@Configuration
public class InfrastructureConfig {

    @Bean
    public InMemoryObjectRepository inMemoryObjectRepository() {
        return new InMemoryObjectRepository();
    }

    @Bean
    public InMemoryMultipartUploadRepository inMemoryMultipartUploadRepository() {
        return new InMemoryMultipartUploadRepository();
    }
}
