package com.example.magrathea.storageengine.infrastructure.filesystem;

import com.example.magrathea.storageengine.domain.valueobject.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import reactor.test.StepVerifier;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class FileSystemStorageConsistencyTest {

    @TempDir
    Path tempDir;

    @Test
    void chunkStoreWritesAndReadsExactBytesByChunkId() {
        FileSystemStorageCluster cluster = new FileSystemStorageCluster(tempDir.resolve("cluster"), 2);
        FileSystemChunkStorePort chunkStore = new FileSystemChunkStorePort(cluster);
        ChunkId chunkId = ChunkId.generate();
        byte[] content = "stored bytes".getBytes(StandardCharsets.UTF_8);

        StepVerifier.create(chunkStore.store(chunkId, content, plan()))
                .assertNext(nodes -> assertThat(nodes).extracting(NodeId::value).containsExactly("node-001"))
                .verifyComplete();

        StepVerifier.create(chunkStore.read(chunkId))
                .assertNext(bytes -> assertThat(bytes).isEqualTo(content))
                .verifyComplete();
    }

    @Test
    void manifestRepositoryRoundTripsChunkReferencesAndMetadata() {
        FileSystemManifestRepository repository = new FileSystemManifestRepository(tempDir.resolve("manifests"));
        ObjectManifest manifest = manifest(List.of(
                chunk("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", 4, "node-001"),
                chunk("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb", 2, "node-002")));

        StepVerifier.create(repository.save(manifest)).verifyComplete();

        StepVerifier.create(repository.findBy(manifest.manifestId()))
                .assertNext(restored -> {
                    assertThat(restored.manifestId()).isEqualTo(manifest.manifestId());
                    assertThat(restored.objectId()).isEqualTo(manifest.objectId());
                    assertThat(restored.versionId()).isEqualTo(manifest.versionId());
                    assertThat(restored.storageClassId()).isEqualTo(manifest.storageClassId());
                    assertThat(restored.deviceHash()).isEqualTo(manifest.deviceHash());
                    assertThat(restored.totalOriginalSize()).isEqualTo(6L);
                    assertThat(restored.totalStoredSize()).isEqualTo(6L);
                    assertThat(restored.policyDecisions()).hasSize(6);
                    assertThat(restored.chunks()).hasSize(2);
                    assertThat(restored.chunks()).extracting(ChunkReferenceDescriptor::chunkId)
                            .containsExactly(manifest.chunks().get(0).chunkId(), manifest.chunks().get(1).chunkId());
                    assertThat(restored.chunks().get(0).locations()).extracting(NodeId::value).containsExactly("node-001");
                    assertThat(restored.chunks().get(1).locations()).extracting(NodeId::value).containsExactly("node-002");
                })
                .verifyComplete();
    }

    @Test
    void contentAddressIndexFindsRecordedDuplicateMapping() {
        FileSystemContentAddressIndex index = new FileSystemContentAddressIndex(tempDir.resolve("index"));
        DeviceConfigurationHash deviceHash = DeviceConfigurationHash.of("device-hash");
        Fingerprint fingerprint = Fingerprint.of(FingerprintAlgorithm.SHA256, "fingerprint-value");
        ChunkId chunkId = ChunkId.generate();

        StepVerifier.create(index.find(deviceHash, fingerprint))
                .assertNext(optional -> assertThat(optional).isEmpty())
                .verifyComplete();

        StepVerifier.create(index.record(deviceHash, fingerprint, chunkId)).verifyComplete();

        StepVerifier.create(index.find(deviceHash, fingerprint))
                .assertNext(optional -> {
                    assertThat(optional).isPresent();
                    assertThat(optional.get().chunkId()).isEqualTo(chunkId);
                    assertThat(optional.get().fingerprint()).isEqualTo(fingerprint);
                })
                .verifyComplete();
    }

    private PersistencePlan plan() {
        BucketRef bucket = BucketRef.of(BucketId.of("bucket"), "bucket");
        StorageClassId storageClass = StorageClassId.of("TEST");
        EffectiveStoragePolicy policy = EffectiveStoragePolicy.of(
                storageClass,
                bucket,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                ReplicationConfig.of(1));
        return PersistencePlan.create(policy, new VirtualDevice.BucketDevice(bucket, policy));
    }

    private ObjectManifest manifest(List<ChunkReferenceDescriptor> chunks) {
        PersistencePlan plan = plan();
        UploadCompletionTrace uploadTrace = new UploadCompletionTrace(
                UploadMode.SINGLE_OBJECT,
                Optional.empty(),
                ContentHash.of(ChecksumAlgorithm.SHA256, "consolidated"),
                true,
                6,
                true,
                Optional.empty());
        return new ObjectManifest(
                ManifestId.generate(),
                ObjectId.of("bucket/key"),
                VersionId.of("version-1"),
                plan.effectivePolicy().storageClassId(),
                plan.targetDevice(),
                plan.deviceHash(),
                uploadTrace,
                policyDecisions(),
                chunks.size(),
                chunks.stream().mapToLong(ChunkReferenceDescriptor::originalSize).sum(),
                chunks.stream().mapToLong(ChunkReferenceDescriptor::storedSize).sum(),
                chunks);
    }

    private ChunkReferenceDescriptor chunk(String uuid, long size, String nodeId) {
        ContentHash hash = ContentHash.of(ChecksumAlgorithm.SHA256, "hash-" + uuid.substring(0, 8));
        return new ChunkReferenceDescriptor(
                ChunkId.of(java.util.UUID.fromString(uuid)),
                Fingerprint.of(FingerprintAlgorithm.SHA256, "fp-" + uuid.substring(0, 8)),
                size,
                size,
                List.of(StepChecksumDescriptor.of(StepId.STORE, hash, hash)),
                hash,
                List.of(NodeId.of(nodeId)));
    }

    private List<PolicyDecision> policyDecisions() {
        return List.of(
                decision(StepId.DEDUP, PolicyDecisionStatus.DISABLED),
                decision(StepId.COMPRESS, PolicyDecisionStatus.DISABLED),
                decision(StepId.CRYPT, PolicyDecisionStatus.DISABLED),
                decision(StepId.ERASURE_CODING, PolicyDecisionStatus.DISABLED),
                decision(StepId.REPLICATION, PolicyDecisionStatus.ENABLED),
                decision(StepId.STORE, PolicyDecisionStatus.ENABLED));
    }

    private PolicyDecision decision(StepId stepId, PolicyDecisionStatus status) {
        return PolicyDecision.of(stepId, status, PolicyDecisionReason.of(status.name(), status.name()));
    }
}
