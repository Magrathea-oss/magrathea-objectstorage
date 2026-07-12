package com.example.magrathea.s3api.cucumber.performance;

import com.example.magrathea.s3api.cucumber.ep4.Ep4CapacityTestSupport;
import com.example.magrathea.s3api.cucumber.requirements.RequirementsTestApp;
import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Path;

@SpringBootTest(classes = RequirementsTestApp.class, properties = {
    "admin.server.port=0", "storage.engine.filesystem.node-count=1",
    "s3.capacity.enabled=true"
})
@ActiveProfiles("storage-engine")
@CucumberContextConfiguration
public class PhaseEp6PerformanceCapacityCucumberConfig {
    static final Path ROOT = Path.of("target/ep6/performance-capacity").toAbsolutePath().normalize();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("storage.engine.filesystem.root", ROOT::toString);
        registry.add("storage.engine.policies.dir", () -> Ep4CapacityTestSupport.extractCatalog(
            "ep6-performance", "storage-policies", "minio-standard.yaml").toString());
        registry.add("storage.engine.devices.dir", () -> Ep4CapacityTestSupport.extractCatalog(
            "ep6-performance", "storage-devices", "local-disk-0.yaml").toString());
        registry.add("storage.engine.disksets.dir", () -> Ep4CapacityTestSupport.extractCatalog(
            "ep6-performance", "disk-sets", "default-diskset.yaml").toString());
    }
}
