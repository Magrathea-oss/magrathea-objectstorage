package com.example.magrathea.cluster.data.grpc.ep10;

import org.junit.platform.suite.api.*;
import static io.cucumber.junit.platform.engine.Constants.*;

@Suite
@SelectClasspathResource("specs/phase-ep10-three-node-cluster-mechanisms.feature")
@ConfigurationParameter(key=GLUE_PROPERTY_NAME,value="com.example.magrathea.cluster.data.grpc.ep10")
@ConfigurationParameter(key=FILTER_TAGS_PROPERTY_NAME,value="@phase-ep10 and (@REQ-CLUSTER-011 or @REQ-CLUSTER-012 or @REQ-CLUSTER-013)")
@ConfigurationParameter(key=PLUGIN_PROPERTY_NAME,value="pretty,json:target/cucumber-json/ep10-data-plane.json")
public class PhaseEp10DataPlaneCucumberTest { }
