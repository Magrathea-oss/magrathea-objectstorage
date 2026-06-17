package com.example.magrathea.s3api.phase5awscli;

import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Cucumber context configuration for Phase 5 S3 semantic compatibility AWS CLI runner.
 *
 * <p>Starts a real HTTP server using {@link Phase5S3SemanticCompatibilityAwsCliTestApp}
 * on a random port so that {@code aws s3api} commands issued in step definitions can
 * target a live S3-compatible endpoint.
 *
 * <p>The storage-engine profile is activated so all S3 operations flow through the
 * filesystem-backed storage engine pipeline. Catalog directories (policies, devices,
 * disk sets) are extracted from classpath resources to a temporary directory via
 * {@link DynamicPropertySource}.
 */
@SpringBootTest(
    classes = Phase5S3SemanticCompatibilityAwsCliTestApp.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "admin.server.port=0",
        "storage.engine.filesystem.root=target/storage-engine-it/phase5-awscli-current",
        "storage.engine.filesystem.node-count=1"
    }
)
@ActiveProfiles("storage-engine")
@CucumberContextConfiguration
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class Phase5S3SemanticCompatibilityAwsCliCucumberConfig {

    @DynamicPropertySource
    static void catalogProperties(DynamicPropertyRegistry registry) {
        try {
            Path catalogRoot = Files.createTempDirectory("magrathea-phase5-awscli-catalog-");
            registry.add("storage.engine.policies.dir",
                () -> extractCatalogDir(catalogRoot, "storage-policies",
                    List.of("minio-standard.yaml")).toString());
            registry.add("storage.engine.devices.dir",
                () -> extractCatalogDir(catalogRoot, "storage-devices",
                    List.of("local-disk-0.yaml")).toString());
            registry.add("storage.engine.disksets.dir",
                () -> extractCatalogDir(catalogRoot, "disk-sets",
                    List.of("default-diskset.yaml")).toString());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Path extractCatalogDir(Path catalogRoot, String classpathDir, List<String> fileNames) {
        try {
            Path dir = catalogRoot.resolve(classpathDir);
            Files.createDirectories(dir);
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            for (String fileName : fileNames) {
                String resourcePath = classpathDir + "/" + fileName;
                try (InputStream in = cl.getResourceAsStream(resourcePath)) {
                    if (in == null) {
                        throw new IOException("Classpath resource not found: " + resourcePath);
                    }
                    Files.write(dir.resolve(fileName), in.readAllBytes());
                }
            }
            return dir;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
