package com.example.magrathea.s3api.phase5awscli;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

import static io.cucumber.junit.platform.engine.Constants.FILTER_TAGS_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;

/**
 * Phase 5 S3 semantic compatibility AWS CLI runner.
 *
 * <p>Executes {@code @awscli-required} scenarios from the shared
 * {@code phase-5-s3-semantic-compatibility.feature} file using
 * {@code aws s3api} commands against a real HTTP server started by
 * {@link Phase5S3SemanticCompatibilityAwsCliCucumberConfig}.
 *
 * <p>AWS CLI availability is checked in the step definitions' {@code @Before}
 * hook using {@link org.junit.jupiter.api.Assumptions}. All scenarios are
 * skipped if the AWS CLI is not installed, so the suite never fails due to a
 * missing tool.
 *
 * <p>Covers: multipart-ETag (REQ-S3-002-A and -B), byte-range reads
 * (REQ-S3-003-A through -D), and conditional requests
 * (REQ-S3-004-A through -F).
 */
@Suite
@SelectClasspathResource("requirements/phase-5-s3-semantic-compatibility.feature")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.example.magrathea.s3api.phase5awscli")
@ConfigurationParameter(key = FILTER_TAGS_PROPERTY_NAME, value = "@awscli-required and @phase-5")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "pretty,json:target/cucumber-json/phase-5-s3-semantic-compatibility-awscli.json")
public class Phase5S3SemanticCompatibilityAwsCliCucumberTest {
}
