package com.example.magrathea.cluster.control.ratis.ep10;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

import static io.cucumber.junit.platform.engine.Constants.FILTER_TAGS_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;

/** Focused acceptance gate for the REQ-CLUSTER-013 Ratis mTLS boundary. */
@Suite
@SelectClasspathResource("specs/phase-ep10-three-node-cluster-mechanisms.feature")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.example.magrathea.cluster.control.ratis.ep10")
@ConfigurationParameter(key = FILTER_TAGS_PROPERTY_NAME, value = "@phase-ep10 and @REQ-CLUSTER-013")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "pretty,json:target/cucumber-json/ep10-control-plane-tls.json")
public class PhaseEp10ControlPlaneTlsCucumberTest { }
