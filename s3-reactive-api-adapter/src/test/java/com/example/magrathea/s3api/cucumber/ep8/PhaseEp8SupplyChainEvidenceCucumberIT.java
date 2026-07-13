package com.example.magrathea.s3api.cucumber.ep8;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

import static io.cucumber.junit.platform.engine.Constants.FILTER_TAGS_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;

/** Opt-in: run only after the clean-checkout evidence orchestration has completed. */
@Suite
@SelectClasspathResource("specs/phase-ep8-cluster-architecture-supply-chain.feature")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.example.magrathea.s3api.cucumber.ep8")
@ConfigurationParameter(key = FILTER_TAGS_PROPERTY_NAME, value = "@phase-ep8 and (@REQ-SUPPLY-001 or @REQ-SUPPLY-002 or @REQ-SUPPLY-003 or @REQ-SUPPLY-004 or @REQ-SUPPLY-005 or @REQ-SUPPLY-006)")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "pretty,json:target/cucumber-json/ep8-supply-chain-evidence.json")
public class PhaseEp8SupplyChainEvidenceCucumberIT {
}
