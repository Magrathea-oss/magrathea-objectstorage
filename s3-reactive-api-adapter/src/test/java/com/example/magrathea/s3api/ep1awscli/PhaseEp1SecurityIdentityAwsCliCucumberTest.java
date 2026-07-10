package com.example.magrathea.s3api.ep1awscli;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

import static io.cucumber.junit.platform.engine.Constants.FILTER_TAGS_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;

@Suite
@SelectClasspathResource("requirements/phase-ep1-security-identity.feature")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.example.magrathea.s3api.ep1awscli")
@ConfigurationParameter(key = FILTER_TAGS_PROPERTY_NAME, value = "@phase-ep1 and @awscli-required")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "pretty,json:target/cucumber-json/phase-ep1-security-identity-awscli.json")
public class PhaseEp1SecurityIdentityAwsCliCucumberTest {
}
