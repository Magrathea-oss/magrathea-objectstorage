package com.example.magrathea.cluster.control.ratis.reqcluster015;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

import static io.cucumber.junit.platform.engine.Constants.FILTER_TAGS_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;

/** Focused fixed A/B/C distributed EC 4+2 mechanism gate. */
@Suite
@SelectClasspathResource("specs/phase-ep10-three-node-cluster-mechanisms.feature")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME,
        value = "com.example.magrathea.cluster.control.ratis.reqcluster015")
@ConfigurationParameter(key = FILTER_TAGS_PROPERTY_NAME, value = "@REQ-CLUSTER-015")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME,
        value = "pretty,json:target/cucumber-json/req-cluster-015-distributed-ec.json")
public class ReqCluster015DistributedEcCucumberTest { }
