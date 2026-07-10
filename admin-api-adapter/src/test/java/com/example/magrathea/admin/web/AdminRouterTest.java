package com.example.magrathea.admin.web;

import com.example.magrathea.storageengine.application.port.DiskSetCatalog;
import com.example.magrathea.storageengine.application.port.StorageDeviceCatalog;
import com.example.magrathea.storageengine.application.port.StoragePolicyCatalog;
import com.example.magrathea.storageengine.domain.valueobject.DeviceHealth;
import com.example.magrathea.storageengine.domain.valueobject.DiskSet;
import com.example.magrathea.storageengine.domain.valueobject.ErasureCodingConfig;
import com.example.magrathea.storageengine.domain.valueobject.FailureDomain;
import com.example.magrathea.storageengine.domain.valueobject.ReplicationConfig;
import com.example.magrathea.storageengine.domain.valueobject.StorageClassId;
import com.example.magrathea.storageengine.domain.valueobject.StorageDevice;
import com.example.magrathea.storageengine.domain.valueobject.StorageDeviceId;
import com.example.magrathea.storageengine.domain.valueobject.StoragePolicy;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class AdminRouterTest {

    @Test
    void healthReturnsAdminApiMetadataAndLinks() {
        WebTestClient client = clientWith(null, null, null);

        client.get().uri("/admin/health")
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBody()
            .jsonPath("$.status").isEqualTo("ok")
            .jsonPath("$.message").isEqualTo("Admin API running")
            .jsonPath("$.mode").isEqualTo("configuration-as-code")
            .jsonPath("$._links.policies.href").isEqualTo("/admin/storage-policies")
            .jsonPath("$._links.devices.href").isEqualTo("/admin/storage-devices")
            .jsonPath("$._links.diskSets.href").isEqualTo("/admin/disk-sets");
    }

    @Test
    void livenessAndReadinessExposeOperationalProbeState() {
        WebTestClient client = clientWith(fakePolicyCatalog(), fakeDeviceCatalog(), fakeDiskSetCatalog());

        client.get().uri("/admin/live")
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBody()
            .jsonPath("$.probe").isEqualTo("liveness")
            .jsonPath("$.status").isEqualTo("ok")
            .jsonPath("$._links.ready.href").isEqualTo("/admin/ready");

        client.get().uri("/admin/ready")
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBody()
            .jsonPath("$.probe").isEqualTo("readiness")
            .jsonPath("$.status").isEqualTo("ready")
            .jsonPath("$.components[0].name").isEqualTo("storage-policy-catalog")
            .jsonPath("$.components[0].status").isEqualTo("ready")
            .jsonPath("$.components[1].name").isEqualTo("storage-device-catalog")
            .jsonPath("$.components[1].status").isEqualTo("ready")
            .jsonPath("$.components[2].name").isEqualTo("disk-set-catalog")
            .jsonPath("$.components[2].status").isEqualTo("ready")
            .jsonPath("$._links.live.href").isEqualTo("/admin/live");
    }

    @Test
    void catalogEndpointReturnsServiceUnavailableWhenRequiredCatalogIsMissing() {
        WebTestClient client = clientWith(null, null, null);

        client.get().uri("/admin/storage-policies")
            .exchange()
            .expectStatus().isEqualTo(503)
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBody()
            .jsonPath("$.error.code").isEqualTo("catalog-not-configured")
            .jsonPath("$.error.message").isEqualTo("Required catalog bean is not configured: storage-policy-catalog")
            .jsonPath("$.error.path").isEqualTo("/admin/storage-policies")
            .jsonPath("$._links.self.href").isEqualTo("/admin/health");
    }

    @Test
    void listsAndGetsStoragePoliciesFromCatalog() {
        WebTestClient client = clientWith(fakePolicyCatalog(), null, null);

        client.get().uri("/admin/storage-policies")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.count").isEqualTo(2)
            .jsonPath("$.storagePolicies[0].storageClassId").isEqualTo("MINIO_STANDARD")
            .jsonPath("$.storagePolicies[0].erasureCoding.dataBlocks").isEqualTo(4)
            .jsonPath("$.storagePolicies[0].erasureCoding.parityBlocks").isEqualTo(2)
            .jsonPath("$.storagePolicies[0].replication.factor").isEqualTo(1)
            .jsonPath("$.storagePolicies[0]._links.validate.href").isEqualTo("/admin/storage-policies/validate")
            .jsonPath("$._links.validate.method").isEqualTo("POST");

        client.get().uri("/admin/storage-policies/minio-standard")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.storageClassId").isEqualTo("MINIO_STANDARD")
            .jsonPath("$.erasureCoding.dataBlocks").isEqualTo(4)
            .jsonPath("$.erasureCoding.parityBlocks").isEqualTo(2)
            .jsonPath("$._links.self.href").isEqualTo("/admin/storage-policies/minio-standard");
    }

    @Test
    void storagePolicyNotFoundReturnsStructuredNotFound() {
        WebTestClient client = clientWith(fakePolicyCatalog(), null, null);

        client.get().uri("/admin/storage-policies/archive")
            .exchange()
            .expectStatus().isNotFound()
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBody()
            .jsonPath("$.error.code").isEqualTo("storage-policy-not-found")
            .jsonPath("$.error.message").isEqualTo("Storage policy not found: archive")
            .jsonPath("$.error.path").isEqualTo("/admin/storage-policies/archive")
            .jsonPath("$._links.collection.href").isEqualTo("/admin/storage-policies");
    }

    @Test
    void validatesMinioStandardErasureCodedPolicyWithoutDeduplication() {
        WebTestClient client = clientWith(null, null, null);
        String payload = """
            {
              "storageClassId": "MINIO_STANDARD",
              "erasureCoding": {
                "dataBlocks": 4,
                "parityBlocks": 2
              },
              "replication": {
                "factor": 1
              }
            }
            """;

        client.post().uri("/admin/storage-policies/validate")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(payload)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.valid").isEqualTo(true)
            .jsonPath("$.errors.length()").isEqualTo(0)
            .jsonPath("$.policy.storageClassId").isEqualTo("MINIO_STANDARD")
            .jsonPath("$.policy.erasureCoding.dataBlocks").isEqualTo(4)
            .jsonPath("$.policy.erasureCoding.parityBlocks").isEqualTo(2)
            .jsonPath("$.policy.replication.factor").isEqualTo(1)
            .jsonPath("$.policy.dedup").doesNotExist()
            .jsonPath("$._links.collection.href").isEqualTo("/admin/storage-policies");
    }

    @Test
    void invalidValidationPayloadReturnsInvalidResultWithErrors() {
        WebTestClient client = clientWith(null, null, null);

        client.post().uri("/admin/storage-policies/validate")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{}")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.valid").isEqualTo(false)
            .jsonPath("$.errors[0].field").isEqualTo("storagePolicy")
            .jsonPath("$.errors[0].message").value(message -> assertThat(message.toString()).contains("storageClassId is required"))
            .jsonPath("$.policy").doesNotExist();
    }

    @Test
    void storagePolicyMutationEndpointsAreReadOnly() {
        WebTestClient client = clientWith(fakePolicyCatalog(), null, null);

        client.post().uri("/admin/storage-policies")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{}")
            .exchange()
            .expectStatus().isEqualTo(405)
            .expectHeader().valueEquals("Allow", "GET, HEAD")
            .expectBody()
            .jsonPath("$.error.code").isEqualTo("admin-catalog-read-only")
            .jsonPath("$._links.validate.method").isEqualTo("POST");

        client.put().uri("/admin/storage-policies/minio-standard")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{}")
            .exchange()
            .expectStatus().isEqualTo(405)
            .expectHeader().valueEquals("Allow", "GET, HEAD")
            .expectBody()
            .jsonPath("$.error.code").isEqualTo("admin-catalog-read-only")
            .jsonPath("$.error.path").isEqualTo("/admin/storage-policies/minio-standard");

        client.delete().uri("/admin/storage-policies/minio-standard")
            .exchange()
            .expectStatus().isEqualTo(405)
            .expectHeader().valueEquals("Allow", "GET, HEAD")
            .expectBody()
            .jsonPath("$.error.code").isEqualTo("admin-catalog-read-only")
            .jsonPath("$.error.path").isEqualTo("/admin/storage-policies/minio-standard");
    }

    @Test
    void listsAndGetsStorageDevicesFromCatalog() {
        WebTestClient client = clientWith(null, fakeDeviceCatalog(), null);

        client.get().uri("/admin/storage-devices")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.count").isEqualTo(2)
            .jsonPath("$.storageDevices[0].id").isEqualTo("device-a")
            .jsonPath("$.storageDevices[0].storagePath").isEqualTo("/var/lib/magrathea/device-a")
            .jsonPath("$.storageDevices[0].health").isEqualTo("HEALTHY")
            .jsonPath("$.storageDevices[0].writeEligible").isEqualTo(true)
            .jsonPath("$.storageDevices[0].readEligible").isEqualTo(true);

        client.get().uri("/admin/storage-devices/device-b")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.id").isEqualTo("device-b")
            .jsonPath("$.health").isEqualTo("DEGRADED")
            .jsonPath("$.writeEligible").isEqualTo(false)
            .jsonPath("$.readEligible").isEqualTo(true)
            .jsonPath("$._links.self.href").isEqualTo("/admin/storage-devices/device-b");
    }

    @Test
    void listsAndGetsDiskSetsFromCatalog() {
        WebTestClient client = clientWith(null, null, fakeDiskSetCatalog());

        client.get().uri("/admin/disk-sets")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.count").isEqualTo(2)
            .jsonPath("$.diskSets[0].name").isEqualTo("rack-a")
            .jsonPath("$.diskSets[0].failureDomain").isEqualTo("RACK")
            .jsonPath("$.diskSets[0].devices[0]").isEqualTo("device-a")
            .jsonPath("$.diskSets[0].size").isEqualTo(2);

        client.get().uri("/admin/disk-sets/rack-b")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.name").isEqualTo("rack-b")
            .jsonPath("$.failureDomain").isEqualTo("RACK")
            .jsonPath("$.devices[0]").isEqualTo("device-c")
            .jsonPath("$._links.self.href").isEqualTo("/admin/disk-sets/rack-b");
    }

    private static WebTestClient clientWith(
            StoragePolicyCatalog policyCatalog,
            StorageDeviceCatalog deviceCatalog,
            DiskSetCatalog diskSetCatalog) {
        AdminRouter router = new AdminRouter(
            provider(StoragePolicyCatalog.class, policyCatalog),
            provider(StorageDeviceCatalog.class, deviceCatalog),
            provider(DiskSetCatalog.class, diskSetCatalog));
        return WebTestClient.bindToRouterFunction(router.adminRoutes()).build();
    }

    private static <T> ObjectProvider<T> provider(Class<T> type, T bean) {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        if (bean != null) {
            beanFactory.addBean(type.getName(), bean);
        }
        return beanFactory.getBeanProvider(type);
    }

    private static StoragePolicyCatalog fakePolicyCatalog() {
        Map<String, StoragePolicy> policies = new LinkedHashMap<>();
        policies.put("minio-standard", minioStandardPolicy());
        policies.put("standard", StoragePolicy.minimal(StorageClassId.STANDARD));
        return new FakeStoragePolicyCatalog(policies);
    }

    private static StoragePolicy minioStandardPolicy() {
        return StoragePolicy.of(
            StorageClassId.MINIO_STANDARD,
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.of(ErasureCodingConfig.of(4, 2)),
            ReplicationConfig.of(1));
    }

    private static StorageDeviceCatalog fakeDeviceCatalog() {
        Map<StorageDeviceId, StorageDevice> devices = new LinkedHashMap<>();
        devices.put(StorageDeviceId.of("device-a"), StorageDevice.restore(
            StorageDeviceId.of("device-a"),
            "/var/lib/magrathea/device-a",
            1_000_000L,
            750_000L,
            DeviceHealth.HEALTHY));
        devices.put(StorageDeviceId.of("device-b"), StorageDevice.restore(
            StorageDeviceId.of("device-b"),
            "/var/lib/magrathea/device-b",
            2_000_000L,
            1_500_000L,
            DeviceHealth.DEGRADED));
        return new FakeStorageDeviceCatalog(devices);
    }

    private static DiskSetCatalog fakeDiskSetCatalog() {
        Map<String, DiskSet> diskSets = new LinkedHashMap<>();
        diskSets.put("rack-a", DiskSet.of("rack-a", FailureDomain.RACK,
            List.of(StorageDeviceId.of("device-a"), StorageDeviceId.of("device-b"))));
        diskSets.put("rack-b", DiskSet.of("rack-b", FailureDomain.RACK,
            List.of(StorageDeviceId.of("device-c"))));
        return new FakeDiskSetCatalog(diskSets);
    }

    private record FakeStoragePolicyCatalog(Map<String, StoragePolicy> policies) implements StoragePolicyCatalog {
        @Override
        public Mono<StoragePolicy> findById(String policyId) {
            return Mono.justOrEmpty(policies.get(policyId));
        }

        @Override
        public Mono<StoragePolicy> findBy(StorageClassId id) {
            return Mono.justOrEmpty(policies.values().stream()
                .filter(policy -> policy.id().equals(id))
                .findFirst());
        }

        @Override
        public Flux<StoragePolicy> findAll() {
            return Flux.fromIterable(policies.values());
        }
    }

    private record FakeStorageDeviceCatalog(Map<StorageDeviceId, StorageDevice> devices) implements StorageDeviceCatalog {
        @Override
        public Mono<StorageDevice> findById(StorageDeviceId deviceId) {
            return Mono.justOrEmpty(devices.get(deviceId));
        }

        @Override
        public Flux<StorageDevice> findAll() {
            return Flux.fromIterable(devices.values());
        }

        @Override
        public Flux<StorageDevice> findEligibleForWrite() {
            return Flux.fromIterable(devices.values())
                .filter(StorageDevice::isWriteEligible);
        }
    }

    private record FakeDiskSetCatalog(Map<String, DiskSet> diskSets) implements DiskSetCatalog {
        @Override
        public Mono<DiskSet> findById(String diskSetId) {
            return Mono.justOrEmpty(diskSets.get(diskSetId));
        }

        @Override
        public Flux<DiskSet> findAll() {
            return Flux.fromIterable(diskSets.values());
        }
    }
}
