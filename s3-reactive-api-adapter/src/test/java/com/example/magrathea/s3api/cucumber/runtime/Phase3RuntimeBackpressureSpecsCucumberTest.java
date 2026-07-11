package com.example.magrathea.s3api.cucumber.runtime;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

import static io.cucumber.junit.platform.engine.Constants.FILTER_TAGS_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;

@Suite
@SelectClasspathResource("specs/phase-3-reactive-pipeline.feature")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.example.magrathea.s3api.cucumber.runtime")
@ConfigurationParameter(key = FILTER_TAGS_PROPERTY_NAME, value = "@runtime-backpressure-required")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "pretty,json:target/cucumber-json/specs-phase-3-runtime-backpressure.json")
public class Phase3RuntimeBackpressureSpecsCucumberTest {
}
