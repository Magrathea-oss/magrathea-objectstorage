package com.example.magrathea.bootstrap.ep10;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

import static io.cucumber.junit.platform.engine.Constants.FILTER_TAGS_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;

/** Focused opt-in runner for the two real-process S3 repair requirements. */
@Suite
@SelectClasspathResource("requirements/phase-ep10-three-node-s3-cluster.feature")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.example.magrathea.bootstrap.ep10")
@ConfigurationParameter(key = FILTER_TAGS_PROPERTY_NAME,
        value = "@REQ-CLUSTER-019 or @REQ-CLUSTER-020")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME,
        value = "pretty,json:target/cucumber-json/ep10-repair-real-process.json")
public class PhaseEp10RepairRealProcessCucumberTest { }
