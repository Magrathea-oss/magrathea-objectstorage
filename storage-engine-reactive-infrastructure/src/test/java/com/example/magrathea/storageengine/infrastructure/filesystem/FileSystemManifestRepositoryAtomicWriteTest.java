package com.example.magrathea.storageengine.infrastructure.filesystem;

import com.example.magrathea.storageengine.application.exception.ManifestIntegrityException;
import com.example.magrathea.storageengine.domain.valueobject.BucketId;
import com.example.magrathea.storageengine.domain.valueobject.BucketRef;
import com.example.magrathea.storageengine.domain.valueobject.ChecksumAlgorithm;
import com.example.magrathea.storageengine.domain.valueobject.ChunkId;
import com.example.magrathea.storageengine.domain.valueobject.StorageArtifactReferenceDescriptor;
import com.example.magrathea.storageengine.domain.valueobject.ContentHash;
import com.example.magrathea.storageengine.domain.valueobject.DeviceConfigurationHash;
import com.example.magrathea.storageengine.domain.valueobject.EffectiveStoragePolicy;
import com.example.magrathea.storageengine.domain.valueobject.Fingerprint;
import com.example.magrathea.storageengine.domain.valueobject.FingerprintAlgorithm;
import com.example.magrathea.storageengine.domain.valueobject.ManifestId;
import com.example.magrathea.storageengine.domain.valueobject.NodeId;
import com.example.magrathea.storageengine.domain.valueobject.ObjectId;
import com.example.magrathea.storageengine.domain.valueobject.ObjectManifest;
import com.example.magrathea.storageengine.domain.valueobject.PersistencePlan;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for atomic manifest write and checksum verification in {@link FileSystemManifestRepository}.
 *
 * Covers:
 * <ul>
 *   <li>Save → load → parses correctly → checksum OK (happy path)</li>
 *   <li>No temp file remains after successful save</li>
 *   <li>Corrupting the committed manifest file → load → {@link ManifestIntegrityException}</li>
 *   <li>Removing the checksum trailer → load → {@link ManifestIntegrityException}</li>
 * </ul>
 */
class FileSystemManifestRepositoryAtomicWriteTest {

    @TempDir
    Path tempDir;

    private FileSystemManifestRepository repository() {
        return new FileSystemManifestRepository(tempDir.resolve("manifests"));
    }

    // ─────────────────────────────────────────────────────
    //  Happy-path: save → load → checksum OK
    // ─────────────────────────────────────────────────────

    @Test
    void saveThenLoadRoundTripsManifestCorrectly() {
        FileSystemManifestRepository repo = repository();
        ObjectManifest manifest = sampleManifest();

        StepVerifier.create(repo.save(manifest)).verifyComplete();

        StepVerifier.create(repo.findBy(manifest.manifestId()))
                .assertNext(restored -> {
                    assertThat(restored.manifestId()).isEqualTo(manifest.manifestId());
                    assertThat(restored.objectId()).isEqualTo(manifest.objectId());
                    assertThat(restored.versionId()).isEqualTo(manifest.versionId());
                    assertThat(restored.storageClassId()).isEqualTo(manifest.storageClassId());
                    assertThat(restored.chunkCount()).isEqualTo(manifest.chunkCount());
                    assertThat(restored.totalOriginalSize()).isEqualTo(manifest.totalOriginalSize());
                })
                .verifyComplete();
    }

    @Test
    void savedManifestFileContainsChecksumTrailer() throws IOException {
        FileSystemManifestRepository repo = repository();
        ObjectManifest manifest = sampleManifest();

        repo.save(manifest).block();

        Path manifestFile = tempDir.resolve("manifests")
                .resolve(manifest.manifestId().value() + ".properties");
        String content = Files.readString(manifestFile);

        assertThat(content).contains("manifest.checksum=");
    }

    // ─────────────────────────────────────────────────────
    //  No temp files remain after successful save
    // ─────────────────────────────────────────────────────

    @Test
    void noTempFilesRemainAfterSuccessfulSave() throws IOException {
        FileSystemManifestRepository repo = repository();
        ObjectManifest manifest = sampleManifest();

        repo.save(manifest).block();

        List<Path> tempFiles;
        try (var stream = Files.list(tempDir.resolve("manifests"))) {
            tempFiles = stream
                    .filter(f -> f.getFileName().toString().contains(".tmp."))
                    .toList();
        }

        assertThat(tempFiles).isEmpty();
    }

    // ─────────────────────────────────────────────────────
    //  Corruption detection
    // ─────────────────────────────────────────────────────

    @Test
    void corruptingManifestBytesThrowsManifestIntegrityException() throws IOException {
        FileSystemManifestRepository repo = repository();
        ObjectManifest manifest = sampleManifest();

        repo.save(manifest).block();

        Path manifestFile = tempDir.resolve("manifests")
                .resolve(manifest.manifestId().value() + ".properties");

        // Read original content and corrupt it by prepending garbage
        String original = Files.readString(manifestFile);
        Files.writeString(manifestFile, "CORRUPTED_PREFIX\n" + original);

        StepVerifier.create(repo.findBy(manifest.manifestId()))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(ManifestIntegrityException.class);
                    assertThat(error.getMessage()).contains(manifest.manifestId().value().toString());
                })
                .verify();
    }

    @Test
    void removingChecksumTrailerThrowsManifestIntegrityException() throws IOException {
        FileSystemManifestRepository repo = repository();
        ObjectManifest manifest = sampleManifest();

        repo.save(manifest).block();

        Path manifestFile = tempDir.resolve("manifests")
                .resolve(manifest.manifestId().value() + ".properties");

        // Strip the manifest.checksum= trailer line
        String original = Files.readString(manifestFile);
        int idx = original.lastIndexOf("\nmanifest.checksum=");
        String stripped = original.substring(0, idx + 1); // keep the \n before it
        Files.writeString(manifestFile, stripped);

        StepVerifier.create(repo.findBy(manifest.manifestId()))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(ManifestIntegrityException.class);
                    assertThat(error.getMessage()).contains("checksum trailer missing");
                })
                .verify();
    }

    @Test
    void partialManifestContentChecksumMismatch() throws IOException {
        FileSystemManifestRepository repo = repository();
        ObjectManifest manifest = sampleManifest();

        repo.save(manifest).block();

        Path manifestFile = tempDir.resolve("manifests")
                .resolve(manifest.manifestId().value() + ".properties");

        // Inject a valid-looking but wrong checksum (flip a hex digit)
        String original = Files.readString(manifestFile);
        int idx = original.lastIndexOf("\nmanifest.checksum=");
        String trailer = original.substring(idx + 1);
        // Corrupt the last character of the hex checksum
        String corruptedTrailer = trailer.substring(0, trailer.length() - 2)
                + "xx\n"; // replace last hex digits with 'xx'
        Files.writeString(manifestFile, original.substring(0, idx + 1) + corruptedTrailer);

        StepVerifier.create(repo.findBy(manifest.manifestId()))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(ManifestIntegrityException.class);
                    assertThat(error.getMessage()).contains("checksum mismatch");
                })
                .verify();
    }

    // ─────────────────────────────────────────────────────
    //  buildContentWithChecksum helper test
    // ─────────────────────────────────────────────────────

    @Test
    void buildContentWithChecksumAppendsVerifiableTrailer() {
        String serialized = "#comment\nkey=value\n";
        String withChecksum = FileSystemManifestRepository.buildContentWithChecksum(serialized);

        assertThat(withChecksum).contains("manifest.checksum=");

        int idx = withChecksum.lastIndexOf("\nmanifest.checksum=");
        assertThat(idx).isGreaterThan(0);

        String contentForVerification = withChecksum.substring(0, idx + 1);
        String checksumLine = withChecksum.substring(idx + 1);
        String storedHex = checksumLine.substring("manifest.checksum=".length()).trim();
        String computedHex = FileSystemManifestRepository.sha256Hex(contentForVerification);

        assertThat(storedHex).isEqualTo(computedHex);
    }

    // ─────────────────────────────────────────────────────
    //  Fixtures
    // ─────────────────────────────────────────────────────

    private ObjectManifest sampleManifest() {
        BucketRef bucketRef = BucketRef.of(BucketId.of("test-bucket"), "test-bucket");
        StorageClassId storageClassId = StorageClassId.of("STANDARD");
        EffectiveStoragePolicy policy = EffectiveStoragePolicy.of(
                storageClassId, bucketRef, Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), ReplicationConfig.of(1));
        VirtualDevice device = new VirtualDevice.BucketDevice(bucketRef, policy);
        DeviceConfigurationHash deviceHash = DeviceConfigurationHash.of("device-hash-abc123");

        ContentHash contentHash = ContentHash.of(ChecksumAlgorithm.SHA256, "abc123def456");
        ChunkId chunkId = ChunkId.generate();
        StorageArtifactReferenceDescriptor chunk = new StorageArtifactReferenceDescriptor(
                chunkId,
                Fingerprint.of(FingerprintAlgorithm.SHA256, "fp-abc123"),
                42L,
                42L,
                List.of(StepChecksumDescriptor.of(StepId.STORE, contentHash, contentHash)),
                contentHash,
                List.of(NodeId.of("node-001")));

        UploadCompletionTrace uploadTrace = new UploadCompletionTrace(
                UploadMode.SINGLE_OBJECT,
                Optional.empty(),
                ContentHash.of(ChecksumAlgorithm.SHA256, "consolidated-hash"),
                true,
                42L,
                true,
                Optional.empty());

        return new ObjectManifest(
                ManifestId.generate(),
                ObjectId.of("test-bucket/test-key"),
                VersionId.of("version-1"),
                storageClassId,
                device,
                deviceHash,
                uploadTrace,
                policyDecisions(),
                1,
                42L,
                42L,
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
