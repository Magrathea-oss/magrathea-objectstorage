package com.example.magrathea.s3api.cucumber.specs.delivery;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

public class PhaseEp5DeliverySteps {

    private final Path projectRoot = locateProjectRoot();
    private String ciWorkflow;
    private String releaseWorkflow;
    private String releasePolicy;
    private String releaseVersion;

    @Given("the canonical supported artifact is the Linux JVM 21 OCI image")
    public void canonicalArtifactIsLinuxJvm21Image() throws IOException {
        String dockerfile = read("Dockerfile");
        assertThat(dockerfile).contains("eclipse-temurin:21-jre");
        assertThat(dockerfile).contains("ENTRYPOINT [\"java\", \"-jar\", \"/app.jar\"]");
    }

    @When("a pull request or release tag runs the repository CI workflow")
    public void pullRequestOrReleaseTagRunsCi() throws IOException {
        ciWorkflow = read(".github/workflows/ci.yml");
        assertThat(ciWorkflow).contains("pull_request:");
        assertThat(ciWorkflow).contains("tags:", "v[0-9]+.[0-9]+.[0-9]+");
    }

    @Then("the workflow executes the complete root Maven test gate without fail-never mode")
    public void workflowExecutesCompleteRootMavenGate() {
        String gate = job(ciWorkflow, "canonical-quality-gate");
        assertThat(gate).contains("mvn -B --no-transfer-progress test");
        assertThat(gate).doesNotContain(" -fn", "--fail-never");
        assertThat(gate).doesNotContain("-pl ", "-Dtest=");
    }

    @Then("the workflow checks the Gherkin appendix, S3 API coverage, and source hygiene for freshness")
    public void workflowChecksGeneratedAndSourceFreshness() {
        String gate = job(ciWorkflow, "canonical-quality-gate", "focused-cucumber-supplementary");
        assertThat(gate).contains(
            "generate-gherkin-requirements-appendix.py --check",
            "generate-s3-api-coverage.py --check",
            "scripts/check-source-hygiene.sh");
    }

    @Then("the JVM image build starts only after those mandatory gates pass")
    public void imageBuildNeedsMandatoryGate() {
        String imageJob = job(ciWorkflow, "root-jvm-docker", "live-alert-delivery-manual");
        assertThat(imageJob).contains(
            "needs: canonical-quality-gate",
            "-Pdocker-cucumber-tests",
            "PhaseEp5JvmContainerReplacementRequirementsCucumberIT");
    }

    @Then("focused Cucumber jobs may fail fast but cannot replace the complete gate")
    public void focusedCucumberIsSupplementary() {
        String focused = job(ciWorkflow, "focused-cucumber-supplementary", "root-jvm-docker");
        assertThat(focused).contains("Focused fail-fast Cucumber gates (supplementary)", "-Dtest=");
        assertThat(ciWorkflow).contains("canonical-quality-gate:", "focused-cucumber-supplementary:");
        assertThat(job(ciWorkflow, "root-jvm-docker", "live-alert-delivery-manual"))
            .contains("needs: canonical-quality-gate");
    }

    @Given("the release policy declares semantic versioning and the supported single-node preview scope")
    public void policyDeclaresSemverAndPreviewScope() throws IOException {
        releasePolicy = read("docs/release-policy.md");
        releaseWorkflow = read(".github/workflows/release.yml");
        assertThat(releasePolicy).contains(
            "Semantic Versioning 2.0.0",
            "`v0.1.0`",
            "single-node preview",
            "Linux JVM 21 OCI image",
            "/app/data");
        assertThat(releasePolicy).contains("not a claim of high availability");
    }

    @When("tag {string} starts the release workflow")
    public void tagStartsReleaseWorkflow(String tag) {
        releaseVersion = tag.substring(1);
        assertThat(tag).isEqualTo("v0.1.0");
        assertThat(releaseWorkflow).contains("push:", "tags:", "GITHUB_REF_NAME", "${TAG#v}");
    }

    @Then("Maven release version, Git tag, OCI version label, and image version tag all equal {string}")
    public void releaseIdentityIsCoherent(String expectedVersion) throws IOException {
        assertThat(releaseVersion).isEqualTo(expectedVersion);
        assertThat(read("pom.xml")).contains("<version>0.1.0-SNAPSHOT</version>");
        assertThat(releaseWorkflow).contains(
            "DECLARED_VERSION=\"$(mvn -q -DforceStdout help:evaluate -Dexpression=project.version)\"",
            "\"$DECLARED_VERSION\" != \"$VERSION-SNAPSHOT\"",
            "versions:set -DnewVersion=\"$VERSION\"",
            "test \"$ACTUAL_VERSION\" = \"$VERSION\"",
            "org.opencontainers.image.version=${{ steps.identity.outputs.version }}",
            "${{ steps.identity.outputs.image }}:${{ steps.identity.outputs.version }}");
    }

    @Then("the image records source revision, source URL, license, and creation time OCI labels")
    public void imageHasTraceabilityLabels() {
        assertThat(releaseWorkflow).contains(
            "org.opencontainers.image.revision=${{ github.sha }}",
            "org.opencontainers.image.source=${{ github.server_url }}/${{ github.repository }}",
            "org.opencontainers.image.url=${{ github.server_url }}/${{ github.repository }}",
            "org.opencontainers.image.licenses=MIT",
            "org.opencontainers.image.created=${{ steps.identity.outputs.created_at }}");
    }

    @Then("the registry receives immutable version, minor-line, and commit-SHA tags")
    public void registryGetsImmutableTags() {
        assertThat(releaseWorkflow).contains(
            "Refuse to overwrite immutable tags",
            "for tag in \"$VERSION\" \"$MINOR_LINE\" \"sha-$GITHUB_SHA\"",
            "${{ steps.identity.outputs.image }}:${{ steps.identity.outputs.version }}",
            "${{ steps.identity.outputs.image }}:${{ steps.identity.outputs.minor_line }}",
            "${{ steps.identity.outputs.image }}:sha-${{ github.sha }}");
        assertThat(releaseWorkflow).contains("push: true");
    }

    @Then("the release records the image digest and declares the native image experimental")
    public void releaseRecordsDigestAndNativeStatus() {
        assertThat(releaseWorkflow).contains(
            "DIGEST: ${{ steps.publish.outputs.digest }}",
            "gh release create",
            "Immutable digest:",
            "native image is experimental");
        assertThat(releasePolicy).contains(
            "native image remains experimental",
            "not published by the release workflow");
        assertThat(releaseWorkflow).doesNotContain("Dockerfile.native");
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(projectRoot.resolve(relativePath));
    }

    private static String job(String workflow, String jobName) {
        int start = workflow.indexOf("  " + jobName + ":");
        assertThat(start).as("job %s", jobName).isGreaterThanOrEqualTo(0);

        var followingJob = Pattern.compile("(?m)^  [A-Za-z0-9_-]+:").matcher(workflow);
        int end = followingJob.find(start + 1) ? followingJob.start() : workflow.length();
        return workflow.substring(start, end);
    }

    private static String job(String workflow, String jobName, String nextJobName) {
        int start = workflow.indexOf("  " + jobName + ":");
        int end = workflow.indexOf("\n  " + nextJobName + ":", start + 1);
        assertThat(start).as("job %s", jobName).isGreaterThanOrEqualTo(0);
        assertThat(end).as("job following %s", jobName).isGreaterThan(start);
        return workflow.substring(start, end);
    }

    private static Path locateProjectRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("pom.xml"))
                    && Files.isRegularFile(current.resolve(".github/workflows/ci.yml"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Could not locate repository root");
    }
}
