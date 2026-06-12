package com.example.magrathea.storageengine.infrastructure.filesystem;

import com.example.magrathea.storageengine.application.port.ObjectManifestRepository;
import com.example.magrathea.storageengine.domain.valueobject.BucketId;
import com.example.magrathea.storageengine.domain.valueobject.BucketRef;
import com.example.magrathea.storageengine.domain.valueobject.ChecksumAlgorithm;
import com.example.magrathea.storageengine.domain.valueobject.ChunkId;
import com.example.magrathea.storageengine.domain.valueobject.ChunkReferenceDescriptor;
import com.example.magrathea.storageengine.domain.valueobject.ContentHash;
import com.example.magrathea.storageengine.domain.valueobject.DeviceConfigurationHash;
import com.example.magrathea.storageengine.domain.valueobject.EffectiveStoragePolicy;
import com.example.magrathea.storageengine.domain.valueobject.Fingerprint;
import com.example.magrathea.storageengine.domain.valueobject.FingerprintAlgorithm;
import com.example.magrathea.storageengine.domain.valueobject.ManifestId;
import com.example.magrathea.storageengine.domain.valueobject.NodeId;
import com.example.magrathea.storageengine.domain.valueobject.ObjectId;
import com.example.magrathea.storageengine.domain.valueobject.ObjectManifest;
import com.example.magrathea.storageengine.domain.valueobject.PolicyDecision;
import com.example.magrathea.storageengine.domain.valueobject.PolicyDecisionReason;
import com.example.magrathea.storageengine.domain.valueobject.PolicyDecisionStatus;
import com.example.magrathea.storageengine.domain.valueobject.ReplicationConfig;
import com.example.magrathea.storageengine.domain.valueobject.StepChecksumDescriptor;
import com.example.magrathea.storageengine.domain.valueobject.StepId;
import com.example.magrathea.storageengine.domain.valueobject.StorageClassId;
import com.example.magrathea.storageengine.domain.valueobject.UploadCompletionTrace;
import com.example.magrathea.storageengine.domain.valueobject.UploadMode;
import com.example.magrathea.storageengine.domain.valueobject.VersionId;
import com.example.magrathea.storageengine.domain.valueobject.VirtualDevice;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;

/**
 * Filesystem-backed manifest repository.
 * Persists complete manifest fields needed for read-after-write reconstruction.
 */
public class FileSystemManifestRepository implements ObjectManifestRepository {

    private final Path manifestsRoot;

    public FileSystemManifestRepository(Path manifestsRoot) {
        this.manifestsRoot = java.util.Objects.requireNonNull(manifestsRoot, "manifestsRoot must not be null");
        try {
            Files.createDirectories(manifestsRoot);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create manifests directory: " + manifestsRoot, e);
        }
    }

    @Override
    public Mono<Void> save(ObjectManifest manifest) {
        return Mono.fromRunnable(() -> {
                    try {
                        Path manifestFile = manifestsRoot.resolve(manifest.manifestId().value() + ".properties");
                        Files.writeString(manifestFile, serialize(manifest),
                                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                    } catch (IOException e) {
                        throw new UncheckedIOException("Failed to save manifest", e);
                    }
                })
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    @Override
    public Mono<ObjectManifest> findBy(ManifestId manifestId) {
        return Mono.fromCallable(() -> {
                    Path manifestFile = manifestsRoot.resolve(manifestId.value() + ".properties");
                    if (!Files.exists(manifestFile)) {
                        // Backward compatibility with the previous extension during rolling upgrades.
                        manifestFile = manifestsRoot.resolve(manifestId.value() + ".json");
                    }
                    if (!Files.exists(manifestFile)) {
                        throw new java.util.NoSuchElementException("Manifest not found: " + manifestId.value());
                    }
                    return deserialize(Files.readString(manifestFile));
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    private String serialize(ObjectManifest manifest) {
        Properties properties = new Properties();
        properties.setProperty("manifestId", manifest.manifestId().value().toString());
        properties.setProperty("objectId", manifest.objectId().value());
        properties.setProperty("versionId", manifest.versionId().value());
        properties.setProperty("storageClassId", manifest.storageClassId().value());
        properties.setProperty("deviceHash", manifest.deviceHash().value());
        properties.setProperty("chunkCount", Integer.toString(manifest.chunkCount()));
        properties.setProperty("totalOriginalSize", Long.toString(manifest.totalOriginalSize()));
        properties.setProperty("totalStoredSize", Long.toString(manifest.totalStoredSize()));

        UploadCompletionTrace uploadTrace = manifest.uploadTrace();
        properties.setProperty("upload.mode", uploadTrace.uploadMode().name());
        properties.setProperty("upload.consolidatedChecksum.algorithm", uploadTrace.consolidatedChecksum().algorithm().name());
        properties.setProperty("upload.consolidatedChecksum.value", uploadTrace.consolidatedChecksum().value());
        properties.setProperty("upload.verificationPassed", Boolean.toString(uploadTrace.verificationPassed()));
        properties.setProperty("upload.totalObjectSize", Long.toString(uploadTrace.totalObjectSize()));
        properties.setProperty("upload.metadataValidated", Boolean.toString(uploadTrace.metadataValidated()));

        properties.setProperty("policyDecisionCount", Integer.toString(manifest.policyDecisions().size()));
        for (int i = 0; i < manifest.policyDecisions().size(); i++) {
            PolicyDecision decision = manifest.policyDecisions().get(i);
            String prefix = "policyDecision." + i + ".";
            properties.setProperty(prefix + "feature", decision.feature().name());
            properties.setProperty(prefix + "status", decision.status().name());
            properties.setProperty(prefix + "reason.code", decision.reason().code());
            properties.setProperty(prefix + "reason.description", decision.reason().description());
        }

        for (int i = 0; i < manifest.chunks().size(); i++) {
            ChunkReferenceDescriptor chunk = manifest.chunks().get(i);
            String prefix = "chunk." + i + ".";
            properties.setProperty(prefix + "chunkId", chunk.chunkId().value().toString());
            properties.setProperty(prefix + "fingerprint.algorithm", chunk.fingerprint().algorithm().name());
            properties.setProperty(prefix + "fingerprint.value", chunk.fingerprint().value());
            properties.setProperty(prefix + "originalSize", Long.toString(chunk.originalSize()));
            properties.setProperty(prefix + "storedSize", Long.toString(chunk.storedSize()));
            properties.setProperty(prefix + "finalChecksum.algorithm", chunk.finalChecksum().algorithm().name());
            properties.setProperty(prefix + "finalChecksum.value", chunk.finalChecksum().value());
            properties.setProperty(prefix + "locations", joinLocations(chunk.locations()));
            properties.setProperty(prefix + "stepChecksumCount", Integer.toString(chunk.stepChecksums().size()));
            for (int j = 0; j < chunk.stepChecksums().size(); j++) {
                StepChecksumDescriptor checksum = chunk.stepChecksums().get(j);
                String stepPrefix = prefix + "stepChecksum." + j + ".";
                properties.setProperty(stepPrefix + "stepId", checksum.stepId().name());
                properties.setProperty(stepPrefix + "input.algorithm", checksum.inputChecksum().algorithm().name());
                properties.setProperty(stepPrefix + "input.value", checksum.inputChecksum().value());
                properties.setProperty(stepPrefix + "output.algorithm", checksum.outputChecksum().algorithm().name());
                properties.setProperty(stepPrefix + "output.value", checksum.outputChecksum().value());
            }
        }

        try (StringWriter writer = new StringWriter()) {
            properties.store(writer, "Magrathea storage-engine object manifest");
            return writer.toString();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to serialize manifest", e);
        }
    }

    private ObjectManifest deserialize(String manifestData) {
        Properties properties = new Properties();
        try {
            properties.load(new StringReader(manifestData));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to parse manifest", e);
        }

        ManifestId manifestId = ManifestId.of(UUID.fromString(required(properties, "manifestId")));
        ObjectId objectId = ObjectId.of(required(properties, "objectId"));
        VersionId versionId = VersionId.of(required(properties, "versionId"));
        StorageClassId storageClassId = StorageClassId.of(required(properties, "storageClassId"));
        DeviceConfigurationHash deviceHash = DeviceConfigurationHash.of(required(properties, "deviceHash"));
        int chunkCount = Integer.parseInt(required(properties, "chunkCount"));
        long totalOriginalSize = Long.parseLong(required(properties, "totalOriginalSize"));
        long totalStoredSize = Long.parseLong(required(properties, "totalStoredSize"));

        UploadCompletionTrace uploadTrace = new UploadCompletionTrace(
                UploadMode.valueOf(required(properties, "upload.mode")),
                Optional.empty(),
                ContentHash.of(
                        ChecksumAlgorithm.valueOf(required(properties, "upload.consolidatedChecksum.algorithm")),
                        required(properties, "upload.consolidatedChecksum.value")),
                Boolean.parseBoolean(required(properties, "upload.verificationPassed")),
                Long.parseLong(required(properties, "upload.totalObjectSize")),
                Boolean.parseBoolean(required(properties, "upload.metadataValidated")),
                Optional.empty());

        List<PolicyDecision> policyDecisions = new ArrayList<>();
        int policyDecisionCount = Integer.parseInt(properties.getProperty("policyDecisionCount", "0"));
        for (int i = 0; i < policyDecisionCount; i++) {
            String prefix = "policyDecision." + i + ".";
            policyDecisions.add(PolicyDecision.of(
                    StepId.valueOf(required(properties, prefix + "feature")),
                    PolicyDecisionStatus.valueOf(required(properties, prefix + "status")),
                    PolicyDecisionReason.of(
                            required(properties, prefix + "reason.code"),
                            required(properties, prefix + "reason.description"))));
        }

        List<ChunkReferenceDescriptor> chunks = new ArrayList<>();
        for (int i = 0; i < chunkCount; i++) {
            String prefix = "chunk." + i + ".";
            List<StepChecksumDescriptor> stepChecksums = readStepChecksums(properties, prefix);
            chunks.add(new ChunkReferenceDescriptor(
                    ChunkId.of(UUID.fromString(required(properties, prefix + "chunkId"))),
                    Fingerprint.of(
                            FingerprintAlgorithm.valueOf(required(properties, prefix + "fingerprint.algorithm")),
                            required(properties, prefix + "fingerprint.value")),
                    Long.parseLong(required(properties, prefix + "originalSize")),
                    Long.parseLong(required(properties, prefix + "storedSize")),
                    stepChecksums,
                    ContentHash.of(
                            ChecksumAlgorithm.valueOf(required(properties, prefix + "finalChecksum.algorithm")),
                            required(properties, prefix + "finalChecksum.value")),
                    splitLocations(properties.getProperty(prefix + "locations", ""))));
        }

        return new ObjectManifest(
                manifestId,
                objectId,
                versionId,
                storageClassId,
                reconstructTargetDevice(objectId, storageClassId),
                deviceHash,
                uploadTrace,
                policyDecisions,
                chunkCount,
                totalOriginalSize,
                totalStoredSize,
                chunks);
    }

    private List<StepChecksumDescriptor> readStepChecksums(Properties properties, String prefix) {
        int count = Integer.parseInt(properties.getProperty(prefix + "stepChecksumCount", "0"));
        List<StepChecksumDescriptor> checksums = new ArrayList<>();
        for (int j = 0; j < count; j++) {
            String stepPrefix = prefix + "stepChecksum." + j + ".";
            checksums.add(StepChecksumDescriptor.of(
                    StepId.valueOf(required(properties, stepPrefix + "stepId")),
                    ContentHash.of(
                            ChecksumAlgorithm.valueOf(required(properties, stepPrefix + "input.algorithm")),
                            required(properties, stepPrefix + "input.value")),
                    ContentHash.of(
                            ChecksumAlgorithm.valueOf(required(properties, stepPrefix + "output.algorithm")),
                            required(properties, stepPrefix + "output.value"))));
        }
        return checksums;
    }

    private VirtualDevice reconstructTargetDevice(ObjectId objectId, StorageClassId storageClassId) {
        String objectValue = objectId.value();
        String bucketName = objectValue.contains("/") ? objectValue.substring(0, objectValue.indexOf('/')) : "restored";
        BucketRef bucketRef = BucketRef.of(BucketId.of(bucketName), bucketName);
        EffectiveStoragePolicy policy = EffectiveStoragePolicy.of(
                storageClassId,
                bucketRef,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                ReplicationConfig.of(1));
        return new VirtualDevice.BucketDevice(bucketRef, policy);
    }

    private String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Manifest is missing required property: " + key);
        }
        return value;
    }

    private String joinLocations(List<NodeId> locations) {
        return locations.stream()
                .map(NodeId::value)
                .collect(java.util.stream.Collectors.joining(","));
    }

    private List<NodeId> splitLocations(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(value.split(","))
                .filter(item -> !item.isBlank())
                .map(NodeId::of)
                .toList();
    }
}
