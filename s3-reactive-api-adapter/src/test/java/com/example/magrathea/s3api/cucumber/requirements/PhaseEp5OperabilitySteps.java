package com.example.magrathea.s3api.cucumber.requirements;

import com.example.magrathea.admin.web.AdminRouter;
import com.example.magrathea.storageengine.application.port.DiskSetCatalog;
import com.example.magrathea.storageengine.application.port.StorageDeviceCatalog;
import com.example.magrathea.storageengine.application.port.StoragePolicyCatalog;
import com.jayway.jsonpath.JsonPath;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.After;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class PhaseEp5OperabilitySteps {

    private final WebTestClient adminClient;
    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(3))
        .build();
    private WebTestClient activeClient;
    private EntityExchangeResult<String> response;
    private Path gracefulStorageRoot;
    private Path gracefulPoliciesDir;
    private Path gracefulDevicesDir;
    private Path gracefulDisksetsDir;
    private Path gracefulLog;
    private ManagedProcess gracefulProcess;
    private boolean gracefulProcessForced;

    public PhaseEp5OperabilitySteps(@Qualifier("adminWebTestClient") WebTestClient adminClient) {
        this.adminClient = adminClient;
        this.activeClient = adminClient;
    }

    @After
    public void stopManagedProcess() throws InterruptedException {
        stopGracefulProcessIfRunning();
    }

    @Given("the Admin API is configured with storage policy, storage device, and disk-set catalogs")
    public void adminApiIsConfiguredWithCatalogs() {
        activeClient = adminClient;
        adminClient.get().uri("/admin/storage-policies")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.storagePolicies[0].storageClassId").exists();
        adminClient.get().uri("/admin/storage-devices")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.storageDevices[0].id").exists();
        adminClient.get().uri("/admin/disk-sets")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.diskSets[0].name").exists();
    }

    @Given("the Admin API is missing storage policy, storage device, and disk-set catalogs")
    public void adminApiIsMissingStorageCatalogs() {
        AdminRouter router = new AdminRouter(
            emptyProvider(StoragePolicyCatalog.class),
            emptyProvider(StorageDeviceCatalog.class),
            emptyProvider(DiskSetCatalog.class));
        activeClient = WebTestClient.bindToRouterFunction(router.adminRoutes()).build();
    }

    @When("an Admin API client requests GET {string}")
    public void adminApiClientRequestsGet(String path) {
        response = activeClient.get().uri(path)
            .exchange()
            .expectBody(String.class)
            .returnResult();
    }

    @Then("the Admin API response status is {int}")
    public void adminApiResponseStatusIs(int expectedStatus) {
        assertThat(response).as("Admin API response must be captured").isNotNull();
        HttpStatusCode status = response.getStatus();
        assertThat(status.value()).isEqualTo(expectedStatus);
    }

    @Then("the Admin API response field {string} is {string}")
    public void adminApiResponseFieldIs(String field, String expectedValue) {
        assertThat(responseBody()).isNotBlank();
        String actual = JsonPath.read(responseBody(), "$." + field);
        assertThat(actual).isEqualTo(expectedValue);
    }

    @Then("the Admin API response has a link named {string} to {string}")
    public void adminApiResponseHasLinkNamedTo(String linkName, String href) {
        String actual = JsonPath.read(responseBody(), "$._links." + linkName + ".href");
        assertThat(actual).isEqualTo(href);
    }

    @Then("the Admin API readiness components are ready:")
    public void adminApiReadinessComponentsAreReady(DataTable table) {
        for (String component : table.asMaps().stream().map(row -> row.get("component")).toList()) {
            assertReadinessComponentStatus(component, "ready");
        }
    }

    @Then("the Admin API readiness components have status:")
    public void adminApiReadinessComponentsHaveStatus(DataTable table) {
        table.asMaps().forEach(row -> assertReadinessComponentStatus(row.get("component"), row.get("status")));
    }

    @Given("a storage-engine S3 process is running with graceful shutdown enabled and filesystem root {string}")
    public void storageEngineS3ProcessIsRunningWithGracefulShutdown(String root) throws IOException, InterruptedException {
        stopGracefulProcessIfRunning();
        gracefulStorageRoot = Path.of(root);
        deleteRecursively(gracefulStorageRoot);
        Files.createDirectories(gracefulStorageRoot);
        Path catalogRoot = Files.createTempDirectory("magrathea-ep5-graceful-catalog-");
        gracefulPoliciesDir = extractCatalogDir(catalogRoot, "storage-policies", List.of("minio-standard.yaml"));
        gracefulDevicesDir = extractCatalogDir(catalogRoot, "storage-devices", List.of("local-disk-0.yaml"));
        gracefulDisksetsDir = extractCatalogDir(catalogRoot, "disk-sets", List.of("default-diskset.yaml"));
        gracefulLog = Files.createTempFile("magrathea-ep5-graceful-shutdown-", ".log");
        gracefulProcess = startStorageEngineProcess(gracefulStorageRoot, gracefulLog);
        awaitReady(gracefulProcess);
        gracefulProcessForced = false;
    }

    @Given("bucket {string} contains object {string} with body {string}")
    public void bucketContainsObjectWithBody(String bucket, String key, String body) throws IOException, InterruptedException {
        requireGracefulProcess();
        send(gracefulProcess, "PUT", "/" + bucket, "");
        HttpResponse<String> putObject = send(gracefulProcess, "PUT", "/" + bucket + "/" + key, body);
        assertThat(putObject.statusCode()).isBetween(200, 299);
    }

    @When("operators send SIGTERM to the S3 process")
    public void operatorsSendSigtermToS3Process() {
        requireGracefulProcess();
        gracefulProcess.process().toHandle().destroy();
    }

    @Then("the process exits within {int} seconds without forced termination")
    public void processExitsWithinSecondsWithoutForcedTermination(int seconds) throws InterruptedException {
        requireGracefulProcess();
        boolean exited = gracefulProcess.process().waitFor(seconds, TimeUnit.SECONDS);
        if (!exited) {
            gracefulProcessForced = true;
            gracefulProcess.process().destroyForcibly();
        }
        assertThat(exited).as("process should exit after SIGTERM without destroyForcibly").isTrue();
        assertThat(gracefulProcessForced).isFalse();
    }

    @Then("the shutdown log must not contain Spring Boot's generated security password banner")
    public void shutdownLogMustNotContainGeneratedSecurityPasswordBanner() throws IOException {
        assertThat(gracefulLog).isNotNull();
        String log = Files.readString(gracefulLog);
        assertThat(log).doesNotContain("Using generated security password");
    }

    @When("recovery starts the S3 process again with the same filesystem root")
    public void recoveryStartsS3ProcessAgainWithSameFilesystemRoot() throws IOException, InterruptedException {
        assertThat(gracefulStorageRoot).as("graceful shutdown storage root").isNotNull();
        gracefulLog = Files.createTempFile("magrathea-ep5-graceful-recovery-", ".log");
        gracefulProcess = startStorageEngineProcess(gracefulStorageRoot, gracefulLog);
        awaitReady(gracefulProcess);
        gracefulProcessForced = false;
    }

    @Then("object {string} in bucket {string} can be read with body {string}")
    public void objectInBucketCanBeReadWithBody(String key, String bucket, String expectedBody) throws IOException, InterruptedException {
        requireGracefulProcess();
        HttpResponse<String> response = send(gracefulProcess, "GET", "/" + bucket + "/" + key, "");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo(expectedBody);
        stopGracefulProcessIfRunning();
    }

    private void assertReadinessComponentStatus(String component, String expectedStatus) {
        List<String> statuses = JsonPath.read(responseBody(), "$.components[?(@.name == '" + component + "')].status");
        assertThat(statuses)
            .as("readiness component %s", component)
            .containsExactly(expectedStatus);
    }

    private static <T> ObjectProvider<T> emptyProvider(Class<T> type) {
        return new StaticListableBeanFactory().getBeanProvider(type);
    }

    private ManagedProcess startStorageEngineProcess(Path storageRoot, Path logFile) throws IOException {
        int port = availablePort();
        Path java = Path.of(System.getProperty("java.home"), "bin", "java");
        String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        Process process = new ProcessBuilder(
            java.toString(),
            "-cp", classpath,
            RequirementsTestApp.class.getName(),
            "--spring.profiles.active=storage-engine",
            "--magrathea.object-store.backend=storage-engine",
            "--storage.engine.filesystem.root=" + storageRoot,
            "--storage.engine.policies.dir=" + gracefulPoliciesDir,
            "--storage.engine.devices.dir=" + gracefulDevicesDir,
            "--storage.engine.disksets.dir=" + gracefulDisksetsDir,
            "--server.port=" + port,
            "--server.shutdown=graceful",
            "--spring.lifecycle.timeout-per-shutdown-phase=10s",
            "--s3.security.enabled=false")
            .redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.appendTo(logFile.toFile()))
            .start();
        return new ManagedProcess(process, port);
    }

    private void awaitReady(ManagedProcess managedProcess) throws IOException, InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        AssertionError lastFailure = null;
        while (System.nanoTime() < deadline) {
            if (!managedProcess.process().isAlive()) {
                throw new AssertionError("Managed process exited before readiness. Log:\n" + readLog());
            }
            try {
                HttpResponse<String> response = send(managedProcess, "GET", "/admin/ready", "");
                if (response.statusCode() == 200 && response.body().contains("\"status\":\"ready\"")) {
                    return;
                }
                lastFailure = new AssertionError("Readiness status " + response.statusCode() + " body " + response.body());
            } catch (IOException e) {
                lastFailure = new AssertionError(e);
            }
            Thread.sleep(250);
        }
        throw new AssertionError("Managed process did not become ready. Last failure: " + lastFailure + "\nLog:\n" + readLog());
    }

    private HttpResponse<String> send(ManagedProcess managedProcess, String method, String path, String body)
            throws IOException, InterruptedException {
        HttpRequest.BodyPublisher publisher = body == null || body.isEmpty()
            ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + managedProcess.port() + path))
            .timeout(Duration.ofSeconds(10))
            .method(method, publisher)
            .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private void requireGracefulProcess() {
        assertThat(gracefulProcess).as("managed graceful-shutdown process").isNotNull();
    }

    private void stopGracefulProcessIfRunning() throws InterruptedException {
        if (gracefulProcess != null && gracefulProcess.process().isAlive()) {
            gracefulProcess.process().destroy();
            if (!gracefulProcess.process().waitFor(5, TimeUnit.SECONDS)) {
                gracefulProcess.process().destroyForcibly();
                gracefulProcess.process().waitFor(5, TimeUnit.SECONDS);
            }
        }
        gracefulProcess = null;
    }

    private String readLog() throws IOException {
        return gracefulLog == null || !Files.exists(gracefulLog) ? "" : Files.readString(gracefulLog);
    }

    private static int availablePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            socket.setReuseAddress(false);
            return socket.getLocalPort();
        }
    }

    private static Path extractCatalogDir(Path catalogRoot, String classpathDir, List<String> fileNames) {
        try {
            Path dir = catalogRoot.resolve(classpathDir);
            Files.createDirectories(dir);
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            for (String fileName : fileNames) {
                try (var input = classLoader.getResourceAsStream(classpathDir + "/" + fileName)) {
                    if (input == null) {
                        throw new IllegalStateException("Missing classpath resource " + classpathDir + "/" + fileName);
                    }
                    Files.copy(input, dir.resolve(fileName));
                }
            }
            return dir;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to extract catalog directory " + classpathDir, e);
        }
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try (var stream = Files.walk(path)) {
            for (Path entry : stream.sorted((left, right) -> right.compareTo(left)).toList()) {
                Files.deleteIfExists(entry);
            }
        }
    }

    private String responseBody() {
        assertThat(response).as("Admin API response must be captured").isNotNull();
        assertThat(response.getResponseBody()).as("Admin API response body").isNotNull();
        return response.getResponseBody();
    }

    private record ManagedProcess(Process process, int port) {
    }
}
