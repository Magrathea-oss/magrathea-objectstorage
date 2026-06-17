package com.example.magrathea.storageengine.infrastructure.filesystem;

import com.example.magrathea.storageengine.domain.valueobject.ChunkId;
import com.example.magrathea.storageengine.domain.valueobject.NodeId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link FileSystemRecoveryScanner}.
 *
 * <p>Covers:
 * <ul>
 *   <li>Empty storage root → zero findings</li>
 *   <li>Orphaned temp chunk file → finding type {@code orphaned-chunk}</li>
 *   <li>Committed chunk with corrupted checksum → finding type {@code checksum-mismatch}</li>
 *   <li>Incomplete/malformed manifest → finding type {@code incomplete-manifest}</li>
 *   <li>Object reference pointing to absent manifest → finding type {@code broken-reference}</li>
 *   <li>Quarantine clears findings → second scan produces zero findings (idempotency)</li>
 *   <li>Valid committed object is untouched after scan</li>
 * </ul>
 */
class FileSystemRecoveryScannerTest {

    @TempDir
    Path storageRoot;

    private FileSystemRecoveryScanner scanner;
    private Path chunksDir;
    private Path manifestsDir;
    private Path referencesDir;

    @BeforeEach
    void setUp() throws IOException {
        scanner = new FileSystemRecoveryScanner();

        // Standard storage-engine directory layout under storageRoot
        chunksDir = storageRoot.resolve("nodes").resolve("node-001").resolve("chunks");
        manifestsDir = storageRoot.resolve("metadata").resolve("manifests");
        referencesDir = storageRoot.resolve("metadata").resolve("s3-object-references");

        Files.createDirectories(chunksDir);
        Files.createDirectories(manifestsDir);
        Files.createDirectories(referencesDir);
    }

    // ─────────────────────────────────────────────────────
    //  Empty root → zero findings
    // ─────────────────────────────────────────────────────

    @Test
    void emptyStorageRootProducesZeroFindings() {
        FileSystemRecoveryScanner.ScanReport report = scanner.scan(storageRoot);
        assertThat(report.isEmpty()).isTrue();
        assertThat(report.findings()).isEmpty();
    }

    @Test
    void storageRootWithValidObjectProducesZeroFindings() throws IOException {
        writeValidChunk("valid-chunk-id");
        writeValidManifest("valid-manifest-id");
        writeReference("test-bucket", "test-key", "valid-manifest-id");

        FileSystemRecoveryScanner.ScanReport report = scanner.scan(storageRoot);
        assertThat(report.isEmpty())
                .as("Expected zero findings for a valid committed object, but got: %s", report.findings())
                .isTrue();
    }

    // ─────────────────────────────────────────────────────
    //  Orphaned temp chunk files → orphaned-chunk
    // ─────────────────────────────────────────────────────

    @Test
    void orphanedTempChunkFileProducesFindingOfTypeOrphanedChunk() throws IOException {
        String chunkId = UUID.randomUUID().toString();
        Path orphanedTempFile = chunksDir.resolve(chunkId + ".tmp." + UUID.randomUUID());
        Files.writeString(orphanedTempFile, "partial chunk data from interrupted write");

        FileSystemRecoveryScanner.ScanReport report = scanner.scan(storageRoot);

        assertThat(report.findings()).hasSize(1);
        assertThat(report.findings().get(0).artifactType()).isEqualTo("orphaned-chunk");
        assertThat(report.findings().get(0).artifactPath()).isEqualTo(orphanedTempFile.toString());
        assertThat(report.findings().get(0).reason()).contains("Orphaned");
    }

    @Test
    void orphanedTempChecksumFileWithTmpMarkerIsAlsoOrphanedChunk() throws IOException {
        String chunkId = UUID.randomUUID().toString();
        Path orphanedChecksumTemp = chunksDir.resolve(chunkId + ".sha256.tmp." + UUID.randomUUID());
        Files.writeString(orphanedChecksumTemp, "abc123");

        FileSystemRecoveryScanner.ScanReport report = scanner.scan(storageRoot);

        assertThat(report.findings()).hasSize(1);
        assertThat(report.findings().get(0).artifactType()).isEqualTo("orphaned-chunk");
    }

    // ─────────────────────────────────────────────────────
    //  Committed chunk with corrupted checksum → checksum-mismatch
    // ─────────────────────────────────────────────────────

    @Test
    void committedChunkWithCorruptedBytesProducesChecksumMismatch() throws IOException {
        // Write a valid chunk first
        String chunkId = UUID.randomUUID().toString();
        byte[] data = "original chunk bytes".getBytes(StandardCharsets.UTF_8);
        Path chunkFile = chunksDir.resolve(chunkId);
        Path sidecar = chunksDir.resolve(chunkId + ".sha256");
        Files.write(chunkFile, data);
        Files.writeString(sidecar, FileSystemStorageNode.sha256Hex(data));

        // Now corrupt the chunk file
        Files.writeString(chunkFile, "CORRUPTED BYTES INJECTED BY TEST");

        FileSystemRecoveryScanner.ScanReport report = scanner.scan(storageRoot);

        assertThat(report.findings()).hasSize(1);
        assertThat(report.findings().get(0).artifactType()).isEqualTo("checksum-mismatch");
        assertThat(report.findings().get(0).artifactPath()).isEqualTo(chunkFile.toString());
    }

    @Test
    void committedChunkWithMissingSidecarProducesChecksumMismatch() throws IOException {
        // Write chunk without a .sha256 sidecar
        Path chunkFile = chunksDir.resolve(UUID.randomUUID().toString());
        Files.writeString(chunkFile, "chunk data without checksum sidecar");

        FileSystemRecoveryScanner.ScanReport report = scanner.scan(storageRoot);

        assertThat(report.findings()).hasSize(1);
        assertThat(report.findings().get(0).artifactType()).isEqualTo("checksum-mismatch");
        assertThat(report.findings().get(0).reason()).contains("missing");
    }

    // ─────────────────────────────────────────────────────
    //  Incomplete/malformed manifest → incomplete-manifest
    // ─────────────────────────────────────────────────────

    @Test
    void manifestWithNoChecksumTrailerProducesIncompleteManifest() throws IOException {
        String manifestId = UUID.randomUUID().toString();
        // Write a manifest without the checksum trailer
        Path manifestFile = manifestsDir.resolve(manifestId + ".properties");
        Files.writeString(manifestFile, "#no checksum here\nmanifestId=" + manifestId + "\n");

        FileSystemRecoveryScanner.ScanReport report = scanner.scan(storageRoot);

        assertThat(report.findings()).hasSize(1);
        assertThat(report.findings().get(0).artifactType()).isEqualTo("incomplete-manifest");
        assertThat(report.findings().get(0).artifactPath()).isEqualTo(manifestFile.toString());
    }

    @Test
    void manifestTempFileIsIncompleteManifest() throws IOException {
        Path orphanedManifestTemp = manifestsDir.resolve(UUID.randomUUID() + ".properties.tmp." + UUID.randomUUID());
        Files.writeString(orphanedManifestTemp, "partial manifest data");

        FileSystemRecoveryScanner.ScanReport report = scanner.scan(storageRoot);

        assertThat(report.findings()).hasSize(1);
        assertThat(report.findings().get(0).artifactType()).isEqualTo("incomplete-manifest");
    }

    @Test
    void manifestWithWrongChecksumProducesIncompleteManifest() throws IOException {
        String manifestId = UUID.randomUUID().toString();
        Path manifestFile = manifestsDir.resolve(manifestId + ".properties");
        // Write valid-looking content with a wrong checksum
        Files.writeString(manifestFile,
                "#comment\nmanifestId=" + manifestId + "\n"
                + "manifest.checksum=00000000000000000000000000000000deadbeef00000000000000000000dead\n");

        FileSystemRecoveryScanner.ScanReport report = scanner.scan(storageRoot);

        assertThat(report.findings()).hasSize(1);
        assertThat(report.findings().get(0).artifactType()).isEqualTo("checksum-mismatch");
    }

    // ─────────────────────────────────────────────────────
    //  Object reference pointing to absent manifest → broken-reference
    // ─────────────────────────────────────────────────────

    @Test
    void referencePointingToAbsentManifestProducesBrokenReference() throws IOException {
        String absentManifestId = UUID.randomUUID().toString();
        writeReference("bucket-a", "key/object.bin", absentManifestId);

        FileSystemRecoveryScanner.ScanReport report = scanner.scan(storageRoot);

        assertThat(report.findings()).hasSize(1);
        assertThat(report.findings().get(0).artifactType()).isEqualTo("broken-reference");
        assertThat(report.findings().get(0).reason()).contains(absentManifestId);
    }

    @Test
    void referencePointingToExistingManifestProducesNoFinding() throws IOException {
        writeValidManifest("existing-manifest-id");
        writeReference("bucket-b", "key/object.bin", "existing-manifest-id");

        FileSystemRecoveryScanner.ScanReport report = scanner.scan(storageRoot);

        assertThat(report.isEmpty())
                .as("Expected zero findings but got: %s", report.findings())
                .isTrue();
    }

    // ─────────────────────────────────────────────────────
    //  Quarantine clears findings → idempotency
    // ─────────────────────────────────────────────────────

    @Test
    void quarantineClearsFindingsAndSecondScanProducesZeroFindings() throws IOException {
        // Set up one of each finding type
        String orphanId = UUID.randomUUID().toString();
        Path orphanedTempFile = chunksDir.resolve(orphanId + ".tmp." + UUID.randomUUID());
        Files.writeString(orphanedTempFile, "orphaned temp chunk");

        String badChunkId = UUID.randomUUID().toString();
        Path badChunk = chunksDir.resolve(badChunkId);
        Path badSidecar = chunksDir.resolve(badChunkId + ".sha256");
        Files.writeString(badChunk, "corrupted data");
        Files.writeString(badSidecar, "wrong_checksum_value");

        String badManifestId = UUID.randomUUID().toString();
        Path badManifest = manifestsDir.resolve(badManifestId + ".properties");
        Files.writeString(badManifest, "#no checksum\nmanifestId=" + badManifestId + "\n");

        String absentManifestId = UUID.randomUUID().toString();
        writeReference("scan-bucket", "scan-key.bin", absentManifestId);

        // First scan
        FileSystemRecoveryScanner.ScanReport firstReport = scanner.scan(storageRoot);
        assertThat(firstReport.findings()).hasSizeGreaterThanOrEqualTo(4);

        // Quarantine all findings
        scanner.quarantine(storageRoot, firstReport);

        // Second scan — quarantine dir is excluded, no more artifacts
        FileSystemRecoveryScanner.ScanReport secondReport = scanner.scan(storageRoot);
        assertThat(secondReport.isEmpty())
                .as("Expected zero findings after quarantine, but got: %s", secondReport.findings())
                .isTrue();
    }

    @Test
    void quarantinePreservesValidCommittedObject() throws IOException {
        // Set up a valid committed chunk
        writeValidChunk("valid-chunk");
        writeValidManifest("valid-manifest");
        writeReference("bucket", "key.bin", "valid-manifest");

        // Add one bad artifact alongside the valid ones
        String badChunkId = UUID.randomUUID().toString();
        Path badChunk = chunksDir.resolve(badChunkId);
        Files.writeString(badChunk, "corrupted chunk without sidecar");
        // No .sha256 sidecar → checksum-mismatch

        FileSystemRecoveryScanner.ScanReport report = scanner.scan(storageRoot);
        assertThat(report.findings()).hasSize(1);

        scanner.quarantine(storageRoot, report);

        // Valid chunk and its sidecar should still be present
        Path validChunkFile = chunksDir.resolve("valid-chunk");
        Path validSidecar = chunksDir.resolve("valid-chunk.sha256");
        assertThat(Files.exists(validChunkFile)).isTrue();
        assertThat(Files.exists(validSidecar)).isTrue();

        // Valid manifest should still be present
        Path validManifestFile = manifestsDir.resolve("valid-manifest.properties");
        assertThat(Files.exists(validManifestFile)).isTrue();

        // Valid reference should still be present
        Path referenceFile = findReferenceFile("bucket", "key.bin");
        assertThat(Files.exists(referenceFile)).isTrue();
    }

    // ─────────────────────────────────────────────────────
    //  Multiple findings
    // ─────────────────────────────────────────────────────

    @Test
    void scanReportsAllFindingTypesInOneScan() throws IOException {
        // Orphaned chunk temp
        Files.writeString(
                chunksDir.resolve(UUID.randomUUID() + ".tmp." + UUID.randomUUID()),
                "orphaned temp");

        // Chunk with wrong checksum
        String badChunkId = UUID.randomUUID().toString();
        Files.writeString(chunksDir.resolve(badChunkId), "bad data");
        Files.writeString(chunksDir.resolve(badChunkId + ".sha256"), "wrong_hex");

        // Incomplete manifest
        Files.writeString(manifestsDir.resolve(UUID.randomUUID() + ".properties"),
                "manifestId=missing-checksum-trailer\n");

        // Broken reference
        writeReference("br-bucket", "br-key.bin", UUID.randomUUID().toString());

        FileSystemRecoveryScanner.ScanReport report = scanner.scan(storageRoot);

        assertThat(report.findings()).hasSizeGreaterThanOrEqualTo(4);

        List<String> types = report.findings().stream()
                .map(FileSystemRecoveryScanner.Finding::artifactType)
                .toList();
        assertThat(types).contains("orphaned-chunk");
        assertThat(types).contains("checksum-mismatch");
        assertThat(types).contains("incomplete-manifest");
        assertThat(types).contains("broken-reference");
    }

    // ─────────────────────────────────────────────────────
    //  Fixtures
    // ─────────────────────────────────────────────────────

    /** Writes a valid committed chunk with a correct .sha256 sidecar. */
    private void writeValidChunk(String chunkFileName) throws IOException {
        byte[] data = ("valid chunk bytes for " + chunkFileName).getBytes(StandardCharsets.UTF_8);
        Path chunkFile = chunksDir.resolve(chunkFileName);
        Path sidecar = chunksDir.resolve(chunkFileName + ".sha256");
        Files.write(chunkFile, data);
        Files.writeString(sidecar, FileSystemStorageNode.sha256Hex(data));
    }

    /**
     * Writes a valid committed manifest with a correct checksum trailer.
     * The {@code manifestId} is used as both the filename base and the property value.
     */
    private void writeValidManifest(String manifestId) throws IOException {
        String serialized = "#Magrathea storage-engine object manifest\nmanifestId=" + manifestId + "\n";
        String content = FileSystemManifestRepository.buildContentWithChecksum(serialized);
        Files.writeString(manifestsDir.resolve(manifestId + ".properties"), content);
    }

    /**
     * Writes an S3 object reference pointing to the given manifestId.
     * Uses the same directory layout as {@code S3ObjectManifestReferenceStore}.
     */
    private void writeReference(String bucket, String key, String manifestId) throws IOException {
        String encodedBucket = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(bucket.getBytes(StandardCharsets.UTF_8));
        String encodedKey = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(key.getBytes(StandardCharsets.UTF_8));
        Path bucketDir = referencesDir.resolve(encodedBucket);
        Files.createDirectories(bucketDir);
        Path refFile = bucketDir.resolve(encodedKey + ".properties");
        Files.writeString(refFile,
                "#S3 object reference\nbucket=" + bucket + "\nkey=" + key
                + "\nmanifestId=" + manifestId + "\nversionId=v1\n");
    }

    /** Resolves the reference file path for a given bucket/key (mirrors S3ObjectManifestReferenceStore). */
    private Path findReferenceFile(String bucket, String key) {
        String encodedBucket = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(bucket.getBytes(StandardCharsets.UTF_8));
        String encodedKey = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(key.getBytes(StandardCharsets.UTF_8));
        return referencesDir.resolve(encodedBucket).resolve(encodedKey + ".properties");
    }
}
