package com.example.magrathea.s3api.awscli;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@Suite
@SelectClasspathResource("awscli")
@ConfigurationParameter(key = "cucumber.glue", value = "com.example.magrathea.s3api.awscli")
@ConfigurationParameter(key = "cucumber.plugin", value = "pretty,json:target/cucumber-json/cucumber-awscli.json")
@ConfigurationParameter(key = "cucumber.filter.tags", value = "not @unsupported-awscli")
@SpringJUnitConfig(AwsCliCucumberConfig.class)
public class AwsCliCucumberTest {
}
