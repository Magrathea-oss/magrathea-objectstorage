package com.example.magrathea.s3api.cucumber.specs;

import com.example.magrathea.objectstore.domain.aggregate.Bucket;
import com.example.magrathea.objectstore.domain.aggregate.MultipartUpload;
import com.example.magrathea.objectstore.domain.valueobject.LegalHold;
import com.example.magrathea.objectstore.domain.valueobject.ObjectKey;
import com.example.magrathea.objectstore.domain.valueobject.Region;
import com.example.magrathea.objectstore.domain.valueobject.StorageClass;
import com.example.magrathea.objectstore.domain.valueobject.UploadId;
import com.example.magrathea.objectstorage.repository.storageengine.adapter.StorageEngineReactiveBucketRepository;
import com.example.magrathea.objectstorage.repository.storageengine.adapter.StorageEngineReactiveMultipartUploadRepository;
import com.example.magrathea.objectstorage.repository.storageengine.adapter.StorageEngineReactiveS3ObjectRepository;
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
import com.example.magrathea.storageengine.infrastructure.filesystem.FileSystemManifestRepository;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class PhaseEp5StorageMigrationSteps {

    @Autowired
    private WebTestClient webTestClient;

    private Path manifestFile;
    private FileSystemManifestRepository repository;
    private ObjectManifest manifest;
    private String committedContent;
    private Path multipartRoot;
    private Path multipartStateFile;
    private MultipartUpload multipartUpload;
    private String committedMultipartContent;
    private Path bucketRoot;
    private Path bucketStateFile;
    private Bucket bucket;
    private String committedBucketContent;
    private Path objectConfigRoot;
    private Path objectConfigStateFile;
    private ObjectKey objectConfigKey;
    private LegalHold objectLegalHold;
    private String committedObjectConfigContent;
    private Path objectReferenceRoot;
    private Path objectReferenceStateFile;
    private ObjectKey objectReferenceKey;
    private String committedObjectReferenceContent;

    @Given("a sample storage-engine object manifest is saved through the filesystem manifest repository")
    public void sampleStorageEngineObjectManifestIsSaved() throws IOException {
        Path root = Files.createTempDirectory("magrathea-ep5-manifest-schema-");
        repository = new FileSystemManifestRepository(root.resolve("manifests"));
        manifest = sampleManifest();
        repository.save(manifest).block();
        manifestFile = root.resolve("manifests").resolve(manifest.manifestId().value() + ".properties");
        committedContent = Files.readString(manifestFile);
    }

    @Then("the committed manifest file declares schema version {string}")
    public void committedManifestFileDeclaresSchemaVersion(String version) {
        assertThat(committedContent).contains("manifest.schemaVersion=" + version);
        ObjectManifest restored = repository.findBy(manifest.manifestId()).block();
        assertThat(restored).isNotNull();
        assertThat(restored.manifestId()).isEqualTo(manifest.manifestId());
    }

    @Then("the repository can read a legacy manifest that omits the schema version as compatibility version {string}")
    public void repositoryCanReadLegacyManifestThatOmitsSchemaVersion(String compatibilityVersion) throws IOException {
        assertThat(compatibilityVersion).isEqualTo("0");
        String legacyContent = withRecomputedChecksum(removeSchemaVersion(committedContent));
        Files.writeString(manifestFile, legacyContent);

        ObjectManifest restored = repository.findBy(manifest.manifestId()).block();

        assertThat(restored).isNotNull();
        assertThat(restored.manifestId()).isEqualTo(manifest.manifestId());
        assertThat(restored.objectId()).isEqualTo(manifest.objectId());
    }

    @Then("the repository rejects a manifest that declares unsupported schema version {string}")
    public void repositoryRejectsManifestThatDeclaresUnsupportedSchemaVersion(String unsupportedVersion) throws IOException {
        String unsupportedContent = withRecomputedChecksum(replaceSchemaVersion(committedContent, unsupportedVersion));
        Files.writeString(manifestFile, unsupportedContent);

        assertThatThrownBy(() -> repository.findBy(manifest.manifestId()).block())
            .hasMessageContaining("Unsupported manifest schema version")
            .hasMessageContaining(unsupportedVersion);
    }

    @Given("a sample multipart upload session is saved through the storage-engine repository")
    public void sampleMultipartUploadSessionIsSavedThroughStorageEngineRepository() throws IOException {
        multipartRoot = Files.createTempDirectory("magrathea-ep5-multipart-schema-");
        StorageEngineReactiveMultipartUploadRepository multipartRepository =
            new StorageEngineReactiveMultipartUploadRepository(null, multipartRoot.toString());
        multipartUpload = MultipartUpload.create(
            MultipartUpload.Id.generate(),
            Bucket.Id.of("ep5-multipart-schema-bucket"),
            ObjectKey.of("ep5-multipart-schema-bucket", "objects/schema-versioned.bin"),
            UploadId.generate());
        multipartRepository.save(multipartUpload).block();
        Path stateRoot = multipartRoot.resolve("metadata/multipart-uploads");
        try (var files = Files.list(stateRoot)) {
            multipartStateFile = files.filter(path -> path.getFileName().toString().endsWith(".json"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("multipart state JSON was not committed"));
        }
        committedMultipartContent = Files.readString(multipartStateFile);
    }

    @Then("the committed multipart upload state declares schema version {string}")
    public void committedMultipartUploadStateDeclaresSchemaVersion(String version) {
        assertThat(committedMultipartContent).contains("\"schemaVersion\":" + version);
    }

    @Then("the repository can read legacy multipart state that omits the schema version as compatibility version {string}")
    public void repositoryCanReadLegacyMultipartStateThatOmitsSchemaVersion(String compatibilityVersion)
            throws IOException {
        assertThat(compatibilityVersion).isEqualTo("0");
        String legacy = committedMultipartContent.replaceFirst("\"schemaVersion\":1,", "");
        assertThat(legacy).doesNotContain("\"schemaVersion\"");
        Files.writeString(multipartStateFile, legacy);

        StorageEngineReactiveMultipartUploadRepository legacyRepository =
            new StorageEngineReactiveMultipartUploadRepository(null, multipartRoot.toString());
        MultipartUpload restored = legacyRepository.findById(multipartUpload.uploadId()).block();
        assertThat(restored).isNotNull();
        assertThat(restored.uploadId()).isEqualTo(multipartUpload.uploadId());
        assertThat(restored.key()).isEqualTo(multipartUpload.key());
    }

    @Then("the repository rejects multipart state that declares unsupported schema version {string}")
    public void repositoryRejectsMultipartStateThatDeclaresUnsupportedSchemaVersion(String unsupportedVersion)
            throws IOException {
        String unsupported = committedMultipartContent.replaceFirst(
            "\"schemaVersion\":1", "\"schemaVersion\":" + unsupportedVersion);
        Files.writeString(multipartStateFile, unsupported);

        assertThatThrownBy(() -> new StorageEngineReactiveMultipartUploadRepository(
                null, multipartRoot.toString()))
            .hasMessageContaining("Unsupported multipart upload schema version")
            .hasMessageContaining(unsupportedVersion);
    }

    @Given("a sample bucket is saved through the storage-engine repository")
    public void sampleBucketIsSavedThroughStorageEngineRepository() throws IOException {
        bucketRoot = Files.createTempDirectory("magrathea-ep5-bucket-schema-");
        StorageEngineReactiveBucketRepository bucketRepository =
            new StorageEngineReactiveBucketRepository(null, bucketRoot.toString());
        bucket = Bucket.create(
            Bucket.Id.generate(), "ep5-schema-registry-bucket", Region.EU_WEST_1, StorageClass.STANDARD);
        bucketRepository.save(bucket).block();
        Path stateRoot = bucketRoot.resolve("metadata/buckets");
        try (var files = Files.list(stateRoot)) {
            bucketStateFile = files.filter(path -> path.getFileName().toString().endsWith(".json"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("bucket registry JSON was not committed"));
        }
        committedBucketContent = Files.readString(bucketStateFile);
    }

    @Then("the committed bucket registry state declares schema version {string}")
    public void committedBucketRegistryStateDeclaresSchemaVersion(String version) {
        assertThat(committedBucketContent).contains("\"schemaVersion\":" + version);
    }

    @Then("the repository can read legacy bucket state that omits the schema version as compatibility version {string}")
    public void repositoryCanReadLegacyBucketStateThatOmitsSchemaVersion(String compatibilityVersion)
            throws IOException {
        assertThat(compatibilityVersion).isEqualTo("0");
        String legacy = committedBucketContent.replaceFirst("\"schemaVersion\":1,", "");
        assertThat(legacy).doesNotContain("\"schemaVersion\"");
        Files.writeString(bucketStateFile, legacy);

        StorageEngineReactiveBucketRepository legacyRepository =
            new StorageEngineReactiveBucketRepository(null, bucketRoot.toString());
        Bucket restored = legacyRepository.findByName(bucket.name()).block();
        assertThat(restored).isNotNull();
        assertThat(restored.id()).isEqualTo(bucket.id());
        assertThat(restored.name()).isEqualTo(bucket.name());
    }

    @Then("the repository rejects bucket state that declares unsupported schema version {string}")
    public void repositoryRejectsBucketStateThatDeclaresUnsupportedSchemaVersion(String unsupportedVersion)
            throws IOException {
        String unsupported = committedBucketContent.replaceFirst(
            "\"schemaVersion\":1", "\"schemaVersion\":" + unsupportedVersion);
        Files.writeString(bucketStateFile, unsupported);

        assertThatThrownBy(() -> new StorageEngineReactiveBucketRepository(
                null, bucketRoot.toString()))
            .hasMessageContaining("Unsupported bucket registry schema version")
            .hasMessageContaining(unsupportedVersion);
    }

    @Given("sample object configuration is saved through the storage-engine repository")
    public void sampleObjectConfigurationIsSavedThroughStorageEngineRepository() throws IOException {
        objectConfigRoot = Files.createTempDirectory("magrathea-ep5-object-config-schema-");
        StorageEngineReactiveS3ObjectRepository objectRepository =
            new StorageEngineReactiveS3ObjectRepository(null, null, objectConfigRoot.toString());
        objectConfigKey = ObjectKey.of("ep5-object-config-bucket", "records/versioned.json");
        objectLegalHold = LegalHold.restore(true, Instant.parse("2026-07-11T00:00:00Z"));
        objectRepository.saveLegalHold(objectConfigKey.bucket(), objectConfigKey, objectLegalHold).block();

        Path stateRoot = objectConfigRoot.resolve("metadata/object-config");
        try (var files = Files.walk(stateRoot)) {
            objectConfigStateFile = files.filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().endsWith(".json"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("object configuration JSON was not committed"));
        }
        committedObjectConfigContent = Files.readString(objectConfigStateFile);
    }

    @Then("the committed object configuration state declares schema version {string}")
    public void committedObjectConfigurationStateDeclaresSchemaVersion(String version) {
        assertThat(committedObjectConfigContent).contains("\"schemaVersion\":" + version);
    }

    @Then("the repository can read legacy object configuration that omits the schema version as compatibility version {string}")
    public void repositoryCanReadLegacyObjectConfigurationThatOmitsSchemaVersion(String compatibilityVersion)
            throws IOException {
        assertThat(compatibilityVersion).isEqualTo("0");
        String legacy = committedObjectConfigContent.replaceFirst("\"schemaVersion\":1,", "");
        assertThat(legacy).doesNotContain("\"schemaVersion\"");
        Files.writeString(objectConfigStateFile, legacy);

        StorageEngineReactiveS3ObjectRepository legacyRepository =
            new StorageEngineReactiveS3ObjectRepository(null, null, objectConfigRoot.toString());
        LegalHold restored = legacyRepository.findLegalHold(
            objectConfigKey.bucket(), objectConfigKey).block();
        assertThat(restored).isEqualTo(objectLegalHold);
    }

    @Then("the repository rejects object configuration that declares unsupported schema version {string}")
    public void repositoryRejectsObjectConfigurationThatDeclaresUnsupportedSchemaVersion(String unsupportedVersion)
            throws IOException {
        String unsupported = committedObjectConfigContent.replaceFirst(
            "\"schemaVersion\":1", "\"schemaVersion\":" + unsupportedVersion);
        Files.writeString(objectConfigStateFile, unsupported);

        StorageEngineReactiveS3ObjectRepository unsupportedRepository =
            new StorageEngineReactiveS3ObjectRepository(null, null, objectConfigRoot.toString());
        assertThatThrownBy(() -> unsupportedRepository.findLegalHold(
                objectConfigKey.bucket(), objectConfigKey).block())
            .hasMessageContaining("Unsupported object configuration schema version")
            .hasMessageContaining(unsupportedVersion);
    }

    @Given("a sample object manifest reference is saved through the storage-engine S3 path")
    public void sampleObjectManifestReferenceIsSavedThroughStorageEngineS3Path() throws IOException {
        objectReferenceRoot = Path.of("target/storage-engine-it/current");
        String bucket = "ep5-reference-schema-" + java.util.UUID.randomUUID().toString().substring(0, 8);
        objectReferenceKey = ObjectKey.of(bucket, "records/versioned-reference.bin");

        webTestClient.put().uri("/" + bucket)
            .exchange()
            .expectStatus().is2xxSuccessful();
        webTestClient.put().uri("/" + bucket + "/records/versioned-reference.bin")
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .bodyValue("versioned object reference")
            .exchange()
            .expectStatus().is2xxSuccessful();

        objectReferenceStateFile = objectReferenceRoot
            .resolve("metadata/s3-object-references")
            .resolve(base64Url(bucket))
            .resolve(base64Url(objectReferenceKey.key()) + ".properties");
        assertThat(objectReferenceStateFile).exists();
        committedObjectReferenceContent = Files.readString(objectReferenceStateFile);
    }

    @Then("the committed object manifest reference declares schema version {string}")
    public void committedObjectManifestReferenceDeclaresSchemaVersion(String version) {
        assertThat(committedObjectReferenceContent).contains("reference.schemaVersion=" + version);
    }

    @Then("the repository can read a legacy object reference that omits the schema version as compatibility version {string}")
    public void repositoryCanReadLegacyObjectReferenceThatOmitsSchemaVersion(String compatibilityVersion)
            throws IOException {
        assertThat(compatibilityVersion).isEqualTo("0");
        String legacy = committedObjectReferenceContent.replaceFirst(
            "(?m)^reference\\.schemaVersion=1\\R", "");
        assertThat(legacy).doesNotContain("reference.schemaVersion");
        Files.writeString(objectReferenceStateFile, legacy);

        StorageEngineReactiveS3ObjectRepository legacyRepository =
            new StorageEngineReactiveS3ObjectRepository(null, null, objectReferenceRoot.toString());
        var restored = legacyRepository.findByBucketAndKey(objectReferenceKey).block();
        assertThat(restored).isNotNull();
        assertThat(restored.key()).isEqualTo(objectReferenceKey);
    }

    @Then("the repository rejects an object reference that declares unsupported schema version {string}")
    public void repositoryRejectsObjectReferenceThatDeclaresUnsupportedSchemaVersion(String unsupportedVersion)
            throws IOException {
        String unsupported = committedObjectReferenceContent.replaceFirst(
            "reference\\.schemaVersion=1", "reference.schemaVersion=" + unsupportedVersion);
        Files.writeString(objectReferenceStateFile, unsupported);

        StorageEngineReactiveS3ObjectRepository unsupportedRepository =
            new StorageEngineReactiveS3ObjectRepository(null, null, objectReferenceRoot.toString());
        assertThatThrownBy(() -> unsupportedRepository.findByBucketAndKey(objectReferenceKey).block())
            .hasMessageContaining("Unsupported object manifest reference schema version")
            .hasMessageContaining(unsupportedVersion);
    }

    private static String base64Url(String value) {
        return Base64.getUrlEncoder().withoutPadding()
            .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String removeSchemaVersion(String content) {
        String data = contentWithoutChecksum(content);
        StringBuilder builder = new StringBuilder();
        for (String line : data.split("\\n", -1)) {
            if (!line.startsWith("manifest.schemaVersion=")) {
                builder.append(line).append('\n');
            }
        }
        return normalizeSingleTrailingNewline(builder.toString());
    }

    private static String replaceSchemaVersion(String content, String version) {
        String data = contentWithoutChecksum(content);
        String replaced = data.replaceAll("(?m)^manifest\\.schemaVersion=.*$", "manifest.schemaVersion=" + version);
        if (!replaced.contains("manifest.schemaVersion=")) {
            replaced = "manifest.schemaVersion=" + version + "\n" + replaced;
        }
        return normalizeSingleTrailingNewline(replaced);
    }

    private static String contentWithoutChecksum(String content) {
        int idx = content.lastIndexOf("\nmanifest.checksum=");
        assertThat(idx).as("manifest checksum trailer index").isGreaterThanOrEqualTo(0);
        return content.substring(0, idx + 1);
    }

    private static String withRecomputedChecksum(String manifestData) {
        String normalized = normalizeSingleTrailingNewline(manifestData);
        return normalized + "manifest.checksum=" + sha256Hex(normalized) + "\n";
    }

    private static String normalizeSingleTrailingNewline(String value) {
        return value.replaceAll("\\n+$", "") + "\n";
    }

    private static String sha256Hex(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private ObjectManifest sampleManifest() {
        BucketRef bucketRef = BucketRef.of(BucketId.of("ep5-schema-bucket"), "ep5-schema-bucket");
        StorageClassId storageClassId = StorageClassId.of("STANDARD");
        EffectiveStoragePolicy policy = EffectiveStoragePolicy.of(
            storageClassId, bucketRef, Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.empty(), ReplicationConfig.of(1));
        VirtualDevice device = new VirtualDevice.BucketDevice(bucketRef, policy);
        DeviceConfigurationHash deviceHash = DeviceConfigurationHash.of("ep5-schema-device-hash");

        ContentHash contentHash = ContentHash.of(ChecksumAlgorithm.SHA256, "ep5-schema-content-hash");
        ChunkReferenceDescriptor chunk = new ChunkReferenceDescriptor(
            ChunkId.generate(),
            Fingerprint.of(FingerprintAlgorithm.SHA256, "ep5-schema-fingerprint"),
            64L,
            64L,
            List.of(StepChecksumDescriptor.of(StepId.STORE, contentHash, contentHash)),
            contentHash,
            List.of(NodeId.of("node-001")));

        UploadCompletionTrace uploadTrace = new UploadCompletionTrace(
            UploadMode.SINGLE_OBJECT,
            Optional.empty(),
            ContentHash.of(ChecksumAlgorithm.SHA256, "ep5-schema-consolidated"),
            true,
            64L,
            true,
            Optional.empty());

        return new ObjectManifest(
            ManifestId.generate(),
            ObjectId.of("ep5-schema-bucket/objects/schema-versioned.txt"),
            VersionId.of("version-1"),
            storageClassId,
            device,
            deviceHash,
            uploadTrace,
            policyDecisions(),
            1,
            64L,
            64L,
            List.of(chunk));
    }

    private List<PolicyDecision> policyDecisions() {
        return List.of(
            PolicyDecision.of(StepId.DEDUP, PolicyDecisionStatus.DISABLED,
                PolicyDecisionReason.of("DISABLED", "DISABLED")),
            PolicyDecision.of(StepId.COMPRESS, PolicyDecisionStatus.DISABLED,
                PolicyDecisionReason.of("DISABLED", "DISABLED")),
            PolicyDecision.of(StepId.CRYPT, PolicyDecisionStatus.DISABLED,
                PolicyDecisionReason.of("DISABLED", "DISABLED")),
            PolicyDecision.of(StepId.ERASURE_CODING, PolicyDecisionStatus.DISABLED,
                PolicyDecisionReason.of("DISABLED", "DISABLED")),
            PolicyDecision.of(StepId.REPLICATION, PolicyDecisionStatus.ENABLED,
                PolicyDecisionReason.of("ENABLED", "ENABLED")),
            PolicyDecision.of(StepId.STORE, PolicyDecisionStatus.ENABLED,
                PolicyDecisionReason.of("ENABLED", "ENABLED")));
    }
}
