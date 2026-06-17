package com.example.magrathea.s3api.phase2awscli;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

import static io.cucumber.junit.platform.engine.Constants.FILTER_TAGS_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;

@Suite
@SelectClasspathResource("requirements/phase-2-filesystem-reliability.feature")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.example.magrathea.s3api.phase2awscli")
@ConfigurationParameter(key = FILTER_TAGS_PROPERTY_NAME, value = "@phase-2 and @awscli")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "pretty,json:target/cucumber-json/phase-2-filesystem-reliability-awscli.json")
public class Phase2StorageEngineAwsCliRequirementsCucumberTest {
}
