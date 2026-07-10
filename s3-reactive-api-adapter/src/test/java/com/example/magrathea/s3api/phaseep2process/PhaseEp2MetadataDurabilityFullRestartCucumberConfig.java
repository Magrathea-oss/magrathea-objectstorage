package com.example.magrathea.s3api.phaseep2process;

import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;

/**
 * Minimal Cucumber-Spring context. The scenario steps own the actual S3
 * application contexts so they can prove a full stop/start restart.
 */
@CucumberContextConfiguration
@ContextConfiguration(classes = PhaseEp2MetadataDurabilityFullRestartCucumberConfig.EmptyConfig.class)
public class PhaseEp2MetadataDurabilityFullRestartCucumberConfig {

    @Configuration
    static class EmptyConfig {
    }
}
