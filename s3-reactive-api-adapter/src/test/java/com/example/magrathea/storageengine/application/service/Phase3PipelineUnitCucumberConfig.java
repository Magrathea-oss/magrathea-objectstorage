package com.example.magrathea.storageengine.application.service;

import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;

@CucumberContextConfiguration
@ContextConfiguration(classes = Phase3PipelineUnitCucumberConfig.EmptyConfiguration.class)
public class Phase3PipelineUnitCucumberConfig {

    @Configuration
    static class EmptyConfiguration {
    }
}
