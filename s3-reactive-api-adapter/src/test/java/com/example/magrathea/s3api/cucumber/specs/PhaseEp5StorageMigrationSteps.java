package com.example.magrathea.s3api.cucumber.specs;

import com.example.magrathea.objectstore.domain.aggregate.Bucket;
import com.example.magrathea.objectstore.domain.aggregate.MultipartUpload;
import com.example.magrathea.objectstore.domain.valueobject.ObjectKey;
import com.example.magrathea.objectstore.domain.valueobject.UploadId;
import com.example.magrathea.objectstorage.repository.storageengine.adapter.StorageEngineReactiveMultipartUploadRepository;
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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class PhaseEp5StorageMigrationSteps {

    private Path manifestFile;
    private FileSystemManifestRepository repository;
    private ObjectManifest manifest;
    private String committedContent;
    private Path multipartRoot;
    private Path multipartStateFile;
    private MultipartUpload multipartUpload;
    private String committedMultipartContent;

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
