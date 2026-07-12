package com.example.magrathea.s3api.cucumber.ep7;

import com.example.magrathea.admin.application.port.AdminBackendStatusProvider;
import com.example.magrathea.admin.application.port.AdminOperationalReportProvider;
import com.example.magrathea.admin.infrastructure.ConfiguredAdminBackendStatusProvider;
import com.example.magrathea.admin.web.AdminRouter;
import com.example.magrathea.storageengine.application.port.BucketCapacityPort;
import com.example.magrathea.storageengine.application.port.DiskSetCatalog;
import com.example.magrathea.storageengine.application.port.StorageDeviceCatalog;
import com.example.magrathea.storageengine.application.port.StoragePolicyCatalog;
import com.example.magrathea.storageengine.infrastructure.filesystem.FileSystemBucketCapacityStore;
import com.example.magrathea.storageengine.infrastructure.yaml.YamlDiskSetCatalog;
import com.example.magrathea.storageengine.infrastructure.yaml.YamlStorageDeviceCatalog;
import com.example.magrathea.storageengine.infrastructure.yaml.YamlStoragePolicyCatalog;
import com.jayway.jsonpath.JsonPath;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.http.MediaType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

public class PhaseEp7AdminApiSteps {

    private static final Path CONFIG_ROOT = Path.of("target/ep7-admin-api/config");
    private static final Path POLICY_DIR = CONFIG_ROOT.resolve("storage-policies");
    private static final Path DEVICE_DIR = CONFIG_ROOT.resolve("storage-devices");
    private static final Path DISK_SET_DIR = CONFIG_ROOT.resolve("disk-sets");
    private static final Path STORAGE_ROOT = Path.of("target/ep7-admin-api/storage-engine");

    private WebTestClient client;
    private EntityExchangeResult<byte[]> response;
    private YamlStoragePolicyCatalog policies;
    private YamlStorageDeviceCatalog devices;
    private YamlDiskSetCatalog diskSets;
    private BucketCapacityPort capacity;
    private String profile = "storage-engine";
    private String backendProperty = "storage-engine";

    @Given("the Admin API runs with profile {string}")
    public void adminApiRunsWithProfile(String value) {
        profile = value;
    }

    @Given("property {string} selects backend {string}")
    public void propertySelectsBackend(String property, String backend) {
        assertThat(property).isEqualTo(ConfiguredAdminBackendStatusProvider.BACKEND_PROPERTY);
        backendProperty = backend;
    }

    @Given("the YAML catalogs and storage root are configured as follows:")
    public void yamlCatalogsAndStorageRootAreConfigured(DataTable expected) {
        prepareRealCatalogs();
        Map<String, Integer> counts = expected.asMaps().stream().collect(Collectors.toMap(
            row -> row.get("catalog"), row -> Integer.parseInt(row.get("item count"))));
        assertThat(policies.findAll().count().block()).isEqualTo(counts.get("policies").longValue());
        assertThat(devices.findAll().count().block()).isEqualTo(counts.get("devices").longValue());
        assertThat(diskSets.findAll().count().block()).isEqualTo(counts.get("diskSets").longValue());
        useConfiguredRouter();
    }

    @Given("filesystem root {string} is available")
    public void filesystemRootIsAvailable(String root) {
        assertThat(root).isEqualTo(STORAGE_ROOT.toString());
        try {
            Files.createDirectories(STORAGE_ROOT);
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
        useConfiguredRouter();
    }

    @Given("no latest recovery summary provider is configured")
    public void noLatestRecoverySummaryProviderIsConfigured() {
        useConfiguredRouter();
    }

    @Given("no real {string} report provider is configured")
    public void noRealReportProviderIsConfigured(String report) {
        assertThat(report).isIn("recovery", "garbage-collection", "scrub", "audit", "metrics", "traces");
        client = client(null, null, null, null, null);
    }

    @Given("the YAML policy catalog contains {string} with erasure coding {int} data blocks and {int} parity blocks and replication factor {int}")
    public void yamlPolicyCatalogContains(String id, int data, int parity, int replication) {
        prepareRealCatalogs();
        var policy = policies.findBy(com.example.magrathea.storageengine.domain.valueobject.StorageClassId.of(id)).block();
        assertThat(policy).isNotNull();
        assertThat(policy.erasureCoding()).hasValueSatisfying(ec -> {
            assertThat(ec.dataBlocks()).isEqualTo(data);
            assertThat(ec.parityBlocks()).isEqualTo(parity);
        });
        assertThat(policy.replication().factor()).isEqualTo(replication);
        useConfiguredRouter();
    }

    @Given("the storage-device catalog contains device {string} at {string} with health {string}")
    public void storageDeviceCatalogContains(String id, String path, String health) {
        prepareRealCatalogs();
        var device = devices.findById(com.example.magrathea.storageengine.domain.valueobject.StorageDeviceId.of(id)).block();
        assertThat(device).isNotNull();
        assertThat(device.storagePath()).isEqualTo(path);
        assertThat(device.health().name()).isEqualTo(health);
        useConfiguredRouter();
    }

    @Given("device {string} has total capacity {long}, available capacity {long}, read eligibility {word}, and write eligibility {word}")
    public void deviceHasCapacityAndEligibility(String id, long total, long available, String read, String write) {
        var device = devices.findById(com.example.magrathea.storageengine.domain.valueobject.StorageDeviceId.of(id)).block();
        assertThat(device.totalCapacityBytes()).isEqualTo(total);
        assertThat(device.availableCapacityBytes()).isEqualTo(available);
        assertThat(device.isReadEligible()).isEqualTo(Boolean.parseBoolean(read));
        assertThat(device.isWriteEligible()).isEqualTo(Boolean.parseBoolean(write));
    }

    @Given("disk-set {string} has failure domain {string} and members {string} and {string}")
    public void diskSetHasMembers(String id, String domain, String first, String second) {
        var diskSet = diskSets.findById(id).block();
        assertThat(diskSet).isNotNull();
        assertThat(diskSet.failureDomain().name()).isEqualTo(domain);
        assertThat(diskSet.devices()).extracting(value -> value.value()).containsExactly(first, second);
    }

    @Given("no {string} catalog provider is configured")
    public void noCatalogProviderIsConfigured(String catalog) {
        prepareRealCatalogs();
        StoragePolicyCatalog policyProvider = "storage-policy-catalog".equals(catalog) ? null : policies;
        StorageDeviceCatalog deviceProvider = "storage-device-catalog".equals(catalog) ? null : devices;
        DiskSetCatalog diskSetProvider = "disk-set-catalog".equals(catalog) ? null : diskSets;
        client = client(policyProvider, deviceProvider, diskSetProvider, null, null);
    }

    @Given("the policy catalog contains only storage class {string}")
    public void policyCatalogContainsOnly(String storageClass) {
        preparePolicyOnly(storageClass);
        useConfiguredRouter();
    }

    @Given("the Admin API uses a YAML-backed read-only storage-policy catalog")
    public void adminApiUsesYamlBackedCatalog() {
        prepareRealCatalogs();
        useConfiguredRouter();
    }

    @Given("bucket {string} has used bytes {long}, reserved bytes {long}, quota bytes {long}, {long} rejected reservations, and last rejected bytes {long}")
    public void bucketHasCapacity(String bucket, long used, long reserved, long quota, long rejected, long lastRejected) {
        resetDirectory(STORAGE_ROOT);
        FileSystemBucketCapacityStore store = new FileSystemBucketCapacityStore(STORAGE_ROOT);
        store.configureQuota(bucket, used).block();
        var committed = store.reserve(bucket, "fixture/committed.bin", used, 0).block();
        store.commit(committed, used).block();
        for (int index = 0; index < rejected; index++) {
            try {
                store.reserve(bucket, "fixture/rejected-" + index, lastRejected, 0).block();
            } catch (RuntimeException expected) {
                // The real capacity provider records each expected quota rejection.
            }
        }
        store.configureQuota(bucket, quota).block();
        store.reserve(bucket, "fixture/in-flight.bin", reserved, 0).block();
        capacity = store;
        var actual = store.capacity(bucket).block();
        assertThat(actual.usedBytes()).isEqualTo(used);
        assertThat(actual.reservedBytes()).isEqualTo(reserved);
        assertThat(actual.quotaBytes()).isEqualTo(quota);
        assertThat(actual.rejectedReservations()).isEqualTo(rejected);
        assertThat(actual.lastRejectedBytes()).isEqualTo(lastRejected);
        client = client(null, null, null, capacity, null);
    }

    @Given("the production AdminRouter is the Admin Control Plane route source")
    public void productionAdminRouterIsRouteSource() {
        assertThat(AdminRouter.routeInventory()).isNotEmpty();
    }

    @When("an Admin API client requests GET {string}")
    @When("the client requests GET {string}")
    public void requestGet(String path) {
        response = requireClient().get().uri(path).exchange().expectBody().returnResult();
    }

    @When("an Admin API client posts this proposal to {string}:")
    public void postProposal(String path, String body) {
        response = requireClient().post().uri(path).contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body).exchange().expectBody().returnResult();
    }

    @When("^an Admin API client requests (POST|PUT|DELETE) \"([^\"]+)\"$")
    public void requestMethod(String method, String path) {
        var request = requireClient().method(org.springframework.http.HttpMethod.valueOf(method)).uri(path);
        if (Set.of("POST", "PUT").contains(method)) {
            request.contentType(MediaType.APPLICATION_JSON).bodyValue("{}");
        }
        response = request.exchange().expectBody().returnResult();
    }

    @When("the client requests PUT {string} with quota bytes {long}")
    public void putQuota(String path, long quota) {
        response = requireClient().put().uri(path).contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of("quotaBytes", quota)).exchange().expectBody().returnResult();
    }

    @When("the Admin API contract runner inventories its HTTP methods and path predicates")
    public void inventoryRoutes() {
        assertThat(AdminRouter.routeInventory()).hasSize(19);
    }

    @Then("the Admin API response status is {int} with content type {string}")
    public void responseStatusAndContentType(int status, String contentType) {
        assertThat(response.getStatus().value()).isEqualTo(status);
        assertThat(response.getResponseHeaders().getContentType()).isEqualTo(MediaType.parseMediaType(contentType));
    }

    @Then("the response status is {int} with content type {string}")
    public void responseStatusAndType(int status, String contentType) {
        responseStatusAndContentType(status, contentType);
    }

    @Then("the response status is {int} and preserves the configured path, capacities, health, and eligibility")
    public void deviceResponsePreservesValues(int status) {
        assertThat(response.getStatus().value()).isEqualTo(status);
        assertJson("storagePath", "/data/node-1/disk-0");
        assertJson("totalCapacityBytes", "107374182400");
        assertJson("availableCapacityBytes", "26843545600");
        assertJson("health", "DEGRADED");
        assertJson("readEligible", "true");
        assertJson("writeEligible", "false");
    }

    @Then("the response status is {int} and preserves failure domain {string} and both member identifiers")
    public void diskSetResponsePreservesValues(int status, String domain) {
        assertThat(response.getStatus().value()).isEqualTo(status);
        assertJson("failureDomain", domain);
        assertThat((List<String>) JsonPath.read(body(), "$.devices"))
            .containsExactly("node-1-disk-0", "node-2-disk-0");
    }

    @Then("the response status is {int} and quota bytes are {long}")
    public void responseStatusAndQuota(int status, long quota) {
        assertThat(response.getStatus().value()).isEqualTo(status);
        assertJson("quotaBytes", Long.toString(quota));
    }

    @Then("the response contains these backend-status fields:")
    public void responseContainsBackendStatusFields(DataTable table) {
        table.asMaps().forEach(row -> assertJson(row.get("JSON field"), row.get("value")));
    }

    @Then("storage root {string} reports availability {string}")
    public void storageRootReportsAvailability(String root, String availability) {
        assertJson("storageRoots['" + root + "'].availability", availability);
    }

    @Then("the response invents no recovery scan or finding counts")
    public void noInventedRecoveryCounts() {
        Map<String, Object> recovery = JsonPath.read(body(), "$.recoverySummary");
        assertThat(recovery).containsOnly(Map.entry("availability", "not-configured"));
    }

    @Then("the response error code is {string}")
    public void responseErrorCode(String code) {
        assertJson("error.code", code);
    }

    @Then("the response error path is {string}")
    public void responseErrorPath(String path) {
        assertJson("error.path", path);
    }

    @Then("response field {string} is {string}")
    public void responseFieldIs(String field, String value) {
        assertJson(field, value);
    }

    @Then("the response contains no sample records, generated values, filesystem inference, or healthy default")
    public void unavailableResponseContainsNoInventedEvidence() {
        assertThat(body()).doesNotContain("sample", "generated", "filesystem", "healthy");
        assertThat((Map<String, Object>) JsonPath.read(body(), "$.error.details"))
            .containsOnlyKeys("reportType", "availability");
    }

    @Then("the response count matches the number of configured policies")
    public void policyCountMatchesCatalog() {
        assertThat(((Number) JsonPath.read(body(), "$.count")).longValue())
            .isEqualTo(policies.findAll().count().block());
    }

    @Then("policy {string} reports erasure coding {int} data blocks and {int} parity blocks and replication factor {int}")
    public void policyReportsPipeline(String id, int data, int parity, int replication) {
        List<Map<String, Object>> matches = JsonPath.read(body(),
            "$.storagePolicies[?(@.storageClassId == '" + id + "')]");
        assertThat(matches).singleElement().satisfies(item -> {
            assertThat(JsonPath.read(item, "$.erasureCoding.dataBlocks").toString()).isEqualTo(Integer.toString(data));
            assertThat(JsonPath.read(item, "$.erasureCoding.parityBlocks").toString()).isEqualTo(Integer.toString(parity));
            assertThat(JsonPath.read(item, "$.replication.factor").toString()).isEqualTo(Integer.toString(replication));
        });
    }

    @Then("policy {string} omits deduplication, compression, and encryption stages that are not configured")
    public void policyOmitsUnconfiguredStages(String id) {
        Map<String, Object> item = ((List<Map<String, Object>>) JsonPath.read(body(),
            "$.storagePolicies[?(@.storageClassId == '" + id + "')]")).getFirst();
        assertThat(item).doesNotContainKeys("dedup", "compression", "encryption");
    }

    @Then("the response identifies storage class {string}, erasure coding {int} data blocks and {int} parity blocks, and replication factor {int}")
    public void responseIdentifiesPolicy(String id, int data, int parity, int replication) {
        assertJson("storageClassId", id);
        assertJson("erasureCoding.dataBlocks", Integer.toString(data));
        assertJson("erasureCoding.parityBlocks", Integer.toString(parity));
        assertJson("replication.factor", Integer.toString(replication));
    }

    @Then("its links identify the read-only collection and non-persistent validation endpoint")
    public void policyLinksAreReadOnlyAndValidationOnly() {
        assertJson("_links.collection.href", "/admin/storage-policies");
        assertJson("_links.validate.href", "/admin/storage-policies/validate");
    }

    @Then("the responses link only to their read-only catalog resources")
    public void responsesLinkOnlyToReadOnlyCatalogs() {
        Map<String, Object> links = JsonPath.read(body(), "$._links");
        assertThat(links).containsOnlyKeys("self", "collection");
    }

    @Then("no catalog item is returned")
    public void noCatalogItemReturned() {
        assertThat((Map<String, Object>) JsonPath.read(body(), "$"))
            .containsOnlyKeys("error", "_links");
    }

    @Then("the response reports validity true with no field errors")
    public void responseReportsValid() {
        assertJson("valid", "true");
        assertThat((List<?>) JsonPath.read(body(), "$.errors")).isEmpty();
    }

    @Then("the response links to the validation endpoint and the read-only policy collection")
    public void validationLinks() {
        assertJson("_links.self.href", "/admin/storage-policies/validate");
        assertJson("_links.collection.href", "/admin/storage-policies");
    }

    @Then("storage class {string} is absent and {string} remains unchanged")
    public void storageClassAbsentAndOriginalUnchanged(String absent, String original) {
        List<String> ids = JsonPath.read(body(), "$.storagePolicies[*].storageClassId");
        assertThat(ids).doesNotContain(absent).containsExactly(original);
    }

    @Then("the response Allow header is {string}")
    public void allowHeaderIs(String allow) {
        assertThat(response.getResponseHeaders().getFirst("Allow")).isEqualTo(allow);
    }

    @Then("the response explains that changes require configuration-as-code and catalog reload or redeployment")
    public void responseExplainsConfigurationAsCode() {
        assertThat(body()).contains("configuration-as-code", "redeploy/reload");
    }

    @Then("the response identifies bucket {string} and preserves every capacity accounting value")
    public void responsePreservesCapacity(String bucket) {
        var expected = capacity.capacity(bucket).block();
        assertJson("bucket", bucket);
        assertJson("usedBytes", Long.toString(expected.usedBytes()));
        assertJson("reservedBytes", Long.toString(expected.reservedBytes()));
        assertJson("quotaBytes", Long.toString(expected.quotaBytes()));
        assertJson("rejectedReservations", Long.toString(expected.rejectedReservations()));
        assertJson("lastRejectedBytes", Long.toString(expected.lastRejectedBytes()));
    }

    @Then("a subsequent capacity response preserves usage, reservations, and rejection counters")
    public void subsequentCapacityPreservesCounters() {
        requestGet("/admin/buckets/archive-2026/capacity");
        assertJson("usedBytes", "7340032");
        assertJson("reservedBytes", "1048576");
        assertJson("rejectedReservations", "2");
        assertJson("lastRejectedBytes", "2097152");
    }

    @Then("neither response contains bucket contents, object keys, or object-operation links")
    public void capacityContainsNoObjectData() {
        assertThat(body()).doesNotContain("contents", "objectKey", "/objects", "multipart", "tagging", "acl");
    }

    @Then("the inventory contains only these route families:")
    public void inventoryContainsOnly(DataTable table) {
        List<AdminRouter.AdminRoute> expected = table.asMaps().stream()
            .map(row -> new AdminRouter.AdminRoute(row.get("methods"), row.get("path pattern"), row.get("purpose")))
            .toList();
        assertThat(AdminRouter.routeInventory()).containsExactlyElementsOf(expected);
    }

    @Then("no route creates, lists, reads, writes, or deletes buckets or objects")
    public void noBucketOrObjectDataPlaneRoutes() {
        assertThat(AdminRouter.routeInventory())
            .noneMatch(route -> route.pathPattern().matches("/admin/(objects|buckets/\\{bucket}/objects).*"));
    }

    @Then("no route exposes multipart, metadata, tagging, ACL, versioning, or other S3 object semantics")
    public void noS3ObjectSemantics() {
        String paths = AdminRouter.routeInventory().stream()
            .map(AdminRouter.AdminRoute::pathPattern)
            .map(String::toLowerCase)
            .collect(Collectors.joining("\n"));
        assertThat(paths).doesNotContain("multipart", "metadata", "tagging", "acl", "versioning");
    }

    @Then("no route exposes a storage-engine object, chunk, manifest, filesystem, or private repository API")
    public void noPrivateStorageEngineRoutes() {
        assertThat(routeText()).doesNotContain("storage-engine", "chunk", "manifest", "filesystem", "repository");
    }

    private void prepareRealCatalogs() {
        if (policies != null && devices != null && diskSets != null) {
            return;
        }
        resetDirectory(CONFIG_ROOT);
        try {
            Files.createDirectories(POLICY_DIR);
            Files.createDirectories(DEVICE_DIR);
            Files.createDirectories(DISK_SET_DIR);
            Files.writeString(POLICY_DIR.resolve("minio-standard.yaml"), policyYaml("minio-standard", "MINIO_STANDARD"));
            Files.writeString(POLICY_DIR.resolve("standard.yaml"), policyYaml("standard", "STANDARD"));
            Files.writeString(DEVICE_DIR.resolve("node-1-disk-0.yaml"), deviceYaml(
                "node-1-disk-0", "/data/node-1/disk-0", 107374182400L, 26843545600L, "DEGRADED"));
            Files.writeString(DEVICE_DIR.resolve("node-2-disk-0.yaml"), deviceYaml(
                "node-2-disk-0", "/data/node-2/disk-0", 107374182400L, 53687091200L, "HEALTHY"));
            Files.writeString(DISK_SET_DIR.resolve("rack-a.yaml"), """
                diskSetId: rack-a
                failureDomain: RACK
                deviceIds:
                  - node-1-disk-0
                  - node-2-disk-0
                """);
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
        policies = new YamlStoragePolicyCatalog(POLICY_DIR);
        devices = new YamlStorageDeviceCatalog(DEVICE_DIR);
        diskSets = new YamlDiskSetCatalog(DISK_SET_DIR);
        diskSets.validateDeviceReferences(devices.loadedDeviceIds());
    }

    private void preparePolicyOnly(String storageClass) {
        resetDirectory(CONFIG_ROOT);
        try {
            Files.createDirectories(POLICY_DIR);
            Files.writeString(POLICY_DIR.resolve("minio-standard.yaml"), policyYaml("minio-standard", storageClass));
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
        policies = new YamlStoragePolicyCatalog(POLICY_DIR);
        devices = null;
        diskSets = null;
    }

    private void useConfiguredRouter() {
        MockEnvironment environment = new MockEnvironment()
            .withProperty(ConfiguredAdminBackendStatusProvider.BACKEND_PROPERTY, backendProperty)
            .withProperty("storage.engine.policies.dir", POLICY_DIR.toString())
            .withProperty("storage.engine.devices.dir", DEVICE_DIR.toString())
            .withProperty("storage.engine.disksets.dir", DISK_SET_DIR.toString())
            .withProperty("storage.engine.filesystem.root", STORAGE_ROOT.toString());
        environment.setActiveProfiles(profile);
        AdminBackendStatusProvider status = new ConfiguredAdminBackendStatusProvider(
            environment,
            provider(StoragePolicyCatalog.class, policies),
            provider(StorageDeviceCatalog.class, devices),
            provider(DiskSetCatalog.class, diskSets));
        client = client(policies, devices, diskSets, capacity, status);
    }

    private static WebTestClient client(
            StoragePolicyCatalog policies,
            StorageDeviceCatalog devices,
            DiskSetCatalog diskSets,
            BucketCapacityPort capacity,
            AdminBackendStatusProvider status) {
        AdminRouter router = new AdminRouter(
            provider(StoragePolicyCatalog.class, policies),
            provider(StorageDeviceCatalog.class, devices),
            provider(DiskSetCatalog.class, diskSets),
            provider(BucketCapacityPort.class, capacity),
            provider(AdminBackendStatusProvider.class, status),
            provider(AdminOperationalReportProvider.class, null));
        return WebTestClient.bindToRouterFunction(router.adminRoutes()).build();
    }

    private static <T> ObjectProvider<T> provider(Class<T> type, T bean) {
        StaticListableBeanFactory factory = new StaticListableBeanFactory();
        if (bean != null) {
            factory.addBean(type.getName(), bean);
        }
        return factory.getBeanProvider(type);
    }

    private WebTestClient requireClient() {
        assertThat(client).as("configured Admin API client").isNotNull();
        return client;
    }

    private String body() {
        assertThat(response).as("Admin API response").isNotNull();
        return new String(response.getResponseBody(), java.nio.charset.StandardCharsets.UTF_8);
    }

    private void assertJson(String field, String expected) {
        Object actual = JsonPath.read(body(), "$." + field);
        assertThat(actual.toString()).isEqualTo(expected);
    }

    private static String routeText() {
        return AdminRouter.routeInventory().stream()
            .map(route -> (route.methods() + " " + route.pathPattern() + " " + route.purpose()).toLowerCase())
            .collect(Collectors.joining("\n"));
    }

    private static String policyYaml(String policyId, String storageClassId) {
        return """
            policyId: %s
            version: "1.0"
            storageClassId: %s
            dedup:
              enabled: false
            compression:
              enabled: false
            encryption:
              enabled: false
              mode: NONE
            replication:
              factor: 1
            erasureCoding:
              enabled: true
              dataBlocks: 4
              parityBlocks: 2
            """.formatted(policyId, storageClassId);
    }

    private static String deviceYaml(
            String id, String path, long total, long available, String health) {
        return """
            deviceId: %s
            storagePath: %s
            totalCapacityBytes: %d
            availableCapacityBytes: %d
            health: %s
            failureDomain: DISK
            """.formatted(id, path, total, available, health);
    }

    private static void resetDirectory(Path root) {
        try {
            if (Files.exists(root)) {
                try (var paths = Files.walk(root)) {
                    paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException error) {
                            throw new UncheckedIOException(error);
                        }
                    });
                }
            }
            Files.createDirectories(root);
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }
}
