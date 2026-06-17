package com.example.magrathea.storageengine.infrastructure.yaml;

import com.example.magrathea.storageengine.domain.service.EffectivePolicyResolver;
import com.example.magrathea.storageengine.domain.service.PersistencePlanner;
import com.example.magrathea.storageengine.domain.service.VirtualDeviceResolver;
import com.example.magrathea.storageengine.domain.valueobject.BucketId;
import com.example.magrathea.storageengine.domain.valueobject.BucketRef;
import com.example.magrathea.storageengine.domain.valueobject.EncryptionRequest;
import com.example.magrathea.storageengine.domain.valueobject.ObjectContentDescriptor;
import com.example.magrathea.storageengine.domain.valueobject.ObjectKey;
import com.example.magrathea.storageengine.domain.valueobject.ObjectMetadataDescriptor;
import com.example.magrathea.storageengine.domain.valueobject.StepExecutionStatus;
import com.example.magrathea.storageengine.domain.valueobject.StepId;
import com.example.magrathea.storageengine.domain.valueobject.StepPlan;
import com.example.magrathea.storageengine.domain.valueobject.StorageClassId;
import com.example.magrathea.storageengine.domain.valueobject.StorageDeviceId;
import com.example.magrathea.storageengine.domain.valueobject.UploadRequestContext;
import com.example.magrathea.storageengine.domain.valueobject.VirtualDevice;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the executable MINIO_STANDARD-equivalent YAML scope against the domain policy pipeline.
 *
 * <p>MINIO_STANDARD-compatible behavior is modeled as STANDARD storage with erasure coding enabled,
 * dedup disabled, replication factor 1, and no compression/encryption by default. The sample device
 * catalog is currently a classpath loading and selection smoke test; physical EC shard placement is
 * not validated by these catalogs yet.
 */
class MinioStandardIntegrationTest {

    private static final int MINIO_STANDARD_EC_DATA_BLOCKS = 4;
    private static final int MINIO_STANDARD_EC_PARITY_BLOCKS = 2;
    private static final long OBJECT_SIZE = 1_048_576L;

    @Test
    void classpathCatalogsLoadMinioStandardPolicyAndSelectLocalDisk() {
        YamlStoragePolicyCatalog policyCatalog = YamlStoragePolicyCatalog.fromClasspath("storage-policies");
        YamlStorageDeviceCatalog deviceCatalog = YamlStorageDeviceCatalog.fromClasspath("storage-devices");
        YamlDiskSetCatalog diskSetCatalog = YamlDiskSetCatalog.fromClasspath("disk-sets");

        StepVerifier.create(policyCatalog.findById("minio-standard"))
                .assertNext(policy -> {
                    assertThat(policy.id()).isEqualTo(StorageClassId.STANDARD);
                    assertThat(policy.dedup()).isEmpty();
                    assertThat(policy.compression()).isEmpty();
                    assertThat(policy.encryption()).isEmpty();
                    assertThat(policy.replication().factor()).isEqualTo(1);
                    assertThat(policy.erasureCoding()).isPresent();
                    assertThat(policy.erasureCoding().get().dataBlocks())
                            .isEqualTo(MINIO_STANDARD_EC_DATA_BLOCKS);
                    assertThat(policy.erasureCoding().get().parityBlocks())
                            .isEqualTo(MINIO_STANDARD_EC_PARITY_BLOCKS);

                    var effective = new EffectivePolicyResolver().resolve(
                            policy,
                            uploadContext(OBJECT_SIZE));

                    assertThat(effective.storageClassId()).isEqualTo(StorageClassId.STANDARD);
                    assertThat(effective.dedup()).isEmpty();
                    assertThat(effective.compression()).isEmpty();
                    assertThat(effective.encryption()).isEmpty();
                    assertThat(effective.replication().factor()).isEqualTo(1);
                    assertThat(effective.erasureCoding()).isPresent();
                    assertThat(effective.erasureCoding().get().dataBlocks())
                            .isEqualTo(MINIO_STANDARD_EC_DATA_BLOCKS);
                    assertThat(effective.erasureCoding().get().parityBlocks())
                            .isEqualTo(MINIO_STANDARD_EC_PARITY_BLOCKS);
                    VirtualDeviceResolver virtualDeviceResolver = new VirtualDeviceResolver();
                    VirtualDevice targetDevice = virtualDeviceResolver.resolve(effective, effective.bucketRef());
                    assertThat(targetDevice).isInstanceOf(VirtualDevice.BucketDevice.class);

                    var plan = new PersistencePlanner().createPlan(effective, targetDevice);
                    assertThat(plan.effectivePolicy().storageClassId()).isEqualTo(StorageClassId.STANDARD);
                    assertThat(plan.steps()).extracting(StepPlan::stepId).containsExactly(
                            StepId.DEDUP,
                            StepId.COMPRESS,
                            StepId.CRYPT,
                            StepId.ERASURE_CODING,
                            StepId.REPLICATION,
                            StepId.STORE);
                    assertThat(plan.steps()).extracting(StepPlan::expectedStatus).containsExactly(
                            StepExecutionStatus.SKIPPED,
                            StepExecutionStatus.SKIPPED,
                            StepExecutionStatus.SKIPPED,
                            StepExecutionStatus.EXECUTED,
                            StepExecutionStatus.EXECUTED,
                            StepExecutionStatus.EXECUTED);
                    assertThat(plan.steps().get(3).config()).isPresent();
                    assertThat(plan.steps().get(4).config()).isPresent();

                    var repeatedPlan = new PersistencePlanner().createPlan(effective, targetDevice);
                    assertThat(repeatedPlan.deviceHash()).isEqualTo(plan.deviceHash());
                    assertThat(repeatedPlan.steps()).extracting(StepPlan::stepId)
                            .containsExactlyElementsOf(plan.steps().stream().map(StepPlan::stepId).toList());
                    assertThat(repeatedPlan.steps()).extracting(StepPlan::expectedStatus)
                            .containsExactlyElementsOf(plan.steps().stream().map(StepPlan::expectedStatus).toList());
                })
                .verifyComplete();

        StepVerifier.create(policyCatalog.findBy(StorageClassId.STANDARD))
                .assertNext(policy -> {
                    assertThat(policy.dedup()).isEmpty();
                    assertThat(policy.erasureCoding()).isPresent();
                })
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
                        .map(tuple -> {
                            assertThat(tuple.getT2()).hasSize(1);
                            return new VirtualDeviceResolver().selectDevice(tuple.getT1(), tuple.getT2());
                        }))
                .assertNext(selected -> assertThat(selected.id().value()).isEqualTo("local-disk-0"))
                .verifyComplete();
    }

    @Test
    void effectivePolicyResolverKeepsEcEnabledAndDedupDisabledForSmallObjects() {
        YamlStoragePolicyCatalog policyCatalog = YamlStoragePolicyCatalog.fromClasspath("storage-policies");
        long smallObjectSize = 65 * 1024L;

        StepVerifier.create(policyCatalog.findById("minio-standard")
                        .map(policy -> new EffectivePolicyResolver().resolve(
                                policy,
                                uploadContext(smallObjectSize))))
                .assertNext(effective -> {
                    assertThat(effective.dedup())
                            .as("MINIO_STANDARD dedup is disabled, not bypassed by chunk-size thresholds")
                            .isEmpty();
                    assertThat(effective.erasureCoding()).isPresent();
                    assertThat(effective.replication().factor()).isEqualTo(1);
                    assertThat(effective.compression()).isEmpty();
                    assertThat(effective.encryption()).isEmpty();
                })
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
