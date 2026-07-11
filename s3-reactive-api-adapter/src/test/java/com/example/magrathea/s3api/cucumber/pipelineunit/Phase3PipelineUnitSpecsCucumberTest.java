package com.example.magrathea.s3api.cucumber.pipelineunit;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

import static io.cucumber.junit.platform.engine.Constants.FILTER_TAGS_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;

@Suite
@SelectClasspathResource("specs/phase-3-reactive-pipeline.feature")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.example.magrathea.storageengine.application.service")
@ConfigurationParameter(key = FILTER_TAGS_PROPERTY_NAME, value = "(@REQ-PIPELINE-001 or @REQ-PIPELINE-002 or @REQ-PIPELINE-003 or @REQ-PIPELINE-004 or @REQ-PIPELINE-005 or @REQ-PIPELINE-006 or @REQ-PIPELINE-014 or @REQ-PIPELINE-015) and @pipeline-unit and not @not-implemented")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "pretty,json:target/cucumber-json/specs-phase-3-pipeline-unit.json")
public class Phase3PipelineUnitSpecsCucumberTest {
}
