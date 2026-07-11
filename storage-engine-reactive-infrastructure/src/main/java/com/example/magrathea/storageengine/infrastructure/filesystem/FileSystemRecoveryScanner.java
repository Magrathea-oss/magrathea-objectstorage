package com.example.magrathea.storageengine.infrastructure.filesystem;

import com.example.magrathea.storageengine.application.observability.StorageObservabilityFields;
import com.example.magrathea.storageengine.application.pipeline.StorageEvent;
import com.example.magrathea.storageengine.application.pipeline.StorageEventMeasurements;
import com.example.magrathea.storageengine.application.pipeline.StorageEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Recovery scanner for the filesystem-backed storage engine.
 *
 * <p>Scans the storage root and identifies integrity problems:
 * <ul>
 *   <li><b>orphaned-chunk</b> — temp chunk files ({@code .tmp.UUID}) that were never
 *       renamed to their final path (interrupted writes).</li>
 *   <li><b>checksum-mismatch</b> — committed chunk files whose SHA-256 does not match
 *       the stored {@code .sha256} sidecar, or committed manifests whose content
 *       checksum does not match the stored trailer.</li>
 *   <li><b>incomplete-manifest</b> — manifest files that cannot be parsed or whose
 *       checksum trailer is absent, or manifest temp files left from interrupted writes.</li>
 *   <li><b>broken-reference</b> — S3-object reference files that point to a manifest ID
 *       for which no committed manifest file exists.</li>
 * </ul>
 *
 * <p>The scanner is <em>idempotent</em>: running it twice on the same clean filesystem
 * produces zero findings both times.
 *
 * <p>Quarantine moves reported artifacts to {@code storageRoot/quarantine/} preserving
 * relative paths. Valid committed objects are never moved or deleted.
 *
 * <h2>Directory layout assumed</h2>
 * <pre>
 * storageRoot/
 *   nodes/
 *     node-NNN/
 *       chunks/            ← chunk files (and .sha256 sidecars, .tmp.UUID temp files)
 *   metadata/
 *     manifests/           ← manifest .properties files (and .tmp.UUID temp files)
 *     s3-object-references/← S3 object reference .properties files (sub-bucket dirs)
 *   quarantine/            ← quarantined artifacts (excluded from scanning)
 * </pre>
 */
@Component
public class FileSystemRecoveryScanner {

    private static final Logger log = LoggerFactory.getLogger(FileSystemRecoveryScanner.class);

    private static final String SHA256_EXT = ".sha256";
    private static final String TMP_MARKER = ".tmp.";
    private static final String QUARANTINE_DIR = "quarantine";
    private static final String CHECKSUM_KEY = "manifest.checksum";
    private static final String CHECKSUM_LINE_PREFIX = "\n" + CHECKSUM_KEY + "=";
    private static final String MANIFEST_ID_KEY = "manifestId";
    private static final String PROPERTIES_EXT = ".properties";

    private final StorageEventPublisher eventPublisher;

    public FileSystemRecoveryScanner() {
        this(StorageEventPublisher.noop());
    }

    @Autowired
    public FileSystemRecoveryScanner(StorageEventPublisher eventPublisher) {
        this.eventPublisher = java.util.Objects.requireNonNull(eventPublisher, "eventPublisher must not be null");
    }

    // ─────────────────────────────────────────────────────
    //  Public API
    // ─────────────────────────────────────────────────────

    /**
     * Scan the storage root and return a report of all integrity findings.
     * Does NOT modify the filesystem — call {@link #quarantine(Path, ScanReport)} to act.
     *
     * @param storageRoot the cluster root (parent of {@code nodes/}, {@code metadata/})
     * @return a report listing all findings
     */
    public ScanReport scan(Path storageRoot) {
        String correlationId = UUID.randomUUID().toString();
        Instant startedAt = Instant.now();
        List<Finding> findings = new ArrayList<>();

        scanChunks(storageRoot, findings);
        scanManifests(storageRoot, findings);
        scanObjectReferences(storageRoot, findings);

        ScanReport report = new ScanReport(List.copyOf(findings));
        logScanReport(correlationId, report);
        publish(new StorageEvent.RecoveryScanCompleted(
                correlationId,
                "recovery-scan",
                Instant.now(),
                Duration.between(startedAt, Instant.now()),
                report.isEmpty() ? "clean" : "findings",
                StorageEventMeasurements.recoveryScan(report.size())));
        return report;
    }

    /**
     * Quarantine all artifacts reported in the scan report by moving them to
     * {@code storageRoot/quarantine/}. Preserves the relative path structure.
     *
     * @param storageRoot the cluster root
     * @param report      the report produced by {@link #scan(Path)}
     */
    public void quarantine(Path storageRoot, ScanReport report) {
        String correlationId = UUID.randomUUID().toString();
        Path quarantineRoot = storageRoot.resolve(QUARANTINE_DIR);
        for (Finding finding : report.findings()) {
            Path artifact = Path.of(finding.artifactPath());
            if (!Files.exists(artifact)) {
                continue; // already moved or deleted — skip
            }
            try {
                Path relative = storageRoot.relativize(artifact);
                Path destination = quarantineRoot.resolve(relative);
                Files.createDirectories(destination.getParent());
                Files.move(artifact, destination,
                        StandardCopyOption.REPLACE_EXISTING);
                log.warn("Storage recovery quarantined artifact correlationId={} artifactType={} artifactHash={} reason={}",
                        correlationId,
                        finding.artifactType(),
                        safeArtifactHash(artifact),
                        finding.reason());
                publish(new StorageEvent.RecoveryArtifactQuarantined(
                        correlationId,
                        "recovery-quarantine",
                        Instant.now(),
                        finding.artifactType(),
                        safeArtifactHash(artifact),
                        StorageEventMeasurements.recoveryQuarantine(1)));
                // Also move the .sha256 sidecar if quarantining a chunk
                if (finding.artifactType().equals("checksum-mismatch")
                        || finding.artifactType().equals("orphaned-chunk")) {
                    Path sidecar = Path.of(finding.artifactPath() + SHA256_EXT);
                    if (Files.exists(sidecar)) {
                        Path sidecarDest = quarantineRoot.resolve(
                                storageRoot.relativize(sidecar));
                        Files.createDirectories(sidecarDest.getParent());
                        Files.move(sidecar, sidecarDest, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            } catch (IOException e) {
                throw new UncheckedIOException(
                        "Failed to quarantine artifact: " + finding.artifactPath(), e);
            }
        }
    }

    // ─────────────────────────────────────────────────────
    //  Chunk scanning
    // ─────────────────────────────────────────────────────

    private void scanChunks(Path storageRoot, List<Finding> findings) {
        Path nodesDir = storageRoot.resolve("nodes");
        if (!Files.isDirectory(nodesDir)) {
            return;
        }
        Path quarantineRoot = storageRoot.resolve(QUARANTINE_DIR);
        try (var nodeStream = Files.list(nodesDir)) {
            nodeStream.filter(Files::isDirectory).forEach(nodeDir -> {
                Path chunksDir = nodeDir.resolve("chunks");
                if (!Files.isDirectory(chunksDir)) {
                    return;
                }
                try (var chunkStream = Files.list(chunksDir)) {
                    chunkStream.filter(Files::isRegularFile).forEach(chunkFile -> {
                        // Skip quarantine directory
                        if (chunkFile.startsWith(quarantineRoot)) {
                            return;
                        }
                        String name = chunkFile.getFileName().toString();
                        // A final sidecar without its data file can remain if interruption
                        // occurs between the protocol's sidecar and data atomic renames.
                        if (name.endsWith(SHA256_EXT) && !name.contains(TMP_MARKER)) {
                            String dataName = name.substring(0, name.length() - SHA256_EXT.length());
                            if (!Files.exists(chunkFile.resolveSibling(dataName))) {
                                findings.add(Finding.orphanedChunk(chunkFile.toString(),
                                        "Orphaned checksum sidecar without committed chunk: " + name));
                            }
                            return;
                        }
                        // Orphaned temp files
                        if (name.contains(TMP_MARKER)) {
                            findings.add(Finding.orphanedChunk(chunkFile.toString(),
                                    "Orphaned chunk temp file from interrupted write: " + name));
                            return;
                        }
                        // Committed chunk — verify checksum
                        verifyChunkChecksum(chunkFile, findings);
                    });
                } catch (IOException e) {
                    throw new UncheckedIOException("Failed to list chunks directory: " + chunksDir, e);
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to list nodes directory: " + nodesDir, e);
        }
    }

    private void verifyChunkChecksum(Path chunkFile, List<Finding> findings) {
        Path checksumFile = chunkFile.getParent().resolve(
                chunkFile.getFileName().toString() + SHA256_EXT);
        try {
            byte[] data = Files.readAllBytes(chunkFile);
            if (!Files.exists(checksumFile)) {
                findings.add(Finding.checksumMismatch(chunkFile.toString(),
                        "Chunk checksum sidecar (.sha256) missing — cannot verify integrity"));
                return;
            }
            String storedHex = Files.readString(checksumFile).trim();
            String computedHex = sha256Hex(data);
            if (!computedHex.equals(storedHex)) {
                findings.add(Finding.checksumMismatch(chunkFile.toString(),
                        "Chunk SHA-256 mismatch: stored=" + storedHex + " computed=" + computedHex));
            }
        } catch (IOException e) {
            findings.add(Finding.checksumMismatch(chunkFile.toString(),
                    "Failed to read chunk for checksum verification: " + e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────────────
    //  Manifest scanning
    // ─────────────────────────────────────────────────────

    private void scanManifests(Path storageRoot, List<Finding> findings) {
        Path manifestsDir = storageRoot.resolve("metadata").resolve("manifests");
        if (!Files.isDirectory(manifestsDir)) {
            return;
        }
        try (var stream = Files.list(manifestsDir)) {
            stream.filter(Files::isRegularFile).forEach(file -> {
                String name = file.getFileName().toString();
                // Orphaned manifest temp files
                if (name.contains(TMP_MARKER)) {
                    findings.add(Finding.incompleteManifest(file.toString(),
                            "Orphaned manifest temp file from interrupted write: " + name));
                    return;
                }
                // Only scan .properties files as committed manifests
                if (!name.endsWith(PROPERTIES_EXT)) {
                    return;
                }
                verifyManifestIntegrity(file, findings);
            });
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to list manifests directory: " + manifestsDir, e);
        }
    }

    private void verifyManifestIntegrity(Path manifestFile, List<Finding> findings) {
        try {
            String rawContent = Files.readString(manifestFile);
            // Verify checksum trailer
            int idx = rawContent.lastIndexOf(CHECKSUM_LINE_PREFIX);
            if (idx < 0) {
                findings.add(Finding.incompleteManifest(manifestFile.toString(),
                        "Manifest checksum trailer (manifest.checksum=) not found"));
                return;
            }
            String contentForVerification = rawContent.substring(0, idx + 1);
            String checksumLine = rawContent.substring(idx + 1);
            String storedHex = checksumLine.substring(CHECKSUM_KEY.length() + 1).trim();
            String computedHex = sha256Hex(contentForVerification);
            if (!computedHex.equals(storedHex)) {
                findings.add(Finding.checksumMismatch(manifestFile.toString(),
                        "Manifest SHA-256 mismatch: stored=" + storedHex + " computed=" + computedHex));
                return;
            }
            // Verify parseability
            Properties properties = new Properties();
            properties.load(new StringReader(rawContent));
            String manifestId = properties.getProperty("manifestId");
            if (manifestId == null || manifestId.isBlank()) {
                findings.add(Finding.incompleteManifest(manifestFile.toString(),
                        "Manifest missing required field 'manifestId'"));
            }
        } catch (IOException e) {
            findings.add(Finding.incompleteManifest(manifestFile.toString(),
                    "Failed to read or parse manifest: " + e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────────────
    //  Object reference scanning
    // ─────────────────────────────────────────────────────

    private void scanObjectReferences(Path storageRoot, List<Finding> findings) {
        Path manifestsDir = storageRoot.resolve("metadata").resolve("manifests");
        scanReferenceDirectory(storageRoot, storageRoot.resolve("metadata").resolve("s3-object-references"),
                manifestsDir, findings);
    }

    private void scanReferenceDirectory(Path storageRoot, Path referencesDir,
                                        Path manifestsDir, List<Finding> findings) {
        if (!Files.isDirectory(referencesDir)) {
            return;
        }
        try {
            Files.walkFileTree(referencesDir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (!file.getFileName().toString().endsWith(PROPERTIES_EXT)) {
                        return FileVisitResult.CONTINUE;
                    }
                    checkReferenceFile(file, manifestsDir, findings);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to scan object references directory: " + referencesDir, e);
        }
    }

    private void checkReferenceFile(Path refFile, Path manifestsDir, List<Finding> findings) {
        try {
            String content = Files.readString(refFile);
            Properties properties = new Properties();
            properties.load(new StringReader(content));
            String manifestId = properties.getProperty(MANIFEST_ID_KEY);
            if (manifestId == null || manifestId.isBlank()) {
                findings.add(Finding.brokenReference(refFile.toString(),
                        "Object reference missing 'manifestId' field"));
                return;
            }
            Path manifestFile = manifestsDir.resolve(manifestId.trim() + PROPERTIES_EXT);
            if (!Files.exists(manifestFile)) {
                findings.add(Finding.brokenReference(refFile.toString(),
                        "Object reference points to absent manifest: " + manifestId.trim()));
            }
        } catch (IOException e) {
            findings.add(Finding.brokenReference(refFile.toString(),
                    "Failed to read object reference file: " + e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────────────
    //  Observability helper
    // ─────────────────────────────────────────────────────

    private void logScanReport(String correlationId, ScanReport report) {
        if (report.isEmpty()) {
            log.info("Storage recovery scan completed cleanly correlationId={}", correlationId);
            return;
        }
        Map<String, Long> counts = report.findings().stream()
                .collect(Collectors.groupingBy(Finding::artifactType, Collectors.counting()));
        log.warn("Storage recovery scan found artifacts correlationId={} findingCount={} artifactTypeCounts={}",
                correlationId, report.size(), counts);
        report.findings().forEach(finding -> log.warn(
                "Storage recovery finding correlationId={} artifactType={} artifactHash={} reason={}",
                correlationId,
                finding.artifactType(),
                safeArtifactHash(Path.of(finding.artifactPath())),
                finding.reason()));
    }

    private void publish(StorageEvent event) {
        eventPublisher.publish(event)
                .onErrorResume(error -> {
                    log.warn("Storage recovery observability publication failed eventType={} error={}",
                            event.type(), error.toString());
                    return Mono.empty();
                })
                .subscribe();
    }

    private static String safeArtifactHash(Path artifact) {
        return StorageObservabilityFields.sha256Hex(artifact.toAbsolutePath().normalize().toString());
    }

    // ─────────────────────────────────────────────────────
    //  SHA-256 helper
    // ─────────────────────────────────────────────────────

    private static String sha256Hex(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    private static String sha256Hex(String content) {
        return sha256Hex(content.getBytes(StandardCharsets.UTF_8));
    }

    // ─────────────────────────────────────────────────────
    //  Value types
    // ─────────────────────────────────────────────────────

    /**
     * A single scanner finding describing one problematic artifact.
     *
     * @param artifactPath absolute path of the artifact on disk
     * @param artifactType one of: {@code orphaned-chunk}, {@code checksum-mismatch},
     *                     {@code incomplete-manifest}, {@code broken-reference}
     * @param reason       human-readable description of the problem
     */
    public record Finding(String artifactPath, String artifactType, String reason) {

        static Finding orphanedChunk(String path, String reason) {
            return new Finding(path, "orphaned-chunk", reason);
        }

        static Finding checksumMismatch(String path, String reason) {
            return new Finding(path, "checksum-mismatch", reason);
        }

        static Finding incompleteManifest(String path, String reason) {
            return new Finding(path, "incomplete-manifest", reason);
        }

        static Finding brokenReference(String path, String reason) {
            return new Finding(path, "broken-reference", reason);
        }
    }

    /**
     * The result of a {@link #scan(Path)} operation.
     *
     * @param findings immutable list of findings
     */
    public record ScanReport(List<Finding> findings) {

        public boolean isEmpty() {
            return findings.isEmpty();
        }

        public int size() {
            return findings.size();
        }
    }
}
