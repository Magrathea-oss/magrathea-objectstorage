package com.example.magrathea.s3api.releasepublication;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class PhaseEp5LocalReleasePublicationSteps {
    private final Path projectRoot = locateProjectRoot();
    private String output;

    @Given("the canonical JVM image carries version {string} and the current source revision")
    public void canonicalImageIdentity(String version) throws Exception {
        assertThat(version).isEqualTo("0.1.0");
        run(List.of("bash", "scripts/validate-jvm-container-replacement.sh"),
                true, "REQ_OPS_025_VERSION", version);
    }

    @When("the local release rehearsal publishes version, minor-line, and commit-SHA tags")
    public void localReleaseRehearsalPublishesTags() throws Exception {
        output = run(List.of("bash", "scripts/validate-local-release-publication.sh"), false, null, null);
    }

    @Then("all three registry tags resolve to one non-empty immutable digest")
    public void tagsResolveToOneDigest() {
        List<String> digestLines = output.lines().filter(line -> line.contains("@sha256:")).toList();
        assertThat(digestLines).hasSize(3);
        assertThat(digestLines.stream().map(line -> line.substring(line.indexOf("@sha256:"))).distinct())
                .hasSize(1);
        assertThat(output).contains(":0.1.0@sha256:", ":0.1@sha256:", ":sha-");
    }

    @Then("a repeated publication attempt is refused rather than overwriting a tag")
    public void repeatedPublicationIsRefused() {
        assertThat(output).contains(
                "Immutable overwrite refusal validated for all release tags",
                "Local immutable release publication validated for 0.1.0");
    }

    private String run(List<String> command, boolean tolerateExistingImage,
            String environmentName, String environmentValue) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(command)
                .directory(projectRoot.toFile())
                .redirectErrorStream(true);
        if (environmentName != null) {
            builder.environment().put(environmentName, environmentValue);
        }
        Process process = builder.start();
        String result = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exit = process.waitFor();
        if (exit != 0 && !tolerateExistingImage) {
            throw new AssertionError("Command failed with exit " + exit + ":\n" + result);
        }
        assertThat(exit).as(result).isZero();
        return result;
    }

    private static Path locateProjectRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("pom.xml"))
                    && Files.isRegularFile(current.resolve("scripts/validate-local-release-publication.sh"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Could not locate repository root");
    }
}
