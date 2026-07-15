package com.example.magrathea.storageengine.infrastructure.pipeline;

import com.example.magrathea.storageengine.application.exception.EcReconstructionException;
import com.example.magrathea.storageengine.application.exception.EcReconstructionException.Reason;
import com.example.magrathea.storageengine.application.pipeline.BoundedEcReconstructionPort;
import com.example.magrathea.storageengine.domain.valueobject.ChecksumAlgorithm;
import com.example.magrathea.storageengine.domain.valueobject.ContentHash;
import com.example.magrathea.storageengine.domain.valueobject.EcShardLayout;
import com.example.magrathea.storageengine.domain.valueobject.ErasureCodingConfig;
import com.example.magrathea.storageengine.domain.valueobject.StorageArtifactKind;
import com.example.magrathea.storageengine.domain.valueobject.StorageArtifactReferenceDescriptor;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

/**
 * Stateless bounded GF(256) reconstruction adapter for REQ-PIPELINE-017.
 *
 * <p>The adapter consumes caller-resolved bytes for one stripe and returns verified local
 * evidence. It deliberately has no repository or filesystem dependency and cannot publish
 * reconstructed artifacts. CPU-intensive reconstruction is subscribed on the explicitly
 * supplied bounded scheduler, so a Reactor caller thread never performs the GF(256) loop.</p>
 */
public final class BoundedEcReconstructionAdapter implements BoundedEcReconstructionPort {

    private static final int SCHEMA_WITH_EXPLICIT_EC_LAYOUT = 3;
    private static final int SHARD_SIZE = Math.toIntExact(EcShardLayout.SHARD_SIZE_BYTES);

    private final Scheduler executionScheduler;

    /** Creates a decoder whose CPU work is owned by the supplied bounded scheduler. */
    public BoundedEcReconstructionAdapter(Scheduler executionScheduler) {
        this.executionScheduler = java.util.Objects.requireNonNull(
                executionScheduler, "executionScheduler must not be null");
    }

    @Override
    public Mono<ReconstructionResult> reconstruct(ReconstructionRequest request) {
        return Mono.fromCallable(() -> reconstructNow(request))
                .subscribeOn(executionScheduler);
    }

    private ReconstructionResult reconstructNow(ReconstructionRequest request) {
        java.util.Objects.requireNonNull(request, "request must not be null");
        validateSchema(request);
        ValidatedStripe stripe = validateStripeMetadata(request);

        List<ShardSurvivor> survivors = request.survivors();
        if (survivors.size() < stripe.dataBlocks()) {
            throw rejected(Reason.INSUFFICIENT_SURVIVORS,
                    "At least k=" + stripe.dataBlocks() + " checksum-valid survivors are required");
        }
        if (survivors.size() != stripe.dataBlocks()) {
            throw rejected(Reason.INVALID_SURVIVOR_SET,
                    "Exactly k=" + stripe.dataBlocks() + " bounded survivors must be selected");
        }

        Set<Integer> unavailable = request.unavailableShardIndices();
        if (unavailable.isEmpty() || unavailable.size() > stripe.parityBlocks()) {
            throw rejected(Reason.INVALID_SURVIVOR_SET,
                    "Unavailable shard count must be between 1 and m=" + stripe.parityBlocks());
        }
        for (int shardIndex : unavailable) {
            if (shardIndex < 0 || shardIndex >= stripe.artifacts().length) {
                throw rejected(Reason.INVALID_SURVIVOR_SET,
                        "Unavailable shard index is out of range: " + shardIndex);
            }
        }

        byte[][] shardWorkspace = new byte[stripe.artifacts().length][];
        int[] selectedIndices = new int[stripe.dataBlocks()];
        Set<Integer> seen = new HashSet<>();
        long requestBoundarySnapshotBytes = 0;
        for (int survivorOrdinal = 0; survivorOrdinal < survivors.size(); survivorOrdinal++) {
            ShardSurvivor survivor = survivors.get(survivorOrdinal);
            int shardIndex = survivor.shardIndex();
            if (shardIndex < 0 || shardIndex >= stripe.artifacts().length) {
                throw rejected(Reason.INVALID_SURVIVOR_SET,
                        "Survivor shard index is out of range: " + shardIndex);
            }
            if (!seen.add(shardIndex)) {
                throw rejected(Reason.INVALID_SURVIVOR_SET,
                        "Duplicate survivor shard index: " + shardIndex);
            }
            if (unavailable.contains(shardIndex)) {
                throw rejected(Reason.INVALID_SURVIVOR_SET,
                        "Unavailable shard was also supplied as a survivor: " + shardIndex);
            }

            byte[] bytes = survivor.bytes();
            requestBoundarySnapshotBytes += bytes.length;
            StorageArtifactReferenceDescriptor artifact = stripe.artifacts()[shardIndex];
            validateCommittedBytes(artifact, bytes, "survivor shard " + shardIndex);
            shardWorkspace[shardIndex] = bytes;
            selectedIndices[survivorOrdinal] = shardIndex;
        }

        try {
            GaloisField256Codec.reconstructMissingData(
                    shardWorkspace, selectedIndices, stripe.dataBlocks(), SHARD_SIZE);
        } catch (IllegalArgumentException error) {
            throw new EcReconstructionException(
                    Reason.INVALID_SURVIVOR_SET,
                    "Selected survivor set cannot reconstruct the committed EC stripe", error);
        }

        byte[][] dataShards = Arrays.copyOf(shardWorkspace, stripe.dataBlocks());
        List<RegeneratedShard> regenerated = new ArrayList<>();
        long regeneratedResultSnapshotBytes = 0;
        for (int shardIndex : unavailable.stream().sorted().toList()) {
            byte[] bytes;
            if (shardIndex < stripe.dataBlocks()) {
                bytes = shardWorkspace[shardIndex];
            } else {
                bytes = GaloisField256Codec.encodeShard(
                        dataShards, shardIndex, stripe.dataBlocks());
                shardWorkspace[shardIndex] = bytes;
            }
            StorageArtifactReferenceDescriptor artifact = stripe.artifacts()[shardIndex];
            validateCommittedBytes(artifact, bytes, "regenerated shard " + shardIndex);
            regenerated.add(new RegeneratedShard(
                    shardIndex,
                    bytes,
                    ContentHash.of(ChecksumAlgorithm.SHA256, sha256Hex(bytes))));
            regeneratedResultSnapshotBytes += bytes.length;
        }

        byte[] logicalStripe = new byte[Math.toIntExact(stripe.logicalLength())];
        int outputOffset = 0;
        for (int dataIndex = 0; dataIndex < stripe.dataBlocks() && outputOffset < logicalStripe.length;
                dataIndex++) {
            int copied = Math.min(SHARD_SIZE, logicalStripe.length - outputOffset);
            System.arraycopy(shardWorkspace[dataIndex], 0, logicalStripe, outputOffset, copied);
            outputOffset += copied;
        }

        int decoderOwnedShardBufferCount = Math.toIntExact(
                Arrays.stream(shardWorkspace)
                        .filter(java.util.Objects::nonNull)
                        .count());
        WorkspaceUsage workspace = new WorkspaceUsage(
                decoderOwnedShardBufferCount,
                SHARD_SIZE,
                logicalStripe.length,
                stripe.dataBlocks(),
                stripe.dataBlocks(),
                requestBoundarySnapshotBytes,
                logicalStripe.length + regeneratedResultSnapshotBytes,
                0,
                0);
        VerifiedStripe verifiedStripe = new VerifiedStripe(
                request.stripeIndex(), stripe.logicalLength(), logicalStripe, workspace);
        return new ReconstructionResult(verifiedStripe, regenerated);
    }

    private static void validateSchema(ReconstructionRequest request) {
        if (request.manifestSchemaVersion() == SCHEMA_WITH_EXPLICIT_EC_LAYOUT) {
            return;
        }
        boolean ambiguousTypedEc = request.manifestSchemaVersion() == 2
                && request.stripeArtifacts().stream()
                        .anyMatch(artifact -> isEcShard(artifact.artifactKind()))
                && request.stripeArtifacts().stream()
                        .allMatch(artifact -> artifact.ecShardLayout().isEmpty());
        if (ambiguousTypedEc) {
            throw rejected(Reason.AMBIGUOUS_LAYOUT,
                    "Schema-2 EC metadata has no explicit stripe/shard layout; artifact order is not inferred");
        }
        throw rejected(Reason.UNSUPPORTED_SCHEMA,
                "EC reconstruction requires manifest schema 3; received schema "
                        + request.manifestSchemaVersion());
    }

    private static ValidatedStripe validateStripeMetadata(ReconstructionRequest request) {
        if (request.stripeArtifacts().isEmpty()) {
            throw rejected(Reason.AMBIGUOUS_LAYOUT,
                    "Schema-3 reconstruction requires committed stripe artifacts");
        }

        StorageArtifactReferenceDescriptor first = request.stripeArtifacts().get(0);
        EcShardLayout firstLayout = first.ecShardLayout().orElseThrow(() ->
                rejected(Reason.AMBIGUOUS_LAYOUT,
                        "Schema-3 EC artifact is missing explicit reconstruction layout"));
        int dataBlocks = firstLayout.dataBlocks();
        int parityBlocks = firstLayout.parityBlocks();
        if (dataBlocks != ErasureCodingConfig.FIXED_DATA_BLOCKS
                || parityBlocks != ErasureCodingConfig.FIXED_PARITY_BLOCKS) {
            throw rejected(Reason.UNSUPPORTED_GEOMETRY,
                    "Only fixed EC 4+2 reconstruction is supported; received k="
                            + dataBlocks + ", m=" + parityBlocks);
        }
        int totalBlocks = dataBlocks + parityBlocks;
        long logicalLength = firstLayout.stripeLogicalLength();
        if (request.stripeArtifacts().size() != totalBlocks) {
            throw rejected(Reason.INCONSISTENT_METADATA,
                    "Committed stripe must contain exactly k+m=" + totalBlocks + " artifacts");
        }

        StorageArtifactReferenceDescriptor[] indexed =
                new StorageArtifactReferenceDescriptor[totalBlocks];
        for (StorageArtifactReferenceDescriptor artifact : request.stripeArtifacts()) {
            if (!isEcShard(artifact.artifactKind())) {
                throw rejected(Reason.INCONSISTENT_METADATA,
                        "Schema-3 EC stripe contains a non-shard artifact: " + artifact.artifactKind());
            }
            EcShardLayout layout = artifact.ecShardLayout().orElseThrow(() ->
                    rejected(Reason.AMBIGUOUS_LAYOUT,
                            "Schema-3 EC artifact is missing explicit reconstruction layout"));
            if (layout.stripeIndex() != request.stripeIndex()
                    || layout.dataBlocks() != dataBlocks
                    || layout.parityBlocks() != parityBlocks
                    || layout.stripeLogicalLength() != logicalLength) {
                throw rejected(Reason.INCONSISTENT_METADATA,
                        "EC artifact layout is inconsistent within stripe " + request.stripeIndex());
            }
            int shardIndex = layout.shardIndex();
            if (indexed[shardIndex] != null) {
                throw rejected(Reason.INCONSISTENT_METADATA,
                        "Duplicate committed EC shard index: " + shardIndex);
            }
            boolean parityKind = artifact.artifactKind() == StorageArtifactKind.EC_PARITY_SHARD;
            if (layout.parity() != parityKind) {
                throw rejected(Reason.INCONSISTENT_METADATA,
                        "EC parity flag and artifact kind disagree at shard " + shardIndex);
            }
            if (artifact.storedSize() != SHARD_SIZE) {
                throw rejected(Reason.INCONSISTENT_METADATA,
                        "Committed EC shard " + shardIndex + " stored length must be " + SHARD_SIZE);
            }
            if (artifact.locations().isEmpty()) {
                throw rejected(Reason.INCONSISTENT_METADATA,
                        "Committed EC shard " + shardIndex + " has no transport-neutral location");
            }
            validateCommittedChecksumShape(artifact, shardIndex);

            long expectedOriginalSize = shardIndex < dataBlocks
                    ? Math.max(0, Math.min((long) SHARD_SIZE,
                            logicalLength - (long) shardIndex * SHARD_SIZE))
                    : 0L;
            if (artifact.originalSize() != expectedOriginalSize) {
                throw rejected(Reason.INCONSISTENT_METADATA,
                        "Committed logical data length is inconsistent at shard " + shardIndex);
            }
            indexed[shardIndex] = artifact;
        }
        if (Arrays.stream(indexed).anyMatch(java.util.Objects::isNull)) {
            throw rejected(Reason.INCONSISTENT_METADATA,
                    "Committed EC stripe does not bind every shard index");
        }
        long declaredLogicalLength = Arrays.stream(indexed)
                .limit(dataBlocks)
                .mapToLong(StorageArtifactReferenceDescriptor::originalSize)
                .sum();
        if (declaredLogicalLength != logicalLength) {
            throw rejected(Reason.INCONSISTENT_METADATA,
                    "EC stripe logical length does not equal its data-shard lengths");
        }
        return new ValidatedStripe(dataBlocks, parityBlocks, logicalLength, indexed);
    }

    private static void validateCommittedChecksumShape(
            StorageArtifactReferenceDescriptor artifact, int shardIndex) {
        ContentHash checksum = artifact.finalChecksum();
        if (checksum.algorithm() != ChecksumAlgorithm.SHA256
                || !checksum.value().matches("[0-9a-f]{64}")) {
            throw rejected(Reason.INCONSISTENT_METADATA,
                    "Committed EC shard " + shardIndex
                            + " must bind a lowercase SHA-256 checksum");
        }
    }

    private static void validateCommittedBytes(
            StorageArtifactReferenceDescriptor artifact, byte[] bytes, String label) {
        if (bytes.length != artifact.storedSize()) {
            throw rejected(Reason.INTEGRITY_MISMATCH,
                    label + " length " + bytes.length
                            + " does not match committed length " + artifact.storedSize());
        }
        String actual = sha256Hex(bytes);
        if (!actual.equals(artifact.finalChecksum().value())) {
            throw rejected(Reason.INTEGRITY_MISMATCH,
                    label + " checksum does not match committed SHA-256");
        }
    }

    private static boolean isEcShard(StorageArtifactKind kind) {
        return kind == StorageArtifactKind.EC_DATA_SHARD
                || kind == StorageArtifactKind.EC_PARITY_SHARD;
    }

    private static EcReconstructionException rejected(Reason reason, String message) {
        return new EcReconstructionException(reason, message);
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 unavailable", error);
        }
    }

    private record ValidatedStripe(
            int dataBlocks,
            int parityBlocks,
            long logicalLength,
            StorageArtifactReferenceDescriptor[] artifacts) {
    }
}
