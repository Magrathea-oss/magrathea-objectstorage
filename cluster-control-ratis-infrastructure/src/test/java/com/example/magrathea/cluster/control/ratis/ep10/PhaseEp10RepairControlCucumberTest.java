package com.example.magrathea.cluster.control.ratis.ep10;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

import static io.cucumber.junit.platform.engine.Constants.*;

/** Focused Story BDD runner for the control/application half of ADR 0029. */
@Suite
@SelectClasspathResource("specs/phase-ep10-three-node-cluster-mechanisms.feature")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.example.magrathea.cluster.control.ratis.ep10")
@ConfigurationParameter(key = FILTER_TAGS_PROPERTY_NAME, value =
        "@REQ-CLUSTER-021 or @REQ-CLUSTER-022 or @REQ-CLUSTER-023 or @REQ-CLUSTER-024 or @REQ-CLUSTER-025 or @REQ-CLUSTER-026")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "pretty,json:target/cucumber-json/ep10-repair-control.json")
public class PhaseEp10RepairControlCucumberTest { }
