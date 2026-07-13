package com.example.magrathea.s3api.cucumber.ep10;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

import static io.cucumber.junit.platform.engine.Constants.FILTER_TAGS_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;

@Suite
@SelectClasspathResource("specs/phase-ep10-three-node-cluster-mechanisms.feature")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.example.magrathea.s3api.cucumber.ep10")
@ConfigurationParameter(key = FILTER_TAGS_PROPERTY_NAME,
        value = "@phase-ep10 and (@REQ-CLUSTER-009 or @REQ-CLUSTER-010)")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME,
        value = "pretty,json:target/cucumber-json/ep10-cluster-coordination.json")
public class PhaseEp10ClusterCoordinationCucumberTest { }
