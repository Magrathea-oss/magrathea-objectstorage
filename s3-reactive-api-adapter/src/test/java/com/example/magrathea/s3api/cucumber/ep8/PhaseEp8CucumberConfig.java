package com.example.magrathea.s3api.cucumber.ep8;

import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.test.context.ContextConfiguration;

/** Minimal context for EP-8 document and artifact contracts; no application runtime is started. */
@CucumberContextConfiguration
@ContextConfiguration(classes = PhaseEp8CucumberConfig.class)
public class PhaseEp8CucumberConfig {
}
