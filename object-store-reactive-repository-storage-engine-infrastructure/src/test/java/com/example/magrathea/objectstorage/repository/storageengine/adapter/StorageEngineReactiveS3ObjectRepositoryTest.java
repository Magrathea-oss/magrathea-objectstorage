package com.example.magrathea.objectstorage.repository.storageengine.adapter;

import com.example.magrathea.objectstore.domain.aggregate.S3Object;
import com.example.magrathea.objectstore.domain.valueobject.ObjectKey;
import com.example.magrathea.objectstorage.repository.storageengine.acl.ObjectStoreToStorageEngineTranslator;
import com.example.magrathea.storageengine.application.service.ReactiveStorageOrchestrator;
import com.example.magrathea.storageengine.domain.aggregate.StoredObject;
import com.example.magrathea.storageengine.domain.valueobject.CompleteUploadCommand;
import com.example.magrathea.storageengine.domain.valueobject.ManifestId;
import com.example.magrathea.storageengine.domain.valueobject.ObjectId;
import com.example.magrathea.storageengine.domain.valueobject.VersionId;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class StorageEngineReactiveS3ObjectRepositoryTest {

    private static final DefaultDataBufferFactory BUFFER_FACTORY = new DefaultDataBufferFactory();

    @Test
    void saveWithContentThenGetContentReturnsOriginalBytes() {
        FakeOrchestrator orchestrator = new FakeOrchestrator();
        StorageEngineReactiveS3ObjectRepository repository = new StorageEngineReactiveS3ObjectRepository(
                new ObjectStoreToStorageEngineTranslator(), orchestrator);
        ObjectKey key = ObjectKey.of("bucket", "key");
        byte[] content = "adapter read path".getBytes(StandardCharsets.UTF_8);
        S3Object object = S3Object.createPending(key, "TEST", Map.of(), null);

        StepVerifier.create(repository.saveWithContent(object, Flux.just(BUFFER_FACTORY.wrap(content)), "TEST"))
                .expectNextCount(1)
                .verifyComplete();

        StepVerifier.create(repository.getContent(key).reduce(new byte[0], this::append))
                .assertNext(actual -> assertArrayEquals(content, actual))
                .verifyComplete();
    }

    private byte[] append(byte[] accumulated, DataBuffer buffer) {
        byte[] next = new byte[buffer.readableByteCount()];
        buffer.read(next);
        DataBufferUtils.release(buffer);
        byte[] combined = new byte[accumulated.length + next.length];
        System.arraycopy(accumulated, 0, combined, 0, accumulated.length);
        System.arraycopy(next, 0, combined, accumulated.length, next.length);
        return combined;
    }

    private static final class FakeOrchestrator extends ReactiveStorageOrchestrator {
        private final Map<ManifestId, byte[]> contentByManifestId = new ConcurrentHashMap<>();

        private FakeOrchestrator() {
            super(null, null, null, null, null, null, null, null, null, null, null, null, 65536);
        }

        @Override
        public Mono<StoredObject> store(CompleteUploadCommand command, Flux<DataBuffer> data) {
            return data.reduce(new byte[0], (accumulated, buffer) -> {
                        byte[] next = new byte[buffer.readableByteCount()];
                        buffer.read(next);
                        DataBufferUtils.release(buffer);
                        byte[] combined = new byte[accumulated.length + next.length];
                        System.arraycopy(accumulated, 0, combined, 0, accumulated.length);
                        System.arraycopy(next, 0, combined, accumulated.length, next.length);
                        return combined;
                    })
                    .map(bytes -> {
                        ManifestId manifestId = ManifestId.generate();
                        contentByManifestId.put(manifestId, bytes);
                        StoredObject storedObject = StoredObject.create(
                                ObjectId.of(command.context().objectKey().bucket() + "/" + command.context().objectKey().key()),
                                VersionId.of("version-1"),
                                command.context().bucket(),
                                command.context().storageClassId(),
                                new com.example.magrathea.storageengine.domain.valueobject.VirtualDevice.BucketDevice(
                                        command.context().bucket(),
                                        com.example.magrathea.storageengine.domain.valueobject.EffectiveStoragePolicy.of(
                                                command.context().storageClassId(),
                                                command.context().bucket(),
                                                java.util.Optional.empty(),
                                                java.util.Optional.empty(),
                                                java.util.Optional.empty(),
                                                java.util.Optional.empty(),
                                                com.example.magrathea.storageengine.domain.valueobject.ReplicationConfig.of(1))));
                        storedObject.attachManifest(manifestId);
                        return storedObject;
                    });
        }

        @Override
        public Flux<byte[]> read(ManifestId manifestId) {
            return Flux.just(contentByManifestId.get(manifestId));
        }
    }
}
