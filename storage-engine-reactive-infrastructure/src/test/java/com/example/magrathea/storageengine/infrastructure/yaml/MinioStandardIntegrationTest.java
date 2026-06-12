package com.example.magrathea.storageengine.infrastructure.yaml;

import com.example.magrathea.storageengine.domain.service.EffectivePolicyResolver;
import com.example.magrathea.storageengine.domain.service.VirtualDeviceResolver;
import com.example.magrathea.storageengine.domain.valueobject.BucketId;
import com.example.magrathea.storageengine.domain.valueobject.BucketRef;
import com.example.magrathea.storageengine.domain.valueobject.ChunkAlignment;
import com.example.magrathea.storageengine.domain.valueobject.DedupScope;
import com.example.magrathea.storageengine.domain.valueobject.EncryptionRequest;
import com.example.magrathea.storageengine.domain.valueobject.FingerprintAlgorithm;
import com.example.magrathea.storageengine.domain.valueobject.ObjectContentDescriptor;
import com.example.magrathea.storageengine.domain.valueobject.ObjectKey;
import com.example.magrathea.storageengine.domain.valueobject.ObjectMetadataDescriptor;
import com.example.magrathea.storageengine.domain.valueobject.StorageClassId;
import com.example.magrathea.storageengine.domain.valueobject.StorageDeviceId;
import com.example.magrathea.storageengine.domain.valueobject.UploadRequestContext;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the executable MINIO_STANDARD YAML scope against the domain policy pipeline.
 */
class MinioStandardIntegrationTest {

    private static final long MINIO_STANDARD_CHUNK_SIZE = 1_048_576L;

    @Test
    void classpathCatalogsLoadMinioStandardPolicyAndSelectLocalDisk() {
        YamlStoragePolicyCatalog policyCatalog = YamlStoragePolicyCatalog.fromClasspath("storage-policies");
        YamlStorageDeviceCatalog deviceCatalog = YamlStorageDeviceCatalog.fromClasspath("storage-devices");
        YamlDiskSetCatalog diskSetCatalog = YamlDiskSetCatalog.fromClasspath("disk-sets");

        StepVerifier.create(policyCatalog.findById("minio-standard"))
                .assertNext(policy -> {
                    assertThat(policy.id()).isEqualTo(StorageClassId.STANDARD);
                    assertThat(policy.dedup()).isPresent();
                    assertThat(policy.dedup().get().scope()).isEqualTo(DedupScope.BUCKET_LEVEL);
                    assertThat(policy.dedup().get().chunkSize()).isEqualTo(MINIO_STANDARD_CHUNK_SIZE);
                    assertThat(policy.dedup().get().algorithm()).isEqualTo(FingerprintAlgorithm.SHA256);
                    assertThat(policy.dedup().get().alignment()).isEqualTo(ChunkAlignment.NONE);
                    assertThat(policy.compression()).isEmpty();
                    assertThat(policy.encryption()).isEmpty();
                    assertThat(policy.replication().factor()).isEqualTo(1);
                    assertThat(policy.erasureCoding()).isEmpty();

                    var effective = new EffectivePolicyResolver().resolve(
                            policy,
                            uploadContext(MINIO_STANDARD_CHUNK_SIZE));

                    assertThat(effective.dedup()).isPresent();
                    assertThat(effective.dedup().get().chunkSize())
                            .as("EffectivePolicyResolver must use the policy-defined 1 MiB chunk size")
                            .isEqualTo(MINIO_STANDARD_CHUNK_SIZE);
                })
                .verifyComplete();

        StepVerifier.create(policyCatalog.findBy(StorageClassId.STANDARD))
                .assertNext(policy -> assertThat(policy.dedup()).isPresent())
                .verifyComplete();

        StepVerifier.create(deviceCatalog.findById(StorageDeviceId.of("local-disk-0")))
                .assertNext(device -> {
                    assertThat(device.id().value()).isEqualTo("local-disk-0");
                    assertThat(device.storagePath()).isEqualTo("/data/local/disk-0");
                    assertThat(device.isWriteEligible()).isTrue();
                })
                .verifyComplete();

        StepVerifier.create(diskSetCatalog.findById("default-diskset"))
                .assertNext(diskSet -> {
                    assertThat(diskSet.name()).isEqualTo("default-diskset");
                    assertThat(diskSet.devices())
                            .extracting(StorageDeviceId::value)
                            .containsExactly("local-disk-0");
                })
                .verifyComplete();

        StepVerifier.create(policyCatalog.findById("minio-standard")
                        .zipWith(deviceCatalog.findAll().collectList())
                        .map(tuple -> new VirtualDeviceResolver()
                                .selectDevice(tuple.getT1(), tuple.getT2())))
                .assertNext(selected -> assertThat(selected.id().value()).isEqualTo("local-disk-0"))
                .verifyComplete();
    }

    @Test
    void effectivePolicyResolverUsesPolicyChunkSizeInsteadOfLegacy64KiBConstant() {
        YamlStoragePolicyCatalog policyCatalog = YamlStoragePolicyCatalog.fromClasspath("storage-policies");
        long objectSizeAboveLegacyConstant = 65 * 1024L;

        StepVerifier.create(policyCatalog.findById("minio-standard")
                        .map(policy -> new EffectivePolicyResolver().resolve(
                                policy,
                                uploadContext(objectSizeAboveLegacyConstant))))
                .assertNext(effective -> assertThat(effective.dedup())
                        .as("65 KiB is above the old 64 KiB constant but below the policy-defined 1 MiB chunk")
                        .isEmpty())
                .verifyComplete();
    }

    private static UploadRequestContext uploadContext(long objectSize) {
        BucketRef bucket = BucketRef.of(BucketId.of("bucket-1"), "test-bucket");
        return UploadRequestContext.of(
                ObjectKey.of("test-bucket", "object.bin"),
                bucket,
                StorageClassId.STANDARD,
                ObjectContentDescriptor.of("application/octet-stream", objectSize),
                ObjectMetadataDescriptor.empty(),
                EncryptionRequest.none(),
                Optional.empty());
    }
}
