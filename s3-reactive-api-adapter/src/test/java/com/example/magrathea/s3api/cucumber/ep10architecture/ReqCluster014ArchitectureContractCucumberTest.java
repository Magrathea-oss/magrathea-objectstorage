package com.example.magrathea.s3api.cucumber.ep10architecture;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

import static io.cucumber.junit.platform.engine.Constants.FILTER_TAGS_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;

/** Focused Story BDD gate for the REQ-CLUSTER-014 repository architecture contract. */
@Suite
@SelectClasspathResource("specs/phase-ep10-three-node-cluster-mechanisms.feature")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME,
        value = "com.example.magrathea.s3api.cucumber.ep10architecture")
@ConfigurationParameter(key = FILTER_TAGS_PROPERTY_NAME, value = "@REQ-CLUSTER-014")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME,
        value = "pretty,json:target/cucumber-json/req-cluster-014-architecture-contract.json")
public class ReqCluster014ArchitectureContractCucumberTest { }
