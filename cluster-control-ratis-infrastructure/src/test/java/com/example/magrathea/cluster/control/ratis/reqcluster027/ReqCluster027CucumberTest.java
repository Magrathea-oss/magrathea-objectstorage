package com.example.magrathea.cluster.control.ratis.reqcluster027;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

import static io.cucumber.junit.platform.engine.Constants.FILTER_TAGS_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;

/** Focused Story BDD gate for REQ-CLUSTER-027 periodic anti-entropy. */
@Suite
@SelectClasspathResource("specs/phase-ep10-three-node-cluster-mechanisms.feature")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME,
        value = "com.example.magrathea.cluster.control.ratis.reqcluster027")
@ConfigurationParameter(key = FILTER_TAGS_PROPERTY_NAME, value = "@REQ-CLUSTER-027")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME,
        value = "pretty,json:target/cucumber-json/req-cluster-027.json")
public class ReqCluster027CucumberTest { }
