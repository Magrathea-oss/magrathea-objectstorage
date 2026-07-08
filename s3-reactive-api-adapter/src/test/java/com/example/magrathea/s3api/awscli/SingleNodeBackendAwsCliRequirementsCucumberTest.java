package com.example.magrathea.s3api.awscli;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/**
 * Validates the single-node in-memory backend S3 Business Need requirements through
 * the AWS CLI. These requirement scenarios previously lived as legacy, unclassified
 * "Feature:" scenarios under features/awscli/ (see ADR 0021); they have been migrated
 * into features/requirements/single-node-backend-*.feature as classified Business Need
 * scenarios that explicitly declare their backend scope.
 *
 * Per ADR-0014 and README.md, "single-node" is the Spring-default profile
 * (spring.profiles.default=single-node in the bootstrap application; Spring's own
 * implicit "default" profile in this test harness, which sets no application.properties)
 * and it is documented as suitable for development and single-node/test deployments,
 * not as durable production storage. This is a distinct validation scope from
 * Phase1UploadStorageEngineAwsCliCucumberTest and Phase5S3SemanticCompatibilityAwsCliCucumberTest,
 * which activate the "storage-engine" profile \u2014 the durable, production-grade backend.
 * Reuses the existing AwsCliObjectSteps glue.
 */
@Suite
@SelectClasspathResource("requirements/single-node-backend-bucket-operations.feature")
@SelectClasspathResource("requirements/single-node-backend-object-metadata-tagging.feature")
@SelectClasspathResource("requirements/single-node-backend-multipart-upload.feature")
@SelectClasspathResource("requirements/single-node-backend-object-crud.feature")
@ConfigurationParameter(key = "cucumber.glue", value = "com.example.magrathea.s3api.awscli")
@ConfigurationParameter(key = "cucumber.filter.tags", value = "@awscli-required")
@ConfigurationParameter(key = "cucumber.plugin", value = "pretty,json:target/cucumber-json/cucumber-single-node-backend-awscli.json")
@SpringJUnitConfig(AwsCliCucumberConfig.class)
public class SingleNodeBackendAwsCliRequirementsCucumberTest {
}
