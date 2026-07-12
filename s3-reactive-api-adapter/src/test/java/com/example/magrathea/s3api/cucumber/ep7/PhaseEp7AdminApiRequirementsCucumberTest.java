package com.example.magrathea.s3api.cucumber.ep7;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

import static io.cucumber.junit.platform.engine.Constants.FILTER_TAGS_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;

@Suite
@SelectClasspathResource("requirements/phase-ep7-admin-panel.feature")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.example.magrathea.s3api.cucumber.ep7")
@ConfigurationParameter(key = FILTER_TAGS_PROPERTY_NAME, value =
    "@REQ-ADMIN-023 or @REQ-ADMIN-024 or @REQ-ADMIN-025 or @REQ-ADMIN-026 or "
        + "@REQ-ADMIN-027 or @REQ-ADMIN-028 or @REQ-ADMIN-029 or @REQ-ADMIN-030 or @REQ-ADMIN-031")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME,
    value = "pretty,json:target/cucumber-json/phase-ep7-admin-api.json")
public class PhaseEp7AdminApiRequirementsCucumberTest {
}
