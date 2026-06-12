package com.example.magrathea.bootstrap;

import com.example.magrathea.bootstrap.ObjectStoreBackendStatus.Backend;
import com.example.magrathea.objectstore.domain.aggregate.S3Object;
import com.example.magrathea.objectstore.domain.valueobject.ObjectKey;
import com.example.magrathea.objectstore.reactive.repository.application.BucketCommandRepository;
import com.example.magrathea.objectstore.reactive.repository.application.BucketQueryRepository;
import com.example.magrathea.objectstore.reactive.repository.application.MultipartUploadCommandRepository;
import com.example.magrathea.objectstore.reactive.repository.application.MultipartUploadQueryRepository;
import com.example.magrathea.objectstore.reactive.repository.application.S3ObjectCommandRepository;
import com.example.magrathea.objectstore.reactive.repository.application.S3ObjectQueryRepository;
import com.example.magrathea.objectstorage.repository.storageengine.acl.ObjectStoreToStorageEngineTranslator;
import com.example.magrathea.objectstorage.repository.storageengine.adapter.StorageEngineReactiveBucketRepository;
import com.example.magrathea.objectstorage.repository.storageengine.adapter.StorageEngineReactiveMultipartUploadRepository;
import com.example.magrathea.objectstorage.repository.storageengine.adapter.StorageEngineReactiveS3ObjectRepository;
import com.example.magrathea.reactive.infrastructure.adapter.persistence.InMemoryReactiveBucketRepository;
import com.example.magrathea.reactive.infrastructure.adapter.persistence.InMemoryReactiveMultipartUploadRepository;
import com.example.magrathea.reactive.infrastructure.adapter.persistence.InMemoryReactiveS3ObjectRepository;
import com.example.magrathea.storageengine.application.port.DiskSetCatalog;
import com.example.magrathea.storageengine.application.port.StorageDeviceCatalog;
import com.example.magrathea.storageengine.application.port.StoragePolicyCatalog;
import com.example.magrathea.storageengine.application.service.ReactiveStorageOrchestrator;
import com.example.magrathea.storageengine.domain.valueobject.StorageClassId;
import com.example.magrathea.storageengine.domain.valueobject.StorageDeviceId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.test.context.ActiveProfiles;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
    "admin.server.port=0",
    "storage.engine.filesystem.root=${java.io.tmpdir}/magrathea-objectstorage/bootstrap-storage-engine-test",
    "storage.engine.filesystem.node-count=1"
})
@ActiveProfiles("storage-engine")
class StorageEngineBackendContextTest {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private Environment environment;

    @Autowired
    private ObjectStoreBackendStatus backendStatus;

    @Autowired
    private StoragePolicyCatalog policyCatalog;

    @Autowired
    private StorageDeviceCatalog deviceCatalog;

    @Autowired
    private DiskSetCatalog diskSetCatalog;

    @Autowired
    private S3ObjectCommandRepository objectCommandRepository;

    @Autowired
    private S3ObjectQueryRepository objectQueryRepository;

    @Test
    void storageEngineContextActivatesOnlyStorageEngineRepositories() {
        assertThat(environment.getActiveProfiles()).contains("storage-engine").doesNotContain("single-node");
        assertThat(backendStatus.backend()).isEqualTo(Backend.STORAGE_ENGINE);

        assertSingleBean(S3ObjectCommandRepository.class, StorageEngineReactiveS3ObjectRepository.class);
        assertSingleBean(S3ObjectQueryRepository.class, StorageEngineReactiveS3ObjectRepository.class);
        assertSingleBean(BucketCommandRepository.class, StorageEngineReactiveBucketRepository.class);
        assertSingleBean(BucketQueryRepository.class, StorageEngineReactiveBucketRepository.class);
        assertSingleBean(MultipartUploadCommandRepository.class, StorageEngineReactiveMultipartUploadRepository.class);
        assertSingleBean(MultipartUploadQueryRepository.class, StorageEngineReactiveMultipartUploadRepository.class);

        assertNoBeans(InMemoryReactiveS3ObjectRepository.class);
        assertNoBeans(InMemoryReactiveBucketRepository.class);
        assertNoBeans(InMemoryReactiveMultipartUploadRepository.class);

        assertThat(context.getBeansOfType(ObjectStoreToStorageEngineTranslator.class)).hasSize(1);
        assertThat(context.getBeansOfType(ReactiveStorageOrchestrator.class)).hasSize(1);
    }

    @Test
    void storageEngineContextLoadsClasspathYamlCatalogDefaults() {
        var policy = policyCatalog.findById("minio-standard").block();
        assertThat(policy).isNotNull();
        assertThat(policy.id()).isEqualTo(StorageClassId.STANDARD);
        assertThat(policy.dedup()).isEmpty();
        assertThat(policy.compression()).isEmpty();
        assertThat(policy.encryption()).isEmpty();
        assertThat(policy.replication().factor()).isEqualTo(1);
        assertThat(policy.erasureCoding()).isPresent();
        assertThat(policy.erasureCoding().get().dataBlocks()).isEqualTo(4);
        assertThat(policy.erasureCoding().get().parityBlocks()).isEqualTo(2);

        assertThat(policyCatalog.findBy(StorageClassId.STANDARD).block()).isNotNull();

        var device = deviceCatalog.findById(StorageDeviceId.of("local-disk-0")).block();
        assertThat(device).isNotNull();
        assertThat(device.storagePath()).isEqualTo("/data/local/disk-0");

        var diskSet = diskSetCatalog.findById("default-diskset").block();
        assertThat(diskSet).isNotNull();
        assertThat(diskSet.devices())
            .extracting(StorageDeviceId::value)
            .containsExactly("local-disk-0");
    }

    @Test
    void storageEngineRepositorySmokeWritesAndReadsContentThroughSelectedBackend() {
        ObjectKey key = ObjectKey.of("storage-engine-smoke-bucket", "hello.txt");
        Flux<DataBuffer> content = Flux.just(
            new DefaultDataBufferFactory().wrap("hello-storage-engine".getBytes(StandardCharsets.UTF_8)));

        var pendingObject = S3Object.createPending(key, "STANDARD", Map.of(), null);
        assertThat(objectCommandRepository.saveWithContent(pendingObject, content, "STANDARD").block()).isNotNull();
        assertThat(objectQueryRepository.findByBucketAndKey(key).block())
            .extracting(foundObject -> foundObject.key())
            .isEqualTo(key);

        String readContent = objectQueryRepository.getContent(key)
            .map(buffer -> {
                try {
                    return buffer.toString(StandardCharsets.UTF_8);
                } finally {
                    DataBufferUtils.release(buffer);
                }
            })
            .collectList()
            .map(parts -> String.join("", parts))
            .block();
        assertThat(readContent).isEqualTo("hello-storage-engine");
    }

    private <T> void assertSingleBean(Class<T> portType, Class<?> implementationType) {
        Map<String, T> beans = context.getBeansOfType(portType);
        assertThat(beans).hasSize(1);
        assertThat(beans.values().iterator().next()).isInstanceOf(implementationType);
    }

    private void assertNoBeans(Class<?> beanType) {
        assertThat(((ListableBeanFactory) context).getBeansOfType(beanType)).isEmpty();
    }
}
