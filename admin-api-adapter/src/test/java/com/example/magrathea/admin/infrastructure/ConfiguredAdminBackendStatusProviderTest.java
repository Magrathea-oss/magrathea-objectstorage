package com.example.magrathea.admin.infrastructure;

import com.example.magrathea.storageengine.application.port.DiskSetCatalog;
import com.example.magrathea.storageengine.application.port.StorageDeviceCatalog;
import com.example.magrathea.storageengine.application.port.StoragePolicyCatalog;
import com.example.magrathea.storageengine.domain.valueobject.StorageClassId;
import com.example.magrathea.storageengine.domain.valueobject.StoragePolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.mock.env.MockEnvironment;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ConfiguredAdminBackendStatusProviderTest {

    @TempDir
    Path storageRoot;

    @Test
    void filesystemAvailabilityProbeLeavesTheEventLoop() {
        MockEnvironment environment = new MockEnvironment()
            .withProperty("storage.engine.filesystem.root", storageRoot.toString());
        ConfiguredAdminBackendStatusProvider provider = provider(environment, null);
        var eventLoop = Schedulers.newSingle("admin-event-loop");
        AtomicReference<String> responseThread = new AtomicReference<>();

        try {
            StepVerifier.create(provider.backendStatus()
                    .doOnNext(ignored -> responseThread.set(Thread.currentThread().getName()))
                    .subscribeOn(eventLoop))
                .assertNext(status -> assertThat(storageRoots(status))
                    .containsEntry(storageRoot.toString(), Map.of(
                        "path", storageRoot.toString(),
                        "availability", "available")))
                .verifyComplete();
        } finally {
            eventLoop.dispose();
        }

        assertThat(responseThread.get()).contains("boundedElastic");
    }

    @Test
    void catalogFailureDoesNotExposeProviderErrorDetails() {
        MockEnvironment environment = new MockEnvironment()
            .withProperty("storage.engine.policies.dir", "/etc/magrathea/storage-policies");
        StoragePolicyCatalog failingCatalog = new StoragePolicyCatalog() {
            @Override
            public Mono<StoragePolicy> findById(String policyId) {
                return Mono.empty();
            }

            @Override
            public Mono<StoragePolicy> findBy(StorageClassId id) {
                return Mono.empty();
            }

            @Override
            public Flux<StoragePolicy> findAll() {
                return Flux.error(new IllegalStateException(
                    "catalog token=super-secret path=/private/provider-state"));
            }
        };

        StepVerifier.create(provider(environment, failingCatalog).backendStatus())
            .assertNext(status -> {
                Map<String, Object> policyStatus = catalog(status, "policies");
                assertThat(policyStatus).containsEntry("availability", "unavailable")
                    .containsEntry("sourceDirectory", "/etc/magrathea/storage-policies")
                    .containsEntry("error", "Catalog provider is unavailable");
                assertThat(status.toString()).doesNotContain("super-secret", "/private/provider-state");
            })
            .verifyComplete();
    }

    private static ConfiguredAdminBackendStatusProvider provider(
            MockEnvironment environment,
            StoragePolicyCatalog policies) {
        return new ConfiguredAdminBackendStatusProvider(
            environment,
            provider(StoragePolicyCatalog.class, policies),
            provider(StorageDeviceCatalog.class, null),
            provider(DiskSetCatalog.class, null));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> storageRoots(Map<String, Object> status) {
        return (Map<String, Object>) status.get("storageRoots");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> catalog(Map<String, Object> status, String name) {
        return (Map<String, Object>) ((Map<String, Object>) status.get("catalogs")).get(name);
    }

    private static <T> ObjectProvider<T> provider(Class<T> type, T bean) {
        StaticListableBeanFactory factory = new StaticListableBeanFactory();
        if (bean != null) {
            factory.addBean(type.getName(), bean);
        }
        return factory.getBeanProvider(type);
    }
}
