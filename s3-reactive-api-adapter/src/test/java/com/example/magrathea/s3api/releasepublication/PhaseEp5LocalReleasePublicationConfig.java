package com.example.magrathea.s3api.releasepublication;

import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;

@CucumberContextConfiguration
@ContextConfiguration(classes = PhaseEp5LocalReleasePublicationConfig.EmptyConfig.class)
public class PhaseEp5LocalReleasePublicationConfig {
    @Configuration
    static class EmptyConfig {
    }
}
