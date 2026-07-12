package com.example.magrathea.s3api.cucumber.load;

import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;

@CucumberContextConfiguration
@ContextConfiguration(classes = PhaseEp6LoadCucumberConfig.EmptyConfig.class)
public class PhaseEp6LoadCucumberConfig {
    @Configuration
    static class EmptyConfig {}
}
