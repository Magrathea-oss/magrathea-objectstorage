package com.example.magrathea.bootstrap;

import com.example.magrathea.storageengine.infrastructure.yaml.config.StorageEngineYamlCatalogConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class StorageEngineMissingExternalConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withInitializer(context -> context.getEnvironment().setActiveProfiles("storage-engine"))
        .withUserConfiguration(StorageEngineYamlCatalogConfig.class);

    @Test
    void storageEngineProfileFailsFastWhenExternalCatalogDirectoryIsMissing() {
        contextRunner
            .withPropertyValues(
                "storage.engine.policies.dir=/definitely/missing/policies",
                "storage.engine.devices.dir=/definitely/missing/devices",
                "storage.engine.disksets.dir=/definitely/missing/disksets")
            .run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure())
                    .hasRootCauseInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Policy directory does not exist or is not a directory");
            });
    }
}
