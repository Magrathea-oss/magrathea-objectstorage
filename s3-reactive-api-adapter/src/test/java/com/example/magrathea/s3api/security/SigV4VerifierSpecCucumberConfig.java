package com.example.magrathea.s3api.security;

import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;

@CucumberContextConfiguration
@ContextConfiguration(classes = SigV4VerifierSpecCucumberConfig.NoopConfig.class)
public class SigV4VerifierSpecCucumberConfig {
    @Configuration
    static class NoopConfig {
    }
}
