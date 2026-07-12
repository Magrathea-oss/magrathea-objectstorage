package com.example.magrathea.s3api.cucumber.requirements;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class PhaseEp5JvmContainerReplacementSteps {

    private final Path projectRoot = locateProjectRoot();
    private String expectedVersion;
    private String expectedRevision;
    private String expectedSourceUrl;
    private String validationOutput;

    @Given("Docker is available for canonical JVM container replacement validation")
    public void dockerIsAvailable() throws IOException, InterruptedException {
        CommandResult result = command(projectRoot, 30, "docker", "info");
        assertThat(result.exitCode()).as("docker info output:%n%s", result.output()).isZero();
    }

    @Given("the expected image source identity is release version {string} at the current Git revision")
    public void expectedImageIdentity(String version) throws IOException, InterruptedException {
        expectedVersion = version;
        expectedRevision = command(projectRoot, 10, "git", "rev-parse", "HEAD").output().trim();
        String remote = command(projectRoot, 10, "git", "remote", "get-url", "origin").output().trim();
        expectedSourceUrl = normalizeSourceUrl(remote);
        assertThat(expectedRevision).matches("[0-9a-f]{40}");
        assertThat(expectedSourceUrl).startsWith("https://");
    }

    @When("operators run the deterministic JVM container replacement validation with persistent volume {string}")
    public void runContainerReplacementValidation(String mountPath) throws IOException, InterruptedException {
        assertThat(mountPath).isEqualTo("/app/data");
        Path script = projectRoot.resolve("scripts/validate-jvm-container-replacement.sh");
        assertThat(script).isRegularFile();
        Path output = Files.createTempFile("req-ops-025-", ".log");
        ProcessBuilder builder = new ProcessBuilder("bash", script.toString())
            .directory(projectRoot.toFile())
            .redirectErrorStream(true)
            .redirectOutput(output.toFile());
        builder.environment().put("REQ_OPS_025_VERSION", expectedVersion);
        builder.environment().put("REQ_OPS_025_REVISION", expectedRevision);
        builder.environment().put("REQ_OPS_025_SOURCE_URL", expectedSourceUrl);
        Process process = builder.start();
        boolean exited = process.waitFor(20, TimeUnit.MINUTES);
        if (!exited) {
            process.destroyForcibly();
        }
        validationOutput = Files.readString(output, StandardCharsets.UTF_8);
        Files.deleteIfExists(output);
        assertThat(exited).as("container replacement validation completes within 20 minutes").isTrue();
        assertThat(process.exitValue())
            .as("container replacement validation output:%n%s", validationOutput)
            .isZero();
        assertThat(validationOutput).contains("REQ-OPS-025 validated");
    }

    @Then("the canonical JVM image runs as a non-root user")
    public void imageRunsNonRoot() {
        assertThat(validationOutput).contains("runtime-user=non-root");
    }

    @Then("the first container accepts S3 bucket {string} and object {string} with exact body {string}")
    public void firstContainerAcceptsS3Writes(String bucket, String key, String body) {
        int byteCount = body.getBytes(StandardCharsets.UTF_8).length;
        assertThat(validationOutput).contains("s3-object=" + bucket + "/" + key + " exact-bytes=" + byteCount);
    }

    @Then("the first container exits from SIGTERM without forced removal")
    public void containerStopsFromSigterm() {
        assertThat(validationOutput).contains("shutdown-signal=SIGTERM exit-code=143");
    }

    @Then("a different container starts from the same image and persistent volume")
    public void replacementUsesSameImageAndVolume() {
        assertThat(validationOutput).contains("persistent-volume=", "container-recreated=true image-id=sha256:");
    }

    @Then("Admin endpoints {string} and {string} report healthy operation after replacement")
    public void adminEndpointsAreHealthy(String livePath, String readyPath) {
        assertThat(livePath).isEqualTo("/admin/live");
        assertThat(readyPath).isEqualTo("/admin/ready");
        assertThat(validationOutput).contains("admin-live=healthy admin-ready=ready");
    }

    @Then("the replacement container returns the exact persisted object bytes")
    public void replacementReturnsExactBytes() {
        assertThat(validationOutput).contains("REQ-OPS-025 validated", " exact-bytes=");
    }

    @Then("the running image reports release version {string}, the expected source revision, and the expected source URL")
    public void runningImageReportsIdentity(String version) {
        assertThat(validationOutput).contains(
            "image-version=" + version,
            "image-revision=" + expectedRevision,
            "image-source=" + expectedSourceUrl);
    }

    private static CommandResult command(Path directory, int timeoutSeconds, String... command)
            throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command)
            .directory(directory.toFile())
            .redirectErrorStream(true)
            .start();
        boolean exited = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        if (!exited) {
            process.destroyForcibly();
            throw new AssertionError(String.join(" ", command) + " timed out");
        }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return new CommandResult(process.exitValue(), output);
    }

    private static String normalizeSourceUrl(String remote) {
        String normalized = remote.endsWith(".git") ? remote.substring(0, remote.length() - 4) : remote;
        if (normalized.startsWith("git@github.com:")) {
            return "https://github.com/" + normalized.substring("git@github.com:".length());
        }
        return normalized;
    }

    private static Path locateProjectRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("pom.xml"))
                    && Files.isRegularFile(current.resolve("scripts/validate-jvm-container-replacement.sh"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Could not locate repository root");
    }

    private record CommandResult(int exitCode, String output) {
    }
}
