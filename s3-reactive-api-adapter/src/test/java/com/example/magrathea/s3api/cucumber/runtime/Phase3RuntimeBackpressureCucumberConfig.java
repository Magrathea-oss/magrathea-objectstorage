package com.example.magrathea.s3api.cucumber.runtime;

import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;

@CucumberContextConfiguration
@ContextConfiguration(classes = Phase3RuntimeBackpressureCucumberConfig.EmptyConfiguration.class)
public class Phase3RuntimeBackpressureCucumberConfig {

    @Configuration
    static class EmptyConfiguration {
    }
}
