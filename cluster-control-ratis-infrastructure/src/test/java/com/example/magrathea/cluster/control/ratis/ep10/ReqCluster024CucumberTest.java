package com.example.magrathea.cluster.control.ratis.ep10;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

import static io.cucumber.junit.platform.engine.Constants.FILTER_TAGS_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;

/** Strict focused Story BDD gate for all seven REQ-CLUSTER-024 interruption examples. */
@Suite
@SelectClasspathResource("specs/phase-ep10-three-node-cluster-mechanisms.feature")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME,
        value = "com.example.magrathea.cluster.control.ratis.ep10")
@ConfigurationParameter(key = FILTER_TAGS_PROPERTY_NAME, value = "@REQ-CLUSTER-024")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME,
        value = "pretty,json:target/cucumber-json/req-cluster-024.json")
public class ReqCluster024CucumberTest { }
