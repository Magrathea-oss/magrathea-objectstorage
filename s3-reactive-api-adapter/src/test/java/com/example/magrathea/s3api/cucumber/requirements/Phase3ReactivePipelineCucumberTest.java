package com.example.magrathea.s3api.cucumber.requirements;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

import static io.cucumber.junit.platform.engine.Constants.FILTER_TAGS_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;

/**
 * Phase 3 reactive pipeline WebTestClient requirements runner.
 *
 * <p>Selects {@code @webclient} Examples rows from the shared
 * {@code phase-3-reactive-pipeline.feature} file that are NOT tagged
 * {@code @not-implemented}. The pipeline-unit Examples rows (tagged
 * {@code @pipeline-unit}) are excluded here and belong to the pipeline-unit
 * runner once the StorageStage/StorageEvent abstractions are fully wired.
 *
 * <p>All Phase 3 scenarios are currently tagged {@code @not-implemented}
 * because they require StorageStage, StorageContext, and StorageEvent
 * abstractions that have not yet been implemented. Excluding {@code @not-implemented}
 * avoids {@link io.cucumber.java.PendingException} propagating as a test
 * error (a JUnit Platform 6.x / Cucumber 7.18.0 / Surefire 3.5.4 compatibility
 * constraint where pending scenarios report as errors rather than skips).
 *
 * <p>Once a Phase 3 webclient scenario is promoted to
 * {@code @implemented-not-e2e-validated} or {@code @implemented-and-validated},
 * remove the scenario's {@code @not-implemented} tag and it will automatically
 * be picked up by this runner.
 */
@Suite
@SelectClasspathResource("requirements/phase-3-reactive-pipeline.feature")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.example.magrathea.s3api.cucumber.requirements")
@ConfigurationParameter(key = FILTER_TAGS_PROPERTY_NAME, value = "@phase-3 and @webclient and not @not-implemented")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "pretty,json:target/cucumber-json/phase-3-reactive-pipeline-webclient.json")
public class Phase3ReactivePipelineCucumberTest {
}
