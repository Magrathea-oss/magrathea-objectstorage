package com.example.magrathea.bootstrap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * Spring Boot 4 entry point for Magrathea ObjectStore.
 * <p>
 * S3 API module (s3-reactive-api-adapter) is auto-configured via
 * META-INF/spring/AutoConfiguration.imports when it is on the classpath.
 * Disable via {@code s3.api.enabled=false} in application.properties.
 * <p>
 * Component scan covers:
 * <ul>
 *   <li>{@code com.example.magrathea.objectstore} — object-store domain and port interfaces</li>
 *   <li>{@code com.example.magrathea.objectstorage} — storage-engine ACL/repository adapters</li>
 *   <li>{@code com.example.magrathea.reactive} — reactive application services and in-memory infrastructure</li>
 *   <li>{@code com.example.magrathea.storageengine} — storage-engine domain, application, and infrastructure</li>
 *   <li>{@code com.example.magrathea.admin} — admin API router and handlers</li>
 * </ul>
 * Profile {@code storage-engine} is the default and only supported single-node backend.
 * Legacy in-memory adapters require the explicit {@code legacy-in-memory-test} profile.
 */
@SpringBootApplication(excludeName = {
    "org.springframework.boot.security.autoconfigure.ReactiveUserDetailsServiceAutoConfiguration",
    "org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration"
})
@ComponentScan(basePackages = {
    "com.example.magrathea.bootstrap",
    "com.example.magrathea.objectstore",
    "com.example.magrathea.objectstorage",       // storage-engine ACL adapter
    "com.example.magrathea.reactive",
    "com.example.magrathea.storageengine",        // storage-engine domain/application/infrastructure
    "com.example.magrathea.admin"
})
public class MagratheaApplication {

    public static void main(String[] args) {
        SpringApplication.run(MagratheaApplication.class, args);
    }
}
