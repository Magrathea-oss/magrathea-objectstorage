package com.example.magrathea.s3api.cucumber.ep10;

import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.test.context.ContextConfiguration;

/** Empty Spring test context; cluster lifecycle is owned explicitly by the Cucumber world. */
@CucumberContextConfiguration
@ContextConfiguration(classes = PhaseEp10ClusterCoordinationConfig.EmptyConfiguration.class)
public class PhaseEp10ClusterCoordinationConfig {
    static class EmptyConfiguration { }
}
