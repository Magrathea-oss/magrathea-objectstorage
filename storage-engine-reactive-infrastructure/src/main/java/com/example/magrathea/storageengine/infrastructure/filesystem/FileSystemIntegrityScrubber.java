package com.example.magrathea.storageengine.infrastructure.filesystem;

import com.example.magrathea.storageengine.domain.valueobject.StorageArtifactKind;
import com.example.magrathea.storageengine.domain.valueobject.StepId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

/**
 * Incrementally verifies the final persisted representation of manifest-owned artifacts.
 *
 * <p>The scrubber deliberately hashes bytes before read-time decompression or decryption.
 * Consequently compressed bytes and authenticated ciphertext are protected without loading
 * plaintext or encryption keys into this background process. Applied transformations are
 * retained in every finding so operators can choose a transform-aware repair procedure.</p>
 */
@Component
public class FileSystemIntegrityScrubber {

    private static final Logger log = LoggerFactory.getLogger(FileSystemIntegrityScrubber.class);
    private static final String CHECKSUM_SUFFIX = ".sha256";

    public ScrubReport scrub(Path storageRoot, RepairPolicy repairPolicy) {
        java.util.Objects.requireNonNull(storageRoot, "storageRoot must not be null");
        java.util.Objects.requireNonNull(repairPolicy, "repairPolicy must not be null");
        Instant startedAt = Instant.now();
        List<ScrubFinding> findings = new ArrayList<>();
        long[] inspected = {0};

        Path manifestsRoot = storageRoot.resolve("metadata").resolve("manifests");
        if (Files.isDirectory(manifestsRoot)) {
            try (var manifests = Files.list(manifestsRoot)) {
                manifests.filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().endsWith(".properties"))
                        .sorted()
                        .forEach(path -> {
                            try {
                                inspectManifest(storageRoot, path, findings, inspected);
                            } catch (RuntimeException error) {
                                log.warn("Skipping invalid manifest during artifact scrub path={}", path, error);
                            }
                        });
            } catch (IOException error) {
                throw new UncheckedIOException("Failed to list manifests for integrity scrubbing", error);
            }
        }

        if (repairPolicy == RepairPolicy.QUARANTINE) {
            findings.forEach(finding -> quarantine(storageRoot, finding));
        }
        ScrubReport report = new ScrubReport(startedAt, Instant.now(), inspected[0], List.copyOf(findings));
        log.info("Storage integrity scrub completed inspectedArtifacts={} findingCount={} repairPolicy={}",
                report.inspectedArtifacts(), report.findings().size(), repairPolicy);
        return report;
    }

    private void inspectManifest(
            Path storageRoot,
            Path manifestFile,
            List<ScrubFinding> findings,
            long[] inspected) {
        String raw;
        try {
            raw = Files.readString(manifestFile);
        } catch (IOException error) {
            log.warn("Skipping unreadable manifest during artifact scrub path={}", manifestFile, error);
            return;
        }
        Properties properties = new Properties();
        try {
            properties.load(new StringReader(raw));
        } catch (IOException error) {
            log.warn("Skipping unparsable manifest during artifact scrub path={}", manifestFile, error);
            return;
        }
        String manifestId = properties.getProperty("manifestId", manifestIdentifier(manifestFile));
        int schemaVersion = Integer.parseInt(properties.getProperty("manifest.schemaVersion", "0"));
        String countKey = schemaVersion >= 2 ? "artifactCount" : "chunkCount";
        int artifactCount = Integer.parseInt(properties.getProperty(countKey, "0"));
        for (int ordinal = 0; ordinal < artifactCount; ordinal++) {
            String prefix = (schemaVersion >= 2 ? "artifact." : "chunk.") + ordinal + ".";
            StorageArtifactKind kind = schemaVersion >= 2
                    ? StorageArtifactKind.valueOf(required(properties, prefix + "kind"))
                    : StorageArtifactKind.LEGACY_CHUNK;
            String idKey = schemaVersion >= 2 ? "artifactId" : "chunkId";
            UUID artifactId = UUID.fromString(required(properties, prefix + idKey));
            String expectedChecksum = required(properties, prefix + "finalChecksum.value");
            String checksumAlgorithm = required(properties, prefix + "finalChecksum.algorithm");
            List<StepId> transformations = appliedTransformations(properties, prefix);
            List<String> locations = locations(properties.getProperty(prefix + "locations", "node-001"));
            for (String location : locations) {
                inspected[0]++;
                Path artifact = artifactPath(storageRoot, location, kind, artifactId);
                Path quarantined = storageRoot.resolve("quarantine").resolve(storageRoot.relativize(artifact));
                if (!Files.isRegularFile(artifact) && Files.isRegularFile(quarantined)) {
                    continue;
                }
                String failure = verifyFinalRepresentation(artifact, expectedChecksum, checksumAlgorithm);
                if (failure != null) {
                    findings.add(new ScrubFinding(
                            kind,
                            artifactId,
                            manifestId,
                            artifact,
                            transformations,
                            failure));
                }
            }
        }
    }

    private static String verifyFinalRepresentation(
            Path artifact,
            String expectedChecksum,
            String checksumAlgorithm) {
        if (!Files.isRegularFile(artifact)) {
            return "persisted artifact is missing";
        }
        if (!"SHA256".equals(checksumAlgorithm)) {
            return "unsupported final checksum algorithm: " + checksumAlgorithm;
        }
        Path sidecar = artifact.resolveSibling(artifact.getFileName() + CHECKSUM_SUFFIX);
        if (!Files.isRegularFile(sidecar)) {
            return "checksum sidecar is missing";
        }
        try {
            String computed = sha256Hex(artifact);
            String sidecarChecksum = Files.readString(sidecar).trim();
            if (!expectedChecksum.equals(computed)) {
                return "final persisted SHA-256 mismatch";
            }
            if (!sidecarChecksum.equals(computed)) {
                return "checksum sidecar SHA-256 mismatch";
            }
            return null;
        } catch (IOException error) {
            return "failed to hash final persisted representation: " + error.getMessage();
        }
    }

    private static List<StepId> appliedTransformations(Properties properties, String prefix) {
        int count = Integer.parseInt(properties.getProperty(prefix + "stepChecksumCount", "0"));
        List<StepId> transformations = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            String stepPrefix = prefix + "stepChecksum." + index + ".";
            StepId step = StepId.valueOf(required(properties, stepPrefix + "stepId"));
            String input = required(properties, stepPrefix + "input.value");
            String output = required(properties, stepPrefix + "output.value");
            if ((step == StepId.COMPRESS || step == StepId.CRYPT) && !input.equals(output)) {
                transformations.add(step);
            }
        }
        return List.copyOf(transformations);
    }

    private static Path artifactPath(
            Path storageRoot,
            String location,
            StorageArtifactKind kind,
            UUID artifactId) {
        String namespace = kind == StorageArtifactKind.WHOLE_OBJECT ? "whole-objects" : "chunks";
        return storageRoot.resolve("nodes").resolve(location).resolve(namespace).resolve(artifactId.toString());
    }

    private static void quarantine(Path storageRoot, ScrubFinding finding) {
        Path artifact = finding.artifactPath();
        if (!Files.exists(artifact)) {
            return;
        }
        Path destination = storageRoot.resolve("quarantine").resolve(storageRoot.relativize(artifact));
        try {
            Files.createDirectories(destination.getParent());
            Files.move(artifact, destination, StandardCopyOption.REPLACE_EXISTING);
            Path sidecar = artifact.resolveSibling(artifact.getFileName() + CHECKSUM_SUFFIX);
            if (Files.exists(sidecar)) {
                Path sidecarDestination = destination.resolveSibling(destination.getFileName() + CHECKSUM_SUFFIX);
                Files.move(sidecar, sidecarDestination, StandardCopyOption.REPLACE_EXISTING);
            }
            log.warn("Storage integrity scrub quarantined artifact artifactKind={} artifactId={} manifestId={} transformations={}",
                    finding.artifactKind(), finding.artifactId(), finding.manifestId(), finding.transformations());
        } catch (IOException error) {
            throw new UncheckedIOException("Failed to quarantine corrupt storage artifact " + artifact, error);
        }
    }

    private static List<String> locations(String value) {
        if (value == null || value.isBlank()) {
            return List.of("node-001");
        }
        return java.util.Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(location -> !location.isEmpty())
                .toList();
    }

    private static String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Required manifest property is missing: " + key);
        }
        return value;
    }

    private static String manifestIdentifier(Path manifestFile) {
        String fileName = manifestFile.getFileName().toString();
        return fileName.substring(0, fileName.indexOf('.'));
    }

    private static String sha256Hex(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var input = Files.newInputStream(path)) {
                byte[] block = new byte[64 * 1024];
                int read;
                while ((read = input.read(block)) >= 0) {
                    digest.update(block, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }

    public enum RepairPolicy {
        REPORT_ONLY,
        QUARANTINE
    }

    public record ScrubFinding(
            StorageArtifactKind artifactKind,
            UUID artifactId,
            String manifestId,
            Path artifactPath,
            List<StepId> transformations,
            String failure) {
        public ScrubFinding {
            transformations = List.copyOf(transformations);
        }
    }

    public record ScrubReport(
            Instant startedAt,
            Instant completedAt,
            long inspectedArtifacts,
            List<ScrubFinding> findings) {
        public ScrubReport {
            findings = List.copyOf(findings);
        }

        public boolean isClean() {
            return findings.isEmpty();
        }
    }
}
