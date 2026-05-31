package com.example.magrathea.s3api.cucumber;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@Suite
@SelectClasspathResource("object-store")
@ConfigurationParameter(key = "cucumber.glue", value = "com.example.magrathea.s3api.cucumber.steps,com.example.magrathea.s3api.cucumber")
@SpringJUnitConfig(ObjectStoreCucumberConfig.class)
public class ObjectStoreCucumberTest {
}
