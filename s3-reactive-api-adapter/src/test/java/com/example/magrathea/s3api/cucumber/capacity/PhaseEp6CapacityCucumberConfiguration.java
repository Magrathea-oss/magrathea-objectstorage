package com.example.magrathea.s3api.cucumber.capacity;

import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.test.context.ContextConfiguration;

@CucumberContextConfiguration
@ContextConfiguration(classes = PhaseEp6CapacityCucumberConfiguration.EmptyConfiguration.class)
public class PhaseEp6CapacityCucumberConfiguration {
    static class EmptyConfiguration {
    }
}
