package com.example.magrathea.s3api.cucumber.requirements;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

import static io.cucumber.junit.platform.engine.Constants.FILTER_TAGS_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;

/**
 * Phase 3 reactive pipeline WebTestClient Ability spec runner.
 *
 * <p>Selects implemented {@code @webclient} Examples rows from the shared
 * {@code phase-3-reactive-pipeline.feature} file. Runner-specific HTTP,
 * StorageEvent capture, and filesystem inspection remain in WebTestClient glue;
 * the pipeline-unit runner consumes the same scenario text through its own glue.
 */
@Suite
@SelectClasspathResource("specs/phase-3-reactive-pipeline.feature")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.example.magrathea.s3api.cucumber.requirements")
@ConfigurationParameter(key = FILTER_TAGS_PROPERTY_NAME, value = "@phase-3 and @webclient and not @not-implemented")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "pretty,json:target/cucumber-json/phase-3-reactive-pipeline-webclient.json")
public class Phase3ReactivePipelineCucumberTest {
}
