package com.example.magrathea.s3api.cucumber.requirements;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

import static io.cucumber.junit.platform.engine.Constants.FILTER_TAGS_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;

@Suite
@SelectClasspathResource("requirements/phase-1-upload-storage-engine.feature")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.example.magrathea.s3api.cucumber.requirements")
@ConfigurationParameter(key = FILTER_TAGS_PROPERTY_NAME, value = "(@REQ-UPLOAD-001 or @REQ-UPLOAD-002 or @REQ-UPLOAD-004 or @REQ-UPLOAD-005 or @REQ-UPLOAD-006) and @webclient and not @awscli and not @bootstrap-integration-required")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "pretty,json:target/cucumber-json/phase-1-upload-storage-engine-webclient.json")
public class Phase1UploadStorageEngineCucumberTest {
}
