package com.example.magrathea.s3api.cucumber;

import com.example.magrathea.s3api.cucumber.steps.ObjectStoreStepsCucumberConfig;
import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;

@Suite
@SelectClasspathResource("object-store")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.example.magrathea.s3api.cucumber.steps")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "pretty,json:target/cucumber-json/cucumber.json")
@SpringJUnitConfig(ObjectStoreStepsCucumberConfig.class)
public class ObjectStoreCucumberTest {
}
