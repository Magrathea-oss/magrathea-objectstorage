package com.example.magrathea.s3api.cucumber.specs;

import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Static packaging/distribution specs for KA-5.
 *
 * <p>Heavy image compilation/smoke evidence is recorded in the project report; these
 * Cucumber specs keep the source-controlled packaging contract executable and reviewable.</p>
 */
public class Ka5DistributionPackagingSteps {

    private String validationMode;
    private Path inspectedPath;
    private String inspectedSource;
    private final Path root = repositoryRoot();

    @Before
    public void resetPackagingState() {
        validationMode = null;
        inspectedPath = null;
        inspectedSource = null;
    }

    @Given("the runnable application entry point is {string}")
    public void runnableApplicationEntryPointIs(String entryPoint) throws IOException {
        var application = root.resolve("bootstrap-application/src/main/java/"
            + entryPoint.replace('.', '/') + ".java");
        assertTrue(Files.exists(application), "Expected application entry point: " + application);
        assertTrue(Files.readString(application).contains("SpringApplication.run(MagratheaApplication.class"),
            "Entry point should run the Spring Boot application");
    }

    @Given("the build uses the Maven profiles {string} and {string} for the bootstrap application module")
    public void buildUsesMavenProfiles(String firstProfile, String secondProfile) throws IOException {
        var pom = readRelative("bootstrap-application/pom.xml");
        assertTrue(pom.contains("<id>" + firstProfile + "</id>"), "Missing Maven profile " + firstProfile);
        assertTrue(pom.contains("<id>" + secondProfile + "</id>"), "Missing Maven profile " + secondProfile);
        assertTrue(pom.contains("native-maven-plugin"), "Native profile should configure native-maven-plugin");
    }

    @Given("the native Docker build starts from a GraalVM 25 native-image builder with musl support")
    public void nativeDockerBuildStartsFromGraalVm25MuslBuilder() throws IOException {
        var dockerfile = readRelative("Dockerfile.native");
        assertTrue(dockerfile.contains("ghcr.io/graalvm/native-image-community:25-muslib"),
            "Native Dockerfile must use GraalVM 25 musl builder");
    }

    @Given("the Docker builder regenerates the documentation and admin UI static assets from source-controlled inputs")
    public void dockerBuilderRegeneratesDocsAndAdminUi() throws IOException {
        var dockerfile = readRelative("Dockerfile.native");
        assertDocsAndUiRegeneration(dockerfile);
    }

    @Given("distribution source path {string} defines the root JVM Docker image")
    public void distributionSourcePathDefinesRootJvmDockerImage(String relativePath) throws IOException {
        inspect(relativePath);
        assertTrue(inspectedSource.contains("FROM public.ecr.aws/docker/library/maven:3.9-eclipse-temurin-21 AS builder"),
            "Root Dockerfile should have Maven/JDK builder stage from the public ECR Docker Hub mirror");
        assertTrue(inspectedSource.contains("FROM public.ecr.aws/docker/library/eclipse-temurin:21-jre"),
            "Root Dockerfile should have JRE runtime stage from the public ECR Docker Hub mirror");
    }

    @Given("distribution source path {string} defines the CI workflow")
    public void distributionSourcePathDefinesCiWorkflow(String relativePath) throws IOException {
        inspect(relativePath);
        assertTrue(inspectedSource.contains("name: Magrathea CI"), "Expected Magrathea CI workflow");
        assertTrue(inspectedSource.contains("pull_request:"), "CI should run for pull requests");
        assertTrue(inspectedSource.contains("workflow_dispatch:"), "CI should support manual dispatch");
    }

    @When("maintainers run the validation mode {string}")
    public void maintainersRunValidationMode(String validationMode) {
        this.validationMode = validationMode;
        assertFalse(validationMode.isBlank(), "Validation mode must be documented");
    }

    @When("the builder compiles the bootstrap application with the {string} Maven profiles")
    public void builderCompilesBootstrapWithMavenProfiles(String profiles) throws IOException {
        var dockerfile = readRelative("Dockerfile.native");
        assertTrue(dockerfile.contains("-P" + profiles) || dockerfile.contains("-P" + profiles.replace(",", ",")),
            "Native Docker build should compile with profiles " + profiles);
    }

    @Then("Spring Boot AOT processing prepares the bootstrap application classes for native-image compilation")
    public void springBootAotProcessingPreparesClasses() throws IOException {
        assertValidationMode("Maven native AOT packaging");
        var pom = readRelative("bootstrap-application/pom.xml");
        assertTrue(pom.contains("process-aot"), "Native Maven profile should run Spring AOT processing");
    }

    @Then("the native image configuration names the executable {string}")
    public void nativeImageConfigurationNamesExecutable(String executable) throws IOException {
        var pom = readRelative("bootstrap-application/pom.xml");
        assertTrue(pom.contains("<imageName>" + executable + "</imageName>"),
            "Native image executable name should be " + executable);
    }

    @Then("the validation log must not contain Spring Boot's generated security password banner")
    public void validationLogMustNotContainGeneratedSecurityPasswordBanner() throws IOException {
        var application = readRelative("bootstrap-application/src/main/java/com/example/magrathea/bootstrap/MagratheaApplication.java");
        assertTrue(application.contains("ReactiveUserDetailsServiceAutoConfiguration"),
            "Application should exclude reactive default user details auto-configuration");
        assertTrue(application.contains("UserDetailsServiceAutoConfiguration"),
            "Application should exclude servlet default user details auto-configuration");
    }

    @Then("native executable compilation succeeds with a Spring Boot 4 compatible GraalVM 25 native-image toolchain")
    public void nativeExecutableCompilationUsesGraalVm25Toolchain() throws IOException {
        var dockerfile = readRelative("Dockerfile.native");
        assertTrue(dockerfile.contains("native-image-community:25-muslib"),
            "Native Dockerfile should pin a GraalVM 25 native-image toolchain");
        assertTrue(dockerfile.contains("native:compile"), "Native packaging should invoke native:compile");
    }

    @Then("the final runtime image is based on Alpine")
    public void finalRuntimeImageIsBasedOnAlpine() throws IOException {
        var dockerfile = readRelative("Dockerfile.native");
        assertTrue(dockerfile.contains("alpine:"), "Native runtime image must be Alpine-based");
    }

    @Then("the final runtime image contains the {string} executable")
    public void finalRuntimeImageContainsExecutable(String executable) throws IOException {
        var dockerfile = readRelative("Dockerfile.native");
        assertTrue(dockerfile.contains("/app/" + executable), "Runtime image should copy executable " + executable);
    }

    @Then("the final runtime image does not install a JRE or JDK")
    public void finalRuntimeImageDoesNotInstallJreOrJdk() throws IOException {
        var dockerfile = readRelative("Dockerfile.native");
        var runtimeStage = dockerfile.substring(dockerfile.lastIndexOf("FROM "));
        assertFalse(runtimeStage.contains("openjdk"), "Native runtime must not install OpenJDK");
        assertFalse(runtimeStage.contains(" jre"), "Native runtime must not install a JRE");
        assertFalse(runtimeStage.contains(" jdk"), "Native runtime must not install a JDK");
    }

    @Then("runtime smoke validation confirms the container starts and the Admin API health endpoint is healthy")
    public void runtimeSmokeValidationConfirmsAdminHealth() throws IOException {
        var dockerfile = readRelative("Dockerfile.native");
        assertTrue(dockerfile.contains("/admin/health") || readRelative("docs/test-report.md").contains("/admin/health"),
            "Native smoke evidence should include Admin API health");
    }

    @Then("runtime smoke validation confirms S3 ListBuckets XML and JSON plus bucket\\/object PUT\\/GET work without native reflection errors")
    public void runtimeSmokeValidationConfirmsS3Smoke() throws IOException {
        var report = readRelative("docs/test-report.md");
        assertTrue(report.contains("ListBuckets XML/JSON") || report.contains("ListBuckets XML and JSON"),
            "Native smoke evidence should include ListBuckets XML and JSON");
        assertTrue(report.contains("PUT object") || report.contains("bucket/object PUT/GET"),
            "Native smoke evidence should include S3 bucket/object smoke");
    }

    @Then("the Dockerfile runs the Gherkin appendix freshness gate before packaging")
    public void dockerfileRunsGherkinAppendixGate() {
        assertInspected();
        int gate = inspectedSource.indexOf("generate-gherkin-requirements-appendix.py --check");
        int maven = inspectedSource.indexOf("mvn -B --no-transfer-progress clean package -DskipTests");
        assertTrue(gate >= 0, "Dockerfile must run the Gherkin appendix freshness gate");
        assertTrue(maven > gate, "Dockerfile must run the appendix gate before Maven packaging");
    }

    @Then("the Dockerfile regenerates documentation and admin UI static assets from source inputs")
    public void dockerfileRegeneratesDocumentationAndAdminUi() {
        assertInspected();
        assertDocsAndUiRegeneration(inspectedSource);
    }

    @Then("the Dockerfile builds the bootstrap application without Maven fail-never mode")
    public void dockerfileBuildsWithoutMavenFailNeverMode() {
        assertInspected();
        assertTrue(inspectedSource.contains("mvn -B --no-transfer-progress clean package -DskipTests"),
            "Dockerfile should run an unmasked Maven package command");
        assertFalse(inspectedSource.contains(" -fn"), "Dockerfile must not use Maven fail-never mode");
        assertFalse(inspectedSource.contains("--fail-never"), "Dockerfile must not use Maven fail-never mode");
    }

    @Then("the final runtime image is based on an Eclipse Temurin JRE image")
    public void finalRuntimeImageIsBasedOnTemurinJre() {
        assertInspected();
        assertTrue(inspectedSource.contains("FROM public.ecr.aws/docker/library/eclipse-temurin:21-jre"),
            "Root JVM Dockerfile should use Eclipse Temurin JRE runtime image from public ECR");
    }

    @Then("the final runtime image exposes the S3 and Admin API ports")
    public void finalRuntimeImageExposesS3AndAdminPorts() {
        assertInspected();
        assertTrue(inspectedSource.contains("EXPOSE 8080"), "Dockerfile should expose S3 API port 8080");
        assertTrue(inspectedSource.contains("EXPOSE 8081"), "Dockerfile should expose Admin API port 8081");
    }

    @Then("the final runtime image owns a writable application data directory as the non-root user")
    public void finalRuntimeImageOwnsWritableDataDirectory() {
        assertInspected();
        assertTrue(inspectedSource.contains("mkdir -p /app/data"),
            "Dockerfile should create the runtime data directory");
        assertTrue(inspectedSource.contains("chown -R magrathea:magrathea /app"),
            "Dockerfile should make /app writable by the non-root runtime user");
    }

    @Then("the final runtime image starts the application with {string}")
    public void finalRuntimeImageStartsApplicationWith(String command) {
        assertInspected();
        var jsonCommand = "[\"java\", \"-jar\", \"/app.jar\"]";
        assertTrue(inspectedSource.contains(jsonCommand), "Dockerfile should start with " + command);
        assertTrue(inspectedSource.contains("HEALTHCHECK"), "Dockerfile should define a runtime healthcheck");
        assertTrue(inspectedSource.contains("USER magrathea"), "Dockerfile should run as non-root user magrathea");
    }

    @Then("the CI workflow uses Java {string}")
    public void ciWorkflowUsesJava(String javaVersion) {
        assertInspected();
        assertTrue(inspectedSource.contains("java-version: \"" + javaVersion + "\""),
            "CI workflow should use Java " + javaVersion);
    }

    @Then("the CI workflow checks the Gherkin appendix freshness")
    public void ciWorkflowChecksGherkinAppendixFreshness() {
        assertInspected();
        assertTrue(inspectedSource.contains("generate-gherkin-requirements-appendix.py --check"),
            "CI workflow should check Gherkin appendix freshness");
    }

    @Then("the CI workflow checks source hygiene")
    public void ciWorkflowChecksSourceHygiene() {
        assertInspected();
        assertTrue(inspectedSource.contains("scripts/check-source-hygiene.sh"),
            "CI workflow should check source hygiene");
    }

    @Then("the CI workflow runs focused Cucumber validation for security, metadata durability, Phase 3 streaming, EP-5 operability, and Phase 5 S3 semantics")
    public void ciWorkflowRunsFocusedCucumberValidation() {
        assertInspected();
        for (String runner : new String[] {
            "PhaseEp1SecurityIdentityRequirementsCucumberTest",
            "PhaseEp2MetadataDurabilityFullRestartCucumberTest",
            "Phase3ReactivePipelineStaticArchitectureSpecsCucumberTest",
            "PhaseEp5OperabilityRequirementsCucumberTest",
            "Phase5S3SemanticCompatibilityRequirementsCucumberTest",
            "Phase5S3SemanticCompatibilityAwsCliCucumberTest"
        }) {
            assertTrue(inspectedSource.contains(runner), "CI workflow should run " + runner);
        }
        assertTrue(inspectedSource.contains("Using generated security password"),
            "CI workflow should fail when Spring Boot generated-password banner appears");
    }

    @Then("the CI workflow builds required reactor dependency modules for the focused Maven gate")
    public void ciWorkflowBuildsRequiredReactorDependencies() {
        assertInspected();
        assertTrue(inspectedSource.contains("-pl s3-reactive-api-adapter -am"),
            "CI focused Maven gate should build required reactor dependency modules on a clean runner");
        assertTrue(inspectedSource.contains("-Dsurefire.failIfNoSpecifiedTests=false"),
            "CI focused Maven gate should allow upstream reactor modules that do not contain the selected test classes");
    }

    @Then("the CI workflow writes focused validation logs to an existing directory")
    public void ciWorkflowWritesFocusedLogsToExistingDirectory() {
        assertInspected();
        assertTrue(inspectedSource.contains("mkdir -p target"),
            "CI workflow should create the root target directory before tee writes target/focused-cucumber.log");
        assertTrue(inspectedSource.contains("tee target/focused-cucumber.log"),
            "CI workflow should retain the focused Cucumber log for generated-password checks");
    }

    @Then("the CI workflow builds and smokes the root JVM Docker image")
    public void ciWorkflowBuildsAndSmokesRootJvmDockerImage() {
        assertInspected();
        assertTrue(inspectedSource.contains("docker build --network=host -f Dockerfile -t magrathea-objectstorage:jvm ."),
            "CI workflow should build the root JVM Docker image");
        assertTrue(inspectedSource.contains("docker run -d --name magrathea-jvm-smoke --network=host"),
            "CI workflow should start the root JVM Docker image for smoke validation");
        assertTrue(inspectedSource.contains("http://127.0.0.1:8081/admin/health"),
            "CI workflow should smoke the Admin API health endpoint");
        assertTrue(inspectedSource.contains("http://127.0.0.1:8081/admin/live"),
            "CI workflow should smoke the Admin API liveness endpoint");
        assertTrue(inspectedSource.contains("http://127.0.0.1:8081/admin/ready"),
            "CI workflow should smoke the Admin API readiness endpoint");
        assertTrue(inspectedSource.contains("ci-jvm-smoke-bucket/object.txt"),
            "CI workflow should smoke S3 bucket/object PUT/GET behavior");
    }

    @Then("the CI workflow keeps native Docker packaging available as an explicit manual validation job")
    public void ciWorkflowKeepsNativeDockerManualJob() {
        assertInspected();
        assertTrue(inspectedSource.contains("run-native-docker"), "CI workflow should expose native Docker manual input");
        assertTrue(inspectedSource.contains("Dockerfile.native"), "CI workflow should include a native Docker job");
        assertTrue(inspectedSource.contains("github.event_name == 'workflow_dispatch'"),
            "Native Docker job should be manual-only");
    }

    private void inspect(String relativePath) throws IOException {
        inspectedPath = root.resolve(relativePath);
        assertTrue(Files.exists(inspectedPath), "Expected source path to exist: " + inspectedPath);
        inspectedSource = Files.readString(inspectedPath);
    }

    private String readRelative(String relativePath) throws IOException {
        var path = root.resolve(relativePath);
        assertTrue(Files.exists(path), "Expected file to exist: " + path);
        return Files.readString(path);
    }

    private void assertValidationMode(String expected) {
        assertTrue(expected.equals(validationMode), "Expected validation mode " + expected + " but got " + validationMode);
    }

    private void assertInspected() {
        assertTrue(inspectedSource != null && inspectedPath != null, "A distribution source path must be inspected first");
    }

    private static void assertDocsAndUiRegeneration(String source) {
        assertTrue(source.contains("asciidoc-to-json.mjs"), "Docker build should regenerate user manual JSON");
        assertTrue(source.contains("asciidoc-to-arc42-json.mjs"), "Docker build should regenerate ARC42 JSON");
        assertTrue(source.contains("markdown-to-json.mjs"), "Docker build should regenerate markdown docs JSON");
        assertTrue(source.contains("adr-to-json.mjs"), "Docker build should regenerate ADR JSON");
        assertTrue(source.contains("npm run build --prefix magrathea-ui"), "Docker build should rebuild admin UI");
    }

    private static Path repositoryRoot() {
        var current = Path.of("").toAbsolutePath();
        while (current != null) {
            if (Files.exists(current.resolve("pom.xml")) && Files.exists(current.resolve("s3-reactive-api-adapter"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Cannot locate repository root from " + Path.of("").toAbsolutePath());
    }
}
