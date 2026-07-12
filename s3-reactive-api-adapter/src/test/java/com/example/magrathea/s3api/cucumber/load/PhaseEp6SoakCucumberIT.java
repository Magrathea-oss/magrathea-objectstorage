package com.example.magrathea.s3api.cucumber.load;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

import static io.cucumber.junit.platform.engine.Constants.FILTER_TAGS_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;

@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("specs/phase-ep6-resource-bounds.feature")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.example.magrathea.s3api.cucumber.load")
@ConfigurationParameter(key = FILTER_TAGS_PROPERTY_NAME, value = "@soak-required and not @component-spec-required")
public class PhaseEp6SoakCucumberIT {}
