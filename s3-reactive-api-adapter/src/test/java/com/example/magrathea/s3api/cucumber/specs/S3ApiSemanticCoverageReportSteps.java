package com.example.magrathea.s3api.cucumber.specs;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class S3ApiSemanticCoverageReportSteps {

    private Path projectRoot;
    private String report;
    private String generatorOutput;

    @Given("the canonical S3 API inventory contains 111 distinct operations")
    public void canonicalInventoryContains111DistinctOperations() throws IOException {
        projectRoot = locateProjectRoot();
        String generator = Files.readString(projectRoot.resolve("scripts/generate-s3-api-coverage.py"));
        int inventoryStart = generator.indexOf("OPERATIONS = \"\"\"");
        int inventoryEnd = generator.indexOf("\"\"\".splitlines()", inventoryStart);
        assertThat(inventoryStart).isGreaterThanOrEqualTo(0);
        assertThat(inventoryEnd).isGreaterThan(inventoryStart);
        String inventory = generator.substring(inventoryStart + "OPERATIONS = \"\"\"".length(), inventoryEnd);
        assertThat(inventory.lines().filter(line -> !line.isBlank()).distinct()).hasSize(111);
    }

    @When("the semantic coverage report is generated from router mappings and executable requirement tags")
    public void semanticCoverageReportIsGeneratedFromRouterAndRequirements()
            throws IOException, InterruptedException {
        Process process = new ProcessBuilder("python3", "scripts/generate-s3-api-coverage.py", "--check")
            .directory(projectRoot.toFile())
            .redirectErrorStream(true)
            .start();
        boolean exited = process.waitFor(30, TimeUnit.SECONDS);
        assertThat(exited).as("coverage generator completes").isTrue();
        generatorOutput = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(process.exitValue()).as(generatorOutput).isZero();
        report = Files.readString(projectRoot.resolve("docs/api-coverage.md"));
    }

    @Then("every official operation has one matrix row")
    public void everyOfficialOperationHasOneMatrixRow() {
        String matrix = report.substring(report.indexOf("## Operation matrix"), report.indexOf("## Guardrails"));
        long rows = matrix.lines().filter(line -> line.matches("\\| `[^`]+` \\| (Yes|No) \\|.*")).count();
        assertThat(rows).isEqualTo(111);
    }

    @Then("the report distinguishes mapped routes from implemented-and-validated semantic evidence")
    public void reportDistinguishesRoutesFromSemanticEvidence() {
        assertThat(report).contains("Operations with a mapped router handler:");
        assertThat(report).contains("Implemented-and-validated with explicit operation-linked evidence:");
        assertThat(report).contains("A route or handler never upgrades semantic status.");
    }

    @Then("operations without explicit implemented-and-validated evidence remain pending")
    public void operationsWithoutExplicitEvidenceRemainPending() {
        assertThat(report).contains("`pending-evidence`");
        assertThat(report).contains("Not yet eligible for a 100% completion claim:");
    }

    @Then("the committed S3 API coverage report is fresh")
    public void committedCoverageReportIsFresh() {
        assertThat(generatorOutput).contains("docs/api-coverage.md is fresh");
    }

    private static Path locateProjectRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        if (Files.isRegularFile(current.resolve("scripts/generate-s3-api-coverage.py"))) {
            return current;
        }
        Path parent = current.getParent();
        assertThat(parent).isNotNull();
        assertThat(parent.resolve("scripts/generate-s3-api-coverage.py")).isRegularFile();
        return parent;
    }
}
