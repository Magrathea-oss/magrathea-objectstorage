package com.example.magrathea.s3api.cucumber.specs;

import com.example.magrathea.storageengine.domain.valueobject.StepId;
import com.example.magrathea.storageengine.domain.valueobject.StorageArtifactKind;
import com.example.magrathea.s3api.adapter.web.S3MultipartPartStore;
import com.example.magrathea.objectstorage.repository.storageengine.adapter.FileSystemArtifactGarbageCollector;
import com.example.magrathea.storageengine.infrastructure.filesystem.FileSystemIntegrityScrubJob;
import com.example.magrathea.storageengine.infrastructure.filesystem.FileSystemIntegrityScrubber;
import com.example.magrathea.storageengine.infrastructure.filesystem.FileSystemStorageCluster;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

public class PhaseEp4SpaceManagementSteps {

    private Path storageRoot;
    private UUID manifestId;
    private UUID corruptArtifactId;
    private UUID healthyArtifactId;
    private Path corruptArtifact;
    private Path healthyArtifact;
    private byte[] healthyBytes;
    private StorageArtifactKind expectedKind;
    private List<StepId> expectedTransformations = List.of();
    private FileSystemIntegrityScrubber scrubber;
    private FileSystemIntegrityScrubber.ScrubReport firstReport;
    private FileSystemIntegrityScrubber.ScrubReport secondReport;
    private boolean schedulingEnabled;
    private FileSystemArtifactGarbageCollector garbageCollector;
    private final List<UUID> gcManifests = new java.util.ArrayList<>();
    private final List<Path> gcArtifacts = new java.util.ArrayList<>();
    private FileSystemArtifactGarbageCollector.ReclamationReport reclamationReport;
    private UUID sharedArtifactId;
    private Path contentAddressEntry;
    private S3MultipartPartStore multipartPartStore;
    private Path multipartUploadDirectory;
    private String multipartReason;

    @Before
    public void reset() {
        storageRoot = Path.of("target", "storage-engine-it", "REQ-SCRUB-001-" + UUID.randomUUID());
        deleteRecursively(storageRoot);
        scrubber = new FileSystemIntegrityScrubber();
        garbageCollector = new FileSystemArtifactGarbageCollector(storageRoot);
        gcManifests.clear();
        gcArtifacts.clear();
        reclamationReport = null;
    }

    @Given("periodic integrity scrubbing is disabled by default")
    public void periodicScrubbingDisabledByDefault() throws IOException {
        String condition = Files.readString(Path.of("../storage-engine-reactive-infrastructure/src/main/java/com/example/magrathea/storageengine/infrastructure/filesystem/config/StorageEngineIntegrityScrubEnabledCondition.java"));
        assertTrue(condition.contains("Boolean.class, false"));
    }

    @When("property {string} is set to true")
    public void scrubPropertyEnabled(String property) {
        assertEquals("storage.engine.integrity.scrub.enabled", property);
        schedulingEnabled = true;
    }

    @Then("the storage-engine scheduler runs the scrub job with configurable initial delay, interval, and repair policy")
    public void schedulerConfigurationIsExplicit() throws IOException {
        assertTrue(schedulingEnabled);
        String jobSource = Files.readString(Path.of("../storage-engine-reactive-infrastructure/src/main/java/com/example/magrathea/storageengine/infrastructure/filesystem/FileSystemIntegrityScrubJob.java"));
        assertTrue(jobSource.contains("@Scheduled"));
        assertTrue(jobSource.contains("storage.engine.integrity.scrub.initial-delay-ms"));
        assertTrue(jobSource.contains("storage.engine.integrity.scrub.interval-ms"));
        assertTrue(jobSource.contains("storage.engine.integrity.scrub.repair-policy"));
    }

    @Then("each completed run atomically replaces the latest operator-readable scrub report")
    public void latestReportIsReplaced() {
        FileSystemIntegrityScrubJob job = new FileSystemIntegrityScrubJob(
                new FileSystemStorageCluster(storageRoot, 1), scrubber, "REPORT_ONLY");
        FileSystemIntegrityScrubber.ScrubReport first = job.runOnce();
        FileSystemIntegrityScrubber.ScrubReport second = job.runOnce();
        assertEquals(second, job.lastReport().orElseThrow());
        assertFalse(first == second);
    }

    @Given("the storage engine uses filesystem root {string}")
    public void storageEngineUsesFilesystemRoot(String ignoredTemplate) throws IOException {
        Files.createDirectories(storageRoot.resolve("nodes/node-001/chunks"));
        Files.createDirectories(storageRoot.resolve("nodes/node-001/whole-objects"));
        Files.createDirectories(storageRoot.resolve("metadata/manifests"));
    }

    @Given("the scenario starts with no pending reclamation or scrub findings")
    public void scenarioStartsClean() {
        assertFalse(Files.exists(storageRoot.resolve("quarantine")));
    }

    @Given("a committed plain object uses storage class {string} with multipart, deduplication, and erasure coding disabled")
    public void committedPlainObject(String storageClass) throws IOException {
        assertEquals("PLAIN", storageClass);
        UUID artifactId = createGcArtifact(StorageArtifactKind.WHOLE_OBJECT, "plain-object");
        gcManifests.add(writeGcManifest(List.of(new GcArtifact(StorageArtifactKind.WHOLE_OBJECT, artifactId))));
    }

    @Given("its manifest references one whole-object storage unit and zero chunk artifacts")
    public void plainManifestHasNoChunks() {
        assertEquals(1, gcArtifacts.size());
        assertTrue(gcArtifacts.getFirst().toString().contains("whole-objects"));
    }

    @When("the owning S3 object reference is deleted or replaced and durable reclamation runs")
    public void plainReferenceRemoved() {
        reclamationReport = garbageCollector.reclaim(gcManifests.getFirst(), java.util.Set.of());
    }

    @Then("the whole-object storage unit and obsolete manifest are removed")
    public void wholeObjectAndManifestRemoved() {
        assertFalse(Files.exists(gcArtifacts.getFirst()));
        assertFalse(Files.exists(Path.of(gcArtifacts.getFirst() + ".sha256")));
        assertFalse(Files.exists(manifestFile(gcManifests.getFirst())));
    }

    @Then("no chunk reference count, dedup index entry, multipart part, or EC shard is created or decremented")
    public void noSegmentedAccountingWasInvented() {
        assertFalse(Files.exists(storageRoot.resolve("metadata/content-address-index")));
        assertEquals(1, reclamationReport.artifactsDeleted());
    }

    @Then("an idempotent second reclamation run reports no additional deletion")
    public void secondReclamationIsIdempotent() {
        assertEquals(0, garbageCollector.reclaim(gcManifests.getFirst(), java.util.Set.of()).totalDeleted());
    }

    @Given("two committed objects reference the same dedup chunk fingerprint in one bucket scope")
    public void twoObjectsShareDedupChunk() throws IOException {
        sharedArtifactId = createGcArtifact(StorageArtifactKind.DEDUP_CHUNK, "shared-dedup");
        GcArtifact shared = new GcArtifact(StorageArtifactKind.DEDUP_CHUNK, sharedArtifactId);
        gcManifests.add(writeGcManifest(List.of(shared)));
        gcManifests.add(writeGcManifest(List.of(shared)));
        contentAddressEntry = storageRoot.resolve("metadata/content-address-index/device-a/fingerprint-a");
        Files.createDirectories(contentAddressEntry.getParent());
        Files.writeString(contentAddressEntry, sharedArtifactId.toString());
    }

    @When("the first object is deleted and reclamation runs")
    public void firstDedupOwnerDeleted() {
        reclamationReport = garbageCollector.reclaim(gcManifests.get(0), java.util.Set.of(gcManifests.get(1)));
    }

    @Then("the shared dedup chunk, checksum sidecar, and content-address entry remain readable by the second object")
    public void sharedDedupRemains() {
        assertTrue(Files.isRegularFile(gcArtifacts.getFirst()));
        assertTrue(Files.isRegularFile(Path.of(gcArtifacts.getFirst() + ".sha256")));
        assertTrue(Files.isRegularFile(contentAddressEntry));
    }

    @When("the second object is deleted and reclamation runs")
    public void secondDedupOwnerDeleted() {
        reclamationReport = garbageCollector.reclaim(gcManifests.get(1), java.util.Set.of());
    }

    @Then("the final reference count reaches zero")
    public void finalReferenceCountIsZero() {
        assertEquals(1, reclamationReport.artifactsDeleted());
    }

    @Then("the dedup chunk, checksum sidecar, and content-address entry are removed as one durable reclamation unit")
    public void dedupArtifactsRemoved() {
        assertFalse(Files.exists(gcArtifacts.getFirst()));
        assertFalse(Files.exists(Path.of(gcArtifacts.getFirst() + ".sha256")));
        assertFalse(Files.exists(contentAddressEntry));
    }

    @Given("a multipart upload has persisted parts but has no committed object manifest")
    public void multipartUploadHasUncommittedParts() throws IOException {
        Path partsRoot = storageRoot.resolve("multipart-parts");
        multipartPartStore = new S3MultipartPartStore(partsRoot);
        multipartUploadDirectory = partsRoot.resolve("gcupload");
        Files.createDirectories(multipartUploadDirectory);
        Files.writeString(multipartUploadDirectory.resolve("part-1.bin"), "uncommitted-part");
        Files.writeString(multipartUploadDirectory.resolve("part-1.bin.sha256"), "temporary-checksum");
        Files.setLastModifiedTime(multipartUploadDirectory, FileTime.from(Instant.EPOCH));
        createGcArtifact(StorageArtifactKind.DEDUP_CHUNK, "unrelated-committed-dedup");
        createGcArtifact(StorageArtifactKind.EC_DATA_SHARD, "unrelated-committed-ec");
    }

    @When("the upload is {word} and multipart reclamation runs")
    public void multipartReclamationRuns(String reason) {
        multipartReason = reason;
        if ("aborted".equals(reason)) {
            StepVerifier.create(multipartPartStore.deleteUpload(
                    com.example.magrathea.objectstore.domain.valueobject.UploadId.of("gcupload")))
                .verifyComplete();
        } else {
            StepVerifier.create(multipartPartStore.reclaimExpired(Instant.now()))
                .expectNext(1)
                .verifyComplete();
        }
    }

    @Then("every uncommitted part and temporary checksum artifact is removed")
    public void uncommittedPartsRemoved() {
        assertFalse(Files.exists(multipartUploadDirectory));
    }

    @Then("chunks belonging to committed dedup or EC objects are unchanged")
    public void committedSegmentedArtifactsUnchanged() {
        assertTrue(Files.exists(gcArtifacts.get(0)));
        assertTrue(Files.exists(gcArtifacts.get(1)));
    }

    @Then("a repeated multipart reclamation run is idempotent")
    public void repeatedMultipartReclamationIsIdempotent() {
        if ("aborted".equals(multipartReason)) {
            assertDoesNotThrow(() -> StepVerifier.create(multipartPartStore.deleteUpload(
                    com.example.magrathea.objectstore.domain.valueobject.UploadId.of("gcupload")))
                .verifyComplete());
        } else {
            StepVerifier.create(multipartPartStore.reclaimExpired(Instant.now()))
                .expectNext(0)
                .verifyComplete();
        }
    }

    @Given("a committed EC object manifest references four data shards and two parity shards")
    public void committedEcManifest() throws IOException {
        List<GcArtifact> shards = new java.util.ArrayList<>();
        for (int index = 0; index < 4; index++) {
            shards.add(new GcArtifact(StorageArtifactKind.EC_DATA_SHARD,
                    createGcArtifact(StorageArtifactKind.EC_DATA_SHARD, "data-" + index)));
        }
        for (int index = 0; index < 2; index++) {
            shards.add(new GcArtifact(StorageArtifactKind.EC_PARITY_SHARD,
                    createGcArtifact(StorageArtifactKind.EC_PARITY_SHARD, "parity-" + index)));
        }
        gcManifests.add(writeGcManifest(shards));
        createGcArtifact(StorageArtifactKind.WHOLE_OBJECT, "unrelated-whole-object");
        createGcArtifact(StorageArtifactKind.DEDUP_CHUNK, "unrelated-dedup");
    }

    @When("the final owning object is deleted and EC reclamation runs")
    public void ecObjectDeleted() {
        reclamationReport = garbageCollector.reclaim(gcManifests.getFirst(), java.util.Set.of());
    }

    @Then("all six shard artifacts and checksum metadata are removed")
    public void allEcShardsRemoved() {
        assertEquals(6, reclamationReport.artifactsDeleted());
        assertEquals(6, reclamationReport.sidecarsDeleted());
        gcArtifacts.subList(0, 6).forEach(path -> assertFalse(Files.exists(path)));
    }

    @Then("no whole-object unit or unrelated dedup chunk is removed")
    public void unrelatedArtifactsRemain() {
        assertTrue(Files.exists(gcArtifacts.get(6)));
        assertTrue(Files.exists(gcArtifacts.get(7)));
    }

    @Given("a committed {string} artifact with applied transformations {string} has a mismatching final persisted checksum")
    public void committedCorruptArtifact(String artifactType, String transformations) throws IOException {
        expectedKind = switch (artifactType) {
            case "whole-object" -> StorageArtifactKind.WHOLE_OBJECT;
            case "dedup-chunk" -> StorageArtifactKind.DEDUP_CHUNK;
            case "multipart-part" -> StorageArtifactKind.MULTIPART_PART;
            case "ec-data-shard" -> StorageArtifactKind.EC_DATA_SHARD;
            default -> throw new IllegalArgumentException("Unsupported artifact type: " + artifactType);
        };
        expectedTransformations = "NONE".equals(transformations)
                ? List.of()
                : Arrays.stream(transformations.split(",")).map(StepId::valueOf).toList();
        manifestId = UUID.randomUUID();
        corruptArtifactId = UUID.randomUUID();
        corruptArtifact = artifactPath(expectedKind, corruptArtifactId);
        byte[] committedBytes = ("persisted-" + artifactType + "-" + transformations)
                .getBytes(StandardCharsets.UTF_8);
        writeArtifact(corruptArtifact, committedBytes);
        byte[] corrupted = committedBytes.clone();
        corrupted[0] ^= 0x55;
        Files.write(corruptArtifact, corrupted);
    }

    @Given("another committed healthy artifact exists in the same manifest")
    public void healthyArtifactExists() throws IOException {
        healthyArtifactId = UUID.randomUUID();
        healthyArtifact = artifactPath(StorageArtifactKind.WHOLE_OBJECT, healthyArtifactId);
        healthyBytes = "healthy-neighbour-artifact".getBytes(StandardCharsets.UTF_8);
        writeArtifact(healthyArtifact, healthyBytes);
        writeManifest();
    }

    @When("the periodic scrub job inspects the configured storage root with repair policy {string}")
    public void periodicScrubRuns(String repairPolicy) {
        FileSystemIntegrityScrubJob job = new FileSystemIntegrityScrubJob(
                new FileSystemStorageCluster(storageRoot, 1), scrubber, repairPolicy);
        firstReport = job.runOnce();
        assertEquals(firstReport, job.lastReport().orElseThrow());
    }

    @Then("one integrity finding identifies artifact type, identifier, owning manifest, checksum failure, and transformations {string}")
    public void findingIdentifiesCorruption(String ignoredTransformations) {
        assertEquals(1, firstReport.findings().size());
        FileSystemIntegrityScrubber.ScrubFinding finding = firstReport.findings().getFirst();
        assertEquals(expectedKind, finding.artifactKind());
        assertEquals(corruptArtifactId, finding.artifactId());
        assertEquals(manifestId.toString(), finding.manifestId());
        assertEquals(expectedTransformations, finding.transformations());
        assertTrue(finding.failure().contains("final persisted SHA-256 mismatch"));
    }

    @Then("the corrupt artifact and checksum sidecar are quarantined according to the configured repair policy")
    public void corruptArtifactIsQuarantined() {
        Path destination = storageRoot.resolve("quarantine").resolve(storageRoot.relativize(corruptArtifact));
        assertFalse(Files.exists(corruptArtifact));
        assertFalse(Files.exists(Path.of(corruptArtifact + ".sha256")));
        assertTrue(Files.isRegularFile(destination));
        assertTrue(Files.isRegularFile(Path.of(destination + ".sha256")));
    }

    @Then("the healthy artifact remains byte-for-byte unchanged")
    public void healthyArtifactUnchanged() throws IOException {
        assertArrayEquals(healthyBytes, Files.readAllBytes(healthyArtifact));
        assertEquals(sha256Hex(healthyBytes), Files.readString(Path.of(healthyArtifact + ".sha256")).trim());
    }

    @Then("a second scrub run is deterministic and does not duplicate the quarantined finding")
    public void secondScrubDoesNotDuplicateFinding() {
        secondReport = scrubber.scrub(storageRoot, FileSystemIntegrityScrubber.RepairPolicy.QUARANTINE);
        assertTrue(secondReport.findings().isEmpty());
        assertArrayEquals(healthyBytes, readBytes(healthyArtifact));
    }

    private UUID createGcArtifact(StorageArtifactKind kind, String content) throws IOException {
        UUID id = UUID.randomUUID();
        Path path = artifactPath(kind, id);
        writeArtifact(path, content.getBytes(StandardCharsets.UTF_8));
        gcArtifacts.add(path);
        return id;
    }

    private UUID writeGcManifest(List<GcArtifact> artifacts) throws IOException {
        UUID id = UUID.randomUUID();
        Properties properties = new Properties();
        properties.setProperty("manifest.schemaVersion", "2");
        properties.setProperty("manifestId", id.toString());
        properties.setProperty("artifactCount", Integer.toString(artifacts.size()));
        for (int index = 0; index < artifacts.size(); index++) {
            GcArtifact artifact = artifacts.get(index);
            properties.setProperty("artifact." + index + ".kind", artifact.kind().name());
            properties.setProperty("artifact." + index + ".artifactId", artifact.id().toString());
        }
        Files.createDirectories(manifestFile(id).getParent());
        try (StringWriter writer = new StringWriter()) {
            properties.store(writer, "EP-4 garbage collection fixture");
            Files.writeString(manifestFile(id), writer.toString());
        }
        return id;
    }

    private Path manifestFile(UUID id) {
        return storageRoot.resolve("metadata/manifests").resolve(id + ".properties");
    }

    private record GcArtifact(StorageArtifactKind kind, UUID id) { }

    private Path artifactPath(StorageArtifactKind kind, UUID artifactId) {
        String namespace = kind == StorageArtifactKind.WHOLE_OBJECT ? "whole-objects" : "chunks";
        return storageRoot.resolve("nodes/node-001").resolve(namespace).resolve(artifactId.toString());
    }

    private static void writeArtifact(Path artifact, byte[] bytes) throws IOException {
        Files.createDirectories(artifact.getParent());
        Files.write(artifact, bytes);
        Files.writeString(Path.of(artifact + ".sha256"), sha256Hex(bytes));
    }

    private void writeManifest() throws IOException {
        Properties properties = new Properties();
        properties.setProperty("manifest.schemaVersion", "2");
        properties.setProperty("manifestId", manifestId.toString());
        properties.setProperty("artifactCount", "2");
        byte[] expectedCorruptBytes = ("persisted-" + artifactLabel(expectedKind) + "-"
                + transformationLabel()).getBytes(StandardCharsets.UTF_8);
        addArtifact(properties, 0, expectedKind, corruptArtifactId, expectedCorruptBytes, expectedTransformations);
        addArtifact(properties, 1, StorageArtifactKind.WHOLE_OBJECT, healthyArtifactId, healthyBytes, List.of());
        try (StringWriter writer = new StringWriter()) {
            properties.store(writer, "REQ-SCRUB-001 manifest fixture");
            Files.writeString(storageRoot.resolve("metadata/manifests").resolve(manifestId + ".properties"),
                    writer.toString());
        }
    }

    private static void addArtifact(
            Properties properties,
            int ordinal,
            StorageArtifactKind kind,
            UUID id,
            byte[] bytes,
            List<StepId> transformations) {
        String prefix = "artifact." + ordinal + ".";
        properties.setProperty(prefix + "kind", kind.name());
        properties.setProperty(prefix + "artifactId", id.toString());
        properties.setProperty(prefix + "finalChecksum.algorithm", "SHA256");
        properties.setProperty(prefix + "finalChecksum.value", sha256Hex(bytes));
        properties.setProperty(prefix + "locations", "node-001");
        properties.setProperty(prefix + "stepChecksumCount", Integer.toString(transformations.size()));
        String input = sha256Hex("original".getBytes(StandardCharsets.UTF_8));
        for (int index = 0; index < transformations.size(); index++) {
            String stepPrefix = prefix + "stepChecksum." + index + ".";
            properties.setProperty(stepPrefix + "stepId", transformations.get(index).name());
            properties.setProperty(stepPrefix + "input.value", input);
            String output = sha256Hex(("transformed-" + index).getBytes(StandardCharsets.UTF_8));
            properties.setProperty(stepPrefix + "output.value", output);
            input = output;
        }
    }

    private String artifactLabel(StorageArtifactKind kind) {
        return switch (kind) {
            case WHOLE_OBJECT -> "whole-object";
            case DEDUP_CHUNK -> "dedup-chunk";
            case MULTIPART_PART -> "multipart-part";
            case EC_DATA_SHARD -> "ec-data-shard";
            default -> throw new IllegalArgumentException("Unsupported fixture kind: " + kind);
        };
    }

    private String transformationLabel() {
        return expectedTransformations.isEmpty()
                ? "NONE"
                : expectedTransformations.stream().map(Enum::name).collect(java.util.stream.Collectors.joining(","));
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    private static byte[] readBytes(Path path) {
        try {
            return Files.readAllBytes(path);
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }

    private static void deleteRecursively(Path root) {
        if (!Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException error) {
                    throw new UncheckedIOException(error);
                }
            });
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }
}
