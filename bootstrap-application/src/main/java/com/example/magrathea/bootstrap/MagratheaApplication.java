package com.example.magrathea.bootstrap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * Spring Boot 4 entry point for Magrathea ObjectStorage.
 * S3 API module (s3-api) is auto-configured via META-INF/spring/AutoConfiguration.imports
 * when it's on the classpath. No explicit @ComponentScan needed for s3-api.
 * Disable via s3.api.enabled=false in application.properties.
 */
@SpringBootApplication
@ComponentScan(basePackages = {
    "com.example.magrathea.objectstorage"
})
public class MagratheaApplication {

    public static void main(String[] args) {
        SpringApplication.run(MagratheaApplication.class, args);
    }
}
