package com.example.magrathea.s3api.cucumber.ep4awscli;

import com.example.magrathea.s3api.cucumber.ep4.Ep4CapacityTestSupport;
import com.example.magrathea.s3api.cucumber.requirements.RequirementsTestApp;
import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Path;

@SpringBootTest(classes = {RequirementsTestApp.class, Ep4CapacityTestSupport.class},
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"admin.server.port=0", "storage.engine.filesystem.node-count=1"})
@ActiveProfiles("storage-engine")
@CucumberContextConfiguration
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class PhaseEp4CapacityAwsCliCucumberConfig {
    static final Path ROOT = Ep4CapacityTestSupport.newStorageRoot("awscli");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("storage.engine.filesystem.root", ROOT::toString);
        registry.add("storage.engine.policies.dir", () -> Ep4CapacityTestSupport.extractCatalog(
            "awscli", "storage-policies", "plain-streaming.yaml").toString());
        registry.add("storage.engine.devices.dir", () -> Ep4CapacityTestSupport.extractCatalog(
            "awscli", "storage-devices", "local-disk-0.yaml").toString());
        registry.add("storage.engine.disksets.dir", () -> Ep4CapacityTestSupport.extractCatalog(
            "awscli", "disk-sets", "default-diskset.yaml").toString());
    }
}
