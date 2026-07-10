package com.example.magrathea.s3api.phaseep2;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

import static io.cucumber.junit.platform.engine.Constants.FILTER_TAGS_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;

@Suite
@SelectClasspathResource("requirements/phase-ep2-metadata-durability.feature")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.example.magrathea.s3api.phaseep2")
@ConfigurationParameter(key = FILTER_TAGS_PROPERTY_NAME, value = "@ep2-webclient-restart")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "pretty,json:target/cucumber-json/phase-ep2-metadata-durability.json")
public class PhaseEp2MetadataDurabilityCucumberTest {
}
