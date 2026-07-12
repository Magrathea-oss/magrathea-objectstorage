package com.example.magrathea.storageengine.infrastructure.filesystem;

import com.example.magrathea.storageengine.application.exception.ManifestIntegrityException;
import com.example.magrathea.storageengine.application.port.ObjectManifestRepository;
import com.example.magrathea.storageengine.domain.valueobject.BucketId;
import com.example.magrathea.storageengine.domain.valueobject.BucketRef;
import com.example.magrathea.storageengine.domain.valueobject.ChecksumAlgorithm;
import com.example.magrathea.storageengine.domain.valueobject.ChunkId;
import com.example.magrathea.storageengine.domain.valueobject.StorageArtifactKind;
import com.example.magrathea.storageengine.domain.valueobject.StorageArtifactReferenceDescriptor;
import com.example.magrathea.storageengine.domain.valueobject.ContentHash;
import com.example.magrathea.storageengine.domain.valueobject.DeclaredChecksum;
import com.example.magrathea.storageengine.domain.valueobject.DeviceConfigurationHash;
import com.example.magrathea.storageengine.domain.valueobject.EffectiveStoragePolicy;
import com.example.magrathea.storageengine.domain.valueobject.Fingerprint;
import com.example.magrathea.storageengine.domain.valueobject.FingerprintAlgorithm;
import com.example.magrathea.storageengine.domain.valueobject.ManifestId;
import com.example.magrathea.storageengine.domain.valueobject.NodeId;
import com.example.magrathea.storageengine.domain.valueobject.ObjectId;
import com.example.magrathea.storageengine.domain.valueobject.ObjectManifest;
import com.example.magrathea.storageengine.domain.valueobject.PartChecksumResult;
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

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;

/**
 * Filesystem-backed manifest repository.
 * Persists complete manifest fields needed for read-after-write reconstruction.
 *
 * <h2>Atomic write protocol (REQ-FS-002)</h2>
 * Manifest content is written to a uniquely-named temp file and atomically renamed
 * to the final committed manifest path. An interrupted write never leaves a partial
 * file at the committed path.
 *
 * <h2>Manifest checksum (REQ-FS-004)</h2>
 * A SHA-256 checksum of the serialized manifest is appended as a
 * {@code manifest.checksum=<hex>} trailer line before writing. On every read the
 * checksum is verified before the manifest is parsed. A mismatch throws
 * {@link ManifestIntegrityException}.
 */
public class FileSystemManifestRepository implements ObjectManifestRepository {

    /** Current storage-engine manifest properties schema version. */
    static final int CURRENT_SCHEMA_VERSION = 2;
    /** Chunk-only schema written before typed storage artifacts. */
    static final int CHUNK_SCHEMA_VERSION = 1;
    /** Legacy compatibility version assigned to manifests written before schemaVersion existed. */
    static final int LEGACY_SCHEMA_VERSION = 0;
    /** Property key used for the manifest format schema version. */
    static final String SCHEMA_VERSION_KEY = "manifest.schemaVersion";
    /** Property key used for the manifest self-checksum trailer. */
    static final String CHECKSUM_KEY = "manifest.checksum";
    /** Separator that appears immediately before the checksum trailer line. */
    private static final String CHECKSUM_LINE_PREFIX = "\n" + CHECKSUM_KEY + "=";
    private static final String TMP_SUFFIX_FORMAT = ".tmp.";

    private final Path manifestsRoot;
    private final FileSystemWriteFaultInjector faultInjector;

    public FileSystemManifestRepository(Path manifestsRoot) {
        this(manifestsRoot, FileSystemWriteFaultInjector.disabled());
    }

    public FileSystemManifestRepository(Path manifestsRoot, FileSystemWriteFaultInjector faultInjector) {
        this.manifestsRoot = java.util.Objects.requireNonNull(manifestsRoot, "manifestsRoot must not be null");
        this.faultInjector = java.util.Objects.requireNonNull(faultInjector, "faultInjector must not be null");
        try {
            Files.createDirectories(manifestsRoot);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create manifests directory: " + manifestsRoot, e);
        }
    }

    public Path manifestsRoot() {
        return manifestsRoot;
    }

    @Override
    public Mono<Void> save(ObjectManifest manifest) {
        return BlockingFileSystemOperation.fromRunnable(() -> {
                    try {
                        Path finalPath = manifestsRoot.resolve(manifest.manifestId().value() + ".properties");
                        Path tempPath = manifestsRoot.resolve(
                                manifest.manifestId().value() + ".properties" + TMP_SUFFIX_FORMAT + UUID.randomUUID());

                        String finalContent = buildContentWithChecksum(serialize(manifest));

                        try {
                            Files.writeString(tempPath, finalContent, StandardOpenOption.CREATE_NEW);
                            try (FileChannel channel = FileChannel.open(tempPath, StandardOpenOption.WRITE)) {
                                channel.force(true);
                            }
                            faultInjector.afterManifestTempFileWritten(
                                    new FileSystemWriteFaultInjector.ManifestWriteContext(
                                            manifest.manifestId(), tempPath, finalPath,
                                            finalContent.getBytes(StandardCharsets.UTF_8).length));
                            Files.move(tempPath, finalPath,
                                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                        } catch (Exception e) {
                            if (!preserveTemporaryArtifacts(e)) {
                                try { Files.deleteIfExists(tempPath); } catch (Exception ignored) { /* best effort */ }
                            }
                            if (e instanceof IOException ioe) {
                                throw FileSystemCapacityErrors.translate(ioe, manifestsRoot.getParent().getParent(),
                                        finalContent.getBytes(StandardCharsets.UTF_8).length,
                                        "Atomic manifest write failed");
                            }
                            throw e;
                        }
                    } catch (IOException e) {
                        throw new UncheckedIOException("Failed to save manifest", e);
                    }
                });
    }

    @Override
    public Mono<ObjectManifest> findBy(ManifestId manifestId) {
        return BlockingFileSystemOperation.fromCallable(() -> {
                    Path manifestFile = manifestsRoot.resolve(manifestId.value() + ".properties");
                    if (!Files.exists(manifestFile)) {
                        // Backward compatibility with the previous extension during rolling upgrades.
                        manifestFile = manifestsRoot.resolve(manifestId.value() + ".json");
                    }
                    if (!Files.exists(manifestFile)) {
                        throw new java.util.NoSuchElementException("Manifest not found: " + manifestId.value());
                    }
                    String rawContent = Files.readString(manifestFile);
                    verifyManifestChecksum(rawContent, manifestId);
                    return deserialize(rawContent);
                });
    }

    // ─────────────────────────────────────────────────────
    //  Checksum helpers
    // ─────────────────────────────────────────────────────

    /**
     * Appends a {@code manifest.checksum=<sha256hex>} trailer to the serialized manifest.
     * The checksum is computed over the serialized Properties string (without the trailer).
     */
    private static boolean preserveTemporaryArtifacts(Exception e) {
        return e instanceof FileSystemWriteInterruptedException interrupted
                && interrupted.preserveTemporaryArtifacts();
    }

    static String buildContentWithChecksum(String serialized) {
        // Ensure the Properties output ends with exactly one newline before appending the trailer.
        String normalized = serialized.endsWith("\n") ? serialized : serialized + "\n";
        String checksumHex = sha256Hex(normalized);
        return normalized + CHECKSUM_KEY + "=" + checksumHex + "\n";
    }

    /**
     * Verifies the {@code manifest.checksum} trailer of the given raw file content.
     *
     * @throws ManifestIntegrityException if the trailer is absent or the checksum does not match.
     */
    static void verifyManifestChecksum(String rawContent, ManifestId manifestId) {
        int idx = rawContent.lastIndexOf(CHECKSUM_LINE_PREFIX);
        if (idx < 0) {
            // The manifest may be a legacy file written without a checksum — treat as missing
            throw new ManifestIntegrityException(
                    "Manifest checksum trailer missing for manifest: " + manifestId.value());
        }
        // Content that was checksummed = everything up to and including the \n before the trailer
        String contentForVerification = rawContent.substring(0, idx + 1);
        String checksumLine = rawContent.substring(idx + 1);              // "manifest.checksum=<hex>\n"
        String storedHex = checksumLine.substring(CHECKSUM_KEY.length() + 1).trim(); // strip key= and trailing whitespace
        String computedHex = sha256Hex(contentForVerification);
        if (!computedHex.equals(storedHex)) {
            throw new ManifestIntegrityException(
                    "Manifest checksum mismatch for manifest: " + manifestId.value()
                    + " — stored=" + storedHex + " computed=" + computedHex);
        }
    }

    static String sha256Hex(String content) {
        return sha256Hex(content.getBytes(StandardCharsets.UTF_8));
    }

    static String sha256Hex(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    // ─────────────────────────────────────────────────────
    //  Serialization
    // ─────────────────────────────────────────────────────

    private String serialize(ObjectManifest manifest) {
        Properties properties = new Properties();
        properties.setProperty(SCHEMA_VERSION_KEY, Integer.toString(CURRENT_SCHEMA_VERSION));
        properties.setProperty("manifestId", manifest.manifestId().value().toString());
        properties.setProperty("objectId", manifest.objectId().value());
        properties.setProperty("versionId", manifest.versionId().value());
        properties.setProperty("storageClassId", manifest.storageClassId().value());
        properties.setProperty("deviceHash", manifest.deviceHash().value());
        properties.setProperty("artifactCount", Integer.toString(manifest.artifactCount()));
        properties.setProperty("totalOriginalSize", Long.toString(manifest.totalOriginalSize()));
        properties.setProperty("totalStoredSize", Long.toString(manifest.totalStoredSize()));

        UploadCompletionTrace uploadTrace = manifest.uploadTrace();
        properties.setProperty("upload.mode", uploadTrace.uploadMode().name());
        uploadTrace.declaredChecksum().ifPresent(declaredChecksum -> {
            properties.setProperty("upload.declaredChecksum.present", "true");
            properties.setProperty("upload.declaredChecksum.algorithm", declaredChecksum.algorithm().name());
            properties.setProperty("upload.declaredChecksum.value", declaredChecksum.value());
        });
        properties.setProperty("upload.consolidatedChecksum.algorithm", uploadTrace.consolidatedChecksum().algorithm().name());
        properties.setProperty("upload.consolidatedChecksum.value", uploadTrace.consolidatedChecksum().value());
        properties.setProperty("upload.verificationPassed", Boolean.toString(uploadTrace.verificationPassed()));
        properties.setProperty("upload.totalObjectSize", Long.toString(uploadTrace.totalObjectSize()));
        properties.setProperty("upload.metadataValidated", Boolean.toString(uploadTrace.metadataValidated()));
        uploadTrace.partChecksumResults().ifPresent(partResults -> {
            properties.setProperty("upload.partChecksumResults.present", "true");
            properties.setProperty("upload.partChecksumResults.count", Integer.toString(partResults.size()));
            for (int i = 0; i < partResults.size(); i++) {
                PartChecksumResult partResult = partResults.get(i);
                String prefix = "upload.partChecksumResult." + i + ".";
                properties.setProperty(prefix + "partNumber", Integer.toString(partResult.partNumber()));
                properties.setProperty(prefix + "partSize", Long.toString(partResult.partSize()));
                partResult.declaredChecksum().ifPresent(declaredChecksum -> {
                    properties.setProperty(prefix + "declaredChecksum.present", "true");
                    properties.setProperty(prefix + "declaredChecksum.algorithm", declaredChecksum.algorithm().name());
                    properties.setProperty(prefix + "declaredChecksum.value", declaredChecksum.value());
                });
                properties.setProperty(prefix + "calculatedChecksum.algorithm", partResult.calculatedChecksum().algorithm().name());
                properties.setProperty(prefix + "calculatedChecksum.value", partResult.calculatedChecksum().value());
                properties.setProperty(prefix + "matched", Boolean.toString(partResult.matched()));
            }
        });

        properties.setProperty("policyDecisionCount", Integer.toString(manifest.policyDecisions().size()));
        for (int i = 0; i < manifest.policyDecisions().size(); i++) {
            PolicyDecision decision = manifest.policyDecisions().get(i);
            String prefix = "policyDecision." + i + ".";
            properties.setProperty(prefix + "feature", decision.feature().name());
            properties.setProperty(prefix + "status", decision.status().name());
            properties.setProperty(prefix + "reason.code", decision.reason().code());
            properties.setProperty(prefix + "reason.description", decision.reason().description());
        }

        for (int i = 0; i < manifest.artifacts().size(); i++) {
            StorageArtifactReferenceDescriptor artifact = manifest.artifacts().get(i);
            String prefix = "artifact." + i + ".";
            properties.setProperty(prefix + "kind", artifact.artifactKind().name());
            properties.setProperty(prefix + "artifactId", artifact.chunkId().value().toString());
            properties.setProperty(prefix + "fingerprint.algorithm", artifact.fingerprint().algorithm().name());
            properties.setProperty(prefix + "fingerprint.value", artifact.fingerprint().value());
            properties.setProperty(prefix + "originalSize", Long.toString(artifact.originalSize()));
            properties.setProperty(prefix + "storedSize", Long.toString(artifact.storedSize()));
            properties.setProperty(prefix + "finalChecksum.algorithm", artifact.finalChecksum().algorithm().name());
            properties.setProperty(prefix + "finalChecksum.value", artifact.finalChecksum().value());
            properties.setProperty(prefix + "locations", joinLocations(artifact.locations()));
            properties.setProperty(prefix + "stepChecksumCount", Integer.toString(artifact.stepChecksums().size()));
            for (int j = 0; j < artifact.stepChecksums().size(); j++) {
                StepChecksumDescriptor checksum = artifact.stepChecksums().get(j);
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
        int schemaVersion = readSchemaVersion(properties, manifestId);
        ObjectId objectId = ObjectId.of(required(properties, "objectId"));
        VersionId versionId = VersionId.of(required(properties, "versionId"));
        StorageClassId storageClassId = StorageClassId.of(required(properties, "storageClassId"));
        DeviceConfigurationHash deviceHash = DeviceConfigurationHash.of(required(properties, "deviceHash"));
        int artifactCount = Integer.parseInt(required(properties,
                schemaVersion >= CURRENT_SCHEMA_VERSION ? "artifactCount" : "chunkCount"));
        long totalOriginalSize = Long.parseLong(required(properties, "totalOriginalSize"));
        long totalStoredSize = Long.parseLong(required(properties, "totalStoredSize"));

        UploadCompletionTrace uploadTrace = new UploadCompletionTrace(
                UploadMode.valueOf(required(properties, "upload.mode")),
                readOptionalDeclaredChecksum(properties, "upload.declaredChecksum."),
                ContentHash.of(
                        ChecksumAlgorithm.valueOf(required(properties, "upload.consolidatedChecksum.algorithm")),
                        required(properties, "upload.consolidatedChecksum.value")),
                Boolean.parseBoolean(required(properties, "upload.verificationPassed")),
                Long.parseLong(required(properties, "upload.totalObjectSize")),
                Boolean.parseBoolean(required(properties, "upload.metadataValidated")),
                readOptionalPartChecksumResults(properties));

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

        List<StorageArtifactReferenceDescriptor> artifacts = new ArrayList<>();
        for (int i = 0; i < artifactCount; i++) {
            boolean typed = schemaVersion >= CURRENT_SCHEMA_VERSION;
            String prefix = (typed ? "artifact." : "chunk.") + i + ".";
            List<StepChecksumDescriptor> stepChecksums = readStepChecksums(properties, prefix);
            artifacts.add(new StorageArtifactReferenceDescriptor(
                    typed ? StorageArtifactKind.valueOf(required(properties, prefix + "kind"))
                            : StorageArtifactKind.LEGACY_CHUNK,
                    ChunkId.of(UUID.fromString(required(properties, prefix + (typed ? "artifactId" : "chunkId")))),
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
                artifactCount,
                totalOriginalSize,
                totalStoredSize,
                artifacts);
    }

    private Optional<DeclaredChecksum> readOptionalDeclaredChecksum(Properties properties, String prefix) {
        if (!Boolean.parseBoolean(properties.getProperty(prefix + "present", "false"))) {
            return Optional.empty();
        }
        return Optional.of(DeclaredChecksum.of(
                ChecksumAlgorithm.valueOf(required(properties, prefix + "algorithm")),
                required(properties, prefix + "value")));
    }

    private Optional<List<PartChecksumResult>> readOptionalPartChecksumResults(Properties properties) {
        if (!Boolean.parseBoolean(properties.getProperty("upload.partChecksumResults.present", "false"))) {
            return Optional.empty();
        }
        int count = Integer.parseInt(properties.getProperty("upload.partChecksumResults.count", "0"));
        List<PartChecksumResult> results = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String prefix = "upload.partChecksumResult." + i + ".";
            results.add(PartChecksumResult.of(
                    Integer.parseInt(required(properties, prefix + "partNumber")),
                    Long.parseLong(required(properties, prefix + "partSize")),
                    readOptionalDeclaredChecksum(properties, prefix + "declaredChecksum."),
                    ContentHash.of(
                            ChecksumAlgorithm.valueOf(required(properties, prefix + "calculatedChecksum.algorithm")),
                            required(properties, prefix + "calculatedChecksum.value")),
                    Boolean.parseBoolean(required(properties, prefix + "matched"))));
        }
        return Optional.of(List.copyOf(results));
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

    private int readSchemaVersion(Properties properties, ManifestId manifestId) {
        String rawVersion = properties.getProperty(SCHEMA_VERSION_KEY);
        if (rawVersion == null || rawVersion.isBlank()) {
            // Compatibility mode for manifests written before EP-5 schema versioning.
            return LEGACY_SCHEMA_VERSION;
        }
        int version;
        try {
            version = Integer.parseInt(rawVersion.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Invalid manifest schema version '" + rawVersion + "' for manifest: " + manifestId.value(), e);
        }
        if (version != LEGACY_SCHEMA_VERSION
                && version != CHUNK_SCHEMA_VERSION
                && version != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "Unsupported manifest schema version " + version
                            + " for manifest: " + manifestId.value()
                            + "; supported versions are " + LEGACY_SCHEMA_VERSION + ", "
                            + CHUNK_SCHEMA_VERSION + " and " + CURRENT_SCHEMA_VERSION);
        }
        return version;
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
