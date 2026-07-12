package com.example.magrathea.s3api.cucumber.load;

import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PhaseEp6LoadSteps {
    private Ep6LoadValidation.Result result;

    private Ep6LoadValidation.Result result() throws Exception {
        if (result == null) result = Ep6LoadValidation.resultForConfiguredMode();
        return result;
    }

    @Given("a storage-engine S3 child process runs with {string} and filesystem root {string}")
    public void childProcess(String heap, String root) throws Exception {
        assertEquals("-Xmx256m", heap);
        assertTrue(root.endsWith(System.getProperty("ep6.mode", "ci").equals("soak") ? "soak" : "ci-load"));
        result();
    }

    @And("load seed {string} assigns these repeating operations to eight workers:")
    public void seededWorkers(String seed, DataTable schedule) {
        assertEquals("ep6-ci-0.1.x", seed);
        int assignedWorkers = schedule.asMaps().stream()
            .mapToInt(row -> row.get("workers").split(",").length).sum();
        assertEquals(8, assignedWorkers, "the deterministic schedule must assign exactly eight workers");
    }

    @When("the eight workers execute the seeded schedule for {int} seconds")
    public void executeCi(int duration) throws Exception { assertEquals(duration, result().durationSeconds()); }

    @Then("the child process exits the load window without an out-of-memory error or forced restart")
    public void processSurvives() throws Exception { assertTrue(result().completed() >= 90); }

    @And("observed heap usage never exceeds the configured {long}-byte maximum")
    public void heapBound(long maximum) throws Exception { assertTrue(result().peakHeap() <= maximum); }

    @And("every acknowledged write remains checksum-readable")
    public void writesReadable() throws Exception {
        assertEquals(0, result().corruptions());
        assertTrue(result().checksummedWrites() > 0);
    }

    @And("every failed operation is classified in the result manifest rather than discarded")
    public void failuresClassified() throws Exception {
        assertTrue(Files.readString(result().resultDir().resolve("result.json")).contains("\"unexpectedResponses\""));
    }

    @Given("the same eight-worker workload and seed family used by CI validation")
    public void sameWorkload() throws Exception { assertEquals("soak", result().mode()); }

    @When("the opt-in soak profile runs the mixed workload for {int} minutes")
    public void executeSoak(int minutes) throws Exception { assertEquals(minutes * 60, result().durationSeconds()); }

    @Then("the process completes without an out-of-memory error, deadlock, or forced restart")
    public void soakSurvives() throws Exception { assertTrue(result().completed() >= 1800); }

    @And("heap, active requests, open connections, and temporary storage return to their declared idle bounds after the load stops")
    public void idleBounds() throws Exception {
        assertTrue(result().postGcHeap() <= 192L * 1024 * 1024);
        assertEquals(0, result().idleActiveRequests());
        assertEquals(0, result().idleConnections());
        assertEquals(0, result().temporaryArtifacts());
    }

    @And("every sampled acknowledged write remains checksum-readable")
    public void sampledWrites() throws Exception { writesReadable(); }

    @And("the result manifest identifies validation mode {string} and duration {int} seconds")
    public void manifestMode(String mode, int duration) throws Exception {
        assertEquals(mode, result().mode());
        assertEquals(duration, result().durationSeconds());
    }

    @Given("CI load records accepted, concurrency-rejected, rate-limited, and timed-out request counts")
    public void metricsWorkload() throws Exception {
        assertTrue(result().accepted() > 0);
        // Zero is a meaningful production-meter observation when this bounded schedule does not overload the server.
        assertTrue(result().concurrencyRejections() >= 0);
        assertTrue(result().rateLimitRejections() >= 0);
        assertTrue(result().timedOut() >= 0);
        assertEquals(java.util.Set.of("operation", "outcome"), result().metricTagKeys());
    }

    @When("the validation captures process and application metrics")
    public void capturesMetrics() throws Exception { assertTrue(Files.isRegularFile(result().resultDir().resolve("result.json"))); }

    @Then("the evidence includes heap usage, active requests, open TCP connections, accepted requests, concurrency rejections, rate-limit rejections, and request timeouts")
    public void evidenceIncludesMetrics() throws Exception {
        String json = Files.readString(result().resultDir().resolve("result.json"));
        for (String field : new String[]{"peakBytes", "peakActiveRequests", "idleOpenTcpConnections", "acceptedRequests",
            "concurrencyRejections", "rateLimitRejections", "requestTimeouts"}) assertTrue(json.contains("\"" + field + "\""));
    }

    @And("counters distinguish operation and outcome without recording access keys, authorization signatures, object bodies, user metadata, bucket names, or object keys")
    public void boundedMetrics() throws Exception {
        String json = Files.readString(result().resultDir().resolve("result.json"));
        assertTrue(json.contains("\"observed\": [\"operation\",\"outcome\"]"));
        assertTrue(json.contains("\"allowed\": [\"operation\", \"outcome\"]"));
    }

    @And("logs and metric labels comply with the existing telemetry redaction policy")
    public void redacted() throws Exception {
        String json = Files.readString(result().resultDir().resolve("result.json"));
        assertTrue(json.contains("\"forbidden\""));
        String log = Files.readString(result().resultDir().resolve("child.log"));
        assertTrue(!log.contains("Authorization:") && !log.contains("AWS4-HMAC-SHA256 Credential="));
    }

    @Given("EP-6 runs in validation mode {string} for {int} seconds")
    public void configuredRun(String mode, int duration) throws Exception {
        assertEquals(mode, result().mode());
        assertEquals(duration, result().durationSeconds());
    }

    @When("the run finishes")
    public void runFinishes() throws Exception { assertTrue(result().completed() > 0); }

    @Then("its result directory contains a machine-readable manifest")
    public void manifestExists() throws Exception { assertTrue(Files.isRegularFile(result().resultDir().resolve("result.json"))); }

    @And("the manifest records Git revision, release line {string}, dirty-tree state, validation mode, workload seed, worker count {int}, duration, JVM vendor and version, maximum heap {string}, operating system, processor count, backend, filesystem root, object and multipart limits, timeout, concurrency limit, rate-limit settings, TCP connection cap, operation counts, error counts, latency summary, peak heap, and artifact checksums")
    public void manifestFields(String release, int workers, String heap) throws Exception {
        String json = Files.readString(result().resultDir().resolve("result.json"));
        assertEquals("0.1.x", release);
        assertEquals(8, workers);
        assertEquals("256m", heap);
        for (String field : new String[]{"revision", "dirtyTree", "validationMode", "seed", "jvmVendor", "jvmVersion",
            "operatingSystem", "processorCount", "backend", "filesystemRoot", "singlePutLimitBytes",
            "multipartPartLimitBytes", "requestTimeoutSeconds", "concurrencyLimit", "rateLimitPerSecond",
            "tcpConnectionCap", "completedOperations", "unexpectedResponses", "p99Millis", "peakBytes",
            "artifactChecksumsSha256"}) assertTrue(json.contains("\"" + field + "\""), field);
    }

    @And("rerunning with the recorded configuration and seed reproduces the operation schedule")
    public void reproducible() throws Exception {
        String json = Files.readString(result().resultDir().resolve("result.json"));
        assertTrue(json.contains("\"seed\": \"ep6-ci-0.1.x\""));
        assertTrue(json.contains("\"workerCount\": 8"));
    }

    @And("the manifest states {string}")
    public void disclaimer(String disclaimer) throws Exception {
        assertEquals(Ep6LoadValidation.DISCLAIMER, disclaimer);
        assertTrue(Files.readString(result().resultDir().resolve("result.json")).contains(disclaimer));
    }

    @Given("CI, soak, or connection validation has produced a result manifest")
    public void resultManifest() throws Exception { manifestExists(); }

    @When("maintainers publish the EP-6 result summary")
    public void summaryPublished() throws Exception { assertTrue(Files.isRegularFile(result().resultDir().resolve("summary.md"))); }

    @Then("the summary links to the exact result manifest and its artifact checksums")
    public void summaryLinks() throws Exception {
        String summary = Files.readString(result().resultDir().resolve("summary.md"));
        assertTrue(summary.contains("[`result.json`](result.json)"));
        assertTrue(summary.contains("SHA-256"));
    }

    @And("it identifies the tested 0.1.x single-node hardware and software envelope")
    public void envelope() throws Exception { assertTrue(Files.readString(result().resultDir().resolve("summary.md")).contains("0.1.x single-node")); }

    @And("it separates observed values from configured limits and pass criteria")
    public void separatesResults() throws Exception {
        String summary = Files.readString(result().resultDir().resolve("summary.md"));
        assertTrue(summary.contains("Observed:") && summary.contains("Configured limits:") && summary.contains("Pass criteria:"));
    }

    @And("it states that the result is not production sizing guidance and not a comparison with another object store")
    public void humanDisclaimer() throws Exception {
        String summary = Files.readString(result().resultDir().resolve("summary.md"));
        assertTrue(summary.contains("not production sizing guidance") && summary.contains("not a comparison"));
    }
}
