package com.example.magrathea.bootstrap;

import com.example.magrathea.bootstrap.ObjectStoreBackendStatus.Backend;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
    "admin.server.port=0",
    "storage.engine.policies.dir=/definitely/missing/policies",
    "storage.engine.devices.dir=/definitely/missing/devices",
    "storage.engine.disksets.dir=/definitely/missing/disksets"
})
class DefaultBackendContextTest {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private Environment environment;

    @Autowired
    private ObjectStoreBackendStatus backendStatus;

    @Autowired
    private S3ObjectCommandRepository objectCommandRepository;

    @Autowired
    private S3ObjectQueryRepository objectQueryRepository;

    @Test
    void defaultContextActivatesOnlyInMemoryRepositories() {
        assertThat(environment.getActiveProfiles()).doesNotContain("storage-engine");
        assertThat(environment.getDefaultProfiles()).contains("single-node");
        assertThat(backendStatus.backend()).isEqualTo(Backend.IN_MEMORY);

        assertSingleBean(S3ObjectCommandRepository.class, InMemoryReactiveS3ObjectRepository.class);
        assertSingleBean(S3ObjectQueryRepository.class, InMemoryReactiveS3ObjectRepository.class);
        assertSingleBean(BucketCommandRepository.class, InMemoryReactiveBucketRepository.class);
        assertSingleBean(BucketQueryRepository.class, InMemoryReactiveBucketRepository.class);
        assertSingleBean(MultipartUploadCommandRepository.class, InMemoryReactiveMultipartUploadRepository.class);
        assertSingleBean(MultipartUploadQueryRepository.class, InMemoryReactiveMultipartUploadRepository.class);

        assertNoBeans(StorageEngineReactiveS3ObjectRepository.class);
        assertNoBeans(StorageEngineReactiveBucketRepository.class);
        assertNoBeans(StorageEngineReactiveMultipartUploadRepository.class);
        assertNoBeans(ObjectStoreToStorageEngineTranslator.class);
    }

    @Test
    void defaultRepositorySmokeUsesInMemoryBackendEvenWhenExternalStorageEngineDirsAreMissing() {
        ObjectKey key = ObjectKey.of("default-smoke-bucket", "hello.txt");
        Flux<DataBuffer> content = Flux.just(
            new DefaultDataBufferFactory().wrap("hello-default".getBytes(StandardCharsets.UTF_8)));

        assertThat(objectCommandRepository.saveWithContent(key, content, "STANDARD").block()).isNotNull();

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
        assertThat(readContent).isEqualTo("hello-default");
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
