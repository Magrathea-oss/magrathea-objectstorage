package com.example.magrathea.s3api.cucumber.requirements;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

import static io.cucumber.junit.platform.engine.Constants.FILTER_TAGS_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;

@Suite
@SelectClasspathResource("requirements/phase-5-s3-semantic-compatibility.feature")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.example.magrathea.s3api.cucumber.requirements")
@ConfigurationParameter(key = FILTER_TAGS_PROPERTY_NAME, value = "@phase-5 and @webclient-required")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "pretty,json:target/cucumber-json/phase-5-s3-semantic-compatibility-webclient.json")
public class Phase5S3SemanticCompatibilityRequirementsCucumberTest {
}
