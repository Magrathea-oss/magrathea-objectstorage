package com.example.magrathea.s3api.cucumber.requirements;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;

/**
 * Validates the single-node in-memory backend S3 Business Need requirements through
 * WebTestClient, as the companion validation mode to
 * {@link com.example.magrathea.s3api.awscli.SingleNodeBackendAwsCliRequirementsCucumberTest}.
 * Selects the {@code @webclient-required} scenarios from the same shared
 * single-node-backend-*.feature files, per AGENTS.md §A.6 (shared feature, multiple
 * runners) and §B.4 (WebTestClient + AWS CLI both required for S3 compatibility
 * capabilities where applicable).
 *
 * Reuses {@code ObjectStoreStepsCucumberConfig} (glue package
 * {@code com.example.magrathea.s3api.cucumber.steps}), which boots the same
 * no-profile-active single-node configuration ({@code ObjectStoreTestApp}) as the
 * legacy {@code ObjectStoreCucumberTest}, and the existing step definitions
 * (BucketSteps, ObjectSteps, MultipartSteps, CommonSteps) — no glue duplication.
 */
@Suite
@SelectClasspathResource("requirements/single-node-backend-bucket-operations.feature")
@SelectClasspathResource("requirements/single-node-backend-object-metadata-tagging.feature")
@SelectClasspathResource("requirements/single-node-backend-multipart-upload.feature")
@SelectClasspathResource("requirements/single-node-backend-object-crud.feature")
@SelectClasspathResource("requirements/single-node-backend-put-object-header-handling.feature")
@SelectClasspathResource("requirements/single-node-backend-object-extended-operations.feature")
@SelectClasspathResource("requirements/single-node-backend-bucket-configuration.feature")
@SelectClasspathResource("requirements/single-node-backend-runtime-effects.feature")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.example.magrathea.s3api.cucumber.steps")
@ConfigurationParameter(key = "cucumber.filter.tags", value = "@webclient-required")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "pretty,json:target/cucumber-json/cucumber-single-node-backend-webclient.json")
public class SingleNodeBackendWebTestClientRequirementsCucumberTest {
}
