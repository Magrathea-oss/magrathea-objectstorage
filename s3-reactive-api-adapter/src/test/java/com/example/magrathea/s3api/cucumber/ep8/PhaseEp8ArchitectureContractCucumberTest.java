package com.example.magrathea.s3api.cucumber.ep8;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

import static io.cucumber.junit.platform.engine.Constants.FILTER_TAGS_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;

@Suite
@SelectClasspathResource("specs/phase-ep8-cluster-architecture-supply-chain.feature")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.example.magrathea.s3api.cucumber.ep8")
@ConfigurationParameter(key = FILTER_TAGS_PROPERTY_NAME, value = "@phase-ep8 and (@REQ-HA-001 or @REQ-HA-002 or @REQ-HA-003 or @REQ-HA-004 or @REQ-HA-005 or @REQ-HA-006 or @REQ-HA-007)")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "pretty,json:target/cucumber-json/ep8-architecture-contract.json")
public class PhaseEp8ArchitectureContractCucumberTest {
}
