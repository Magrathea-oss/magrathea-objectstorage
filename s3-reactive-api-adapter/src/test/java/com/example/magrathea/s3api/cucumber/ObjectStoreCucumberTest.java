package com.example.magrathea.s3api.cucumber;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@Suite
@SelectClasspathResource("object-store")
@ConfigurationParameter(key = "cucumber.glue", value = "com.example.magrathea.s3api.cucumber")
@ConfigurationParameter(key = "cucumber.plugin", value = "pretty,json:target/cucumber-json/cucumber.json")
@SpringJUnitConfig(ObjectStoreCucumberConfig.class)
public class ObjectStoreCucumberTest {
}
