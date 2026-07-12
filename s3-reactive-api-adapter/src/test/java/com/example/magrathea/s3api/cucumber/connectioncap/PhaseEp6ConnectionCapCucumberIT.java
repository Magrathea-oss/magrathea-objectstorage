package com.example.magrathea.s3api.cucumber.connectioncap;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

import static io.cucumber.junit.platform.engine.Constants.FILTER_TAGS_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;

@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("requirements/phase-ep6-performance-capacity.feature")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME,
    value = "com.example.magrathea.s3api.cucumber.connectioncap")
@ConfigurationParameter(key = FILTER_TAGS_PROPERTY_NAME, value = "@REQ-PERF-008")
public class PhaseEp6ConnectionCapCucumberIT {
}
