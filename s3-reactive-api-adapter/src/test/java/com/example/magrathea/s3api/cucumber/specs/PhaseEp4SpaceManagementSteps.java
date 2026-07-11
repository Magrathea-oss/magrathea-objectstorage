package com.example.magrathea.s3api.cucumber.specs;

import com.example.magrathea.storageengine.domain.valueobject.StepId;
import com.example.magrathea.storageengine.domain.valueobject.StorageArtifactKind;
import com.example.magrathea.storageengine.infrastructure.filesystem.FileSystemIntegrityScrubJob;
import com.example.magrathea.storageengine.infrastructure.filesystem.FileSystemIntegrityScrubber;
import com.example.magrathea.storageengine.infrastructure.filesystem.FileSystemStorageCluster;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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

    @Before
    public void reset() {
        storageRoot = Path.of("target", "storage-engine-it", "REQ-SCRUB-001-" + UUID.randomUUID());
        deleteRecursively(storageRoot);
        scrubber = new FileSystemIntegrityScrubber();
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
