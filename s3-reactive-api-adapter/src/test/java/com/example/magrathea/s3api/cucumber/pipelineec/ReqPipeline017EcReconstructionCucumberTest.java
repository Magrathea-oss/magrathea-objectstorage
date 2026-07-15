package com.example.magrathea.s3api.cucumber.pipelineec;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

import static io.cucumber.junit.platform.engine.Constants.FILTER_TAGS_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;

/** Focused Story BDD review gate for local bounded EC reconstruction REQ-PIPELINE-017. */
@Suite
@SelectClasspathResource("specs/phase-3-reactive-pipeline.feature")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME,
        value = "com.example.magrathea.s3api.cucumber.pipelineec")
@ConfigurationParameter(key = FILTER_TAGS_PROPERTY_NAME, value = "@REQ-PIPELINE-017")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME,
        value = "pretty,json:target/cucumber-json/req-pipeline-017-ec-reconstruction.json")
public class ReqPipeline017EcReconstructionCucumberTest { }
