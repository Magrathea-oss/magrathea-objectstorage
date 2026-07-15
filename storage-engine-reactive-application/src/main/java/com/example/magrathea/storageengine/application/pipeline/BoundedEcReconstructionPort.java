package com.example.magrathea.storageengine.application.pipeline;

import com.example.magrathea.storageengine.domain.valueobject.ContentHash;
import com.example.magrathea.storageengine.domain.valueobject.StorageArtifactReferenceDescriptor;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Transport- and filesystem-neutral application boundary for REQ-PIPELINE-017.
 *
 * <p>One invocation reconstructs exactly one bounded EC stripe. Callers resolve survivor
 * locations and supply committed bytes; implementations return verified in-memory evidence
 * only and have no artifact, manifest, object-reference, or replacement-location publication
 * capability.</p>
 */
public interface BoundedEcReconstructionPort {

    Mono<ReconstructionResult> reconstruct(ReconstructionRequest request);

    /** Committed metadata and checksum-valid candidate bytes for one stripe. */
    record ReconstructionRequest(
            int manifestSchemaVersion,
            long stripeIndex,
            List<StorageArtifactReferenceDescriptor> stripeArtifacts,
            List<ShardSurvivor> survivors,
            Set<Integer> unavailableShardIndices) {

        public ReconstructionRequest {
            Objects.requireNonNull(stripeArtifacts, "stripeArtifacts must not be null");
            Objects.requireNonNull(survivors, "survivors must not be null");
            Objects.requireNonNull(unavailableShardIndices,
                    "unavailableShardIndices must not be null");
            stripeArtifacts = List.copyOf(stripeArtifacts);
            survivors = List.copyOf(survivors);
            unavailableShardIndices = Set.copyOf(unavailableShardIndices);
        }
    }

    /**
     * Bytes supplied for one declared shard index.
     * The record owns an immutable boundary snapshot; copies returned by {@link #bytes()}
     * are caller-owned and are not decoder-workspace measurements.
     */
    record ShardSurvivor(int shardIndex, byte[] bytes) {
        public ShardSurvivor {
            Objects.requireNonNull(bytes, "bytes must not be null");
            bytes = Arrays.copyOf(bytes, bytes.length);
        }

        @Override
        public byte[] bytes() {
            return Arrays.copyOf(bytes, bytes.length);
        }
    }

    /** Accepted output is deliberately limited to a verified stripe and regenerated shards. */
    record ReconstructionResult(
            VerifiedStripe verifiedStripe,
            List<RegeneratedShard> regeneratedShards) {

        public ReconstructionResult {
            Objects.requireNonNull(verifiedStripe, "verifiedStripe must not be null");
            Objects.requireNonNull(regeneratedShards, "regeneratedShards must not be null");
            regeneratedShards = List.copyOf(regeneratedShards);
        }
    }

    /**
     * Logical stripe bytes with bounded-workspace measurements from the decoder.
     * The record owns an immutable result snapshot in addition to the decoder's transient
     * logical assembly reported by {@link WorkspaceUsage}.
     */
    record VerifiedStripe(
            long stripeIndex,
            long stripeLogicalLength,
            byte[] logicalBytes,
            WorkspaceUsage workspaceUsage) {

        public VerifiedStripe {
            Objects.requireNonNull(logicalBytes, "logicalBytes must not be null");
            Objects.requireNonNull(workspaceUsage, "workspaceUsage must not be null");
            logicalBytes = Arrays.copyOf(logicalBytes, logicalBytes.length);
        }

        @Override
        public byte[] logicalBytes() {
            return Arrays.copyOf(logicalBytes, logicalBytes.length);
        }
    }

    /**
     * One regenerated shard accepted only after committed length and checksum verification.
     * The byte array is an immutable result-boundary snapshot, separate from transient
     * decoder workspace.
     */
    record RegeneratedShard(int shardIndex, byte[] bytes, ContentHash checksum) {
        public RegeneratedShard {
            Objects.requireNonNull(bytes, "bytes must not be null");
            Objects.requireNonNull(checksum, "checksum must not be null");
            bytes = Arrays.copyOf(bytes, bytes.length);
        }

        @Override
        public byte[] bytes() {
            return Arrays.copyOf(bytes, bytes.length);
        }
    }

    /**
     * Object-size-independent payload retention observed for one decoder invocation.
     * Decoder-owned workspace is reported separately from immutable request/result boundary
     * snapshots. Caller-retained source arrays and copies obtained from defensive accessors
     * are outside this measurement because their lifetimes are controlled by the caller.
     */
    record WorkspaceUsage(
            int decoderOwnedShardBufferCount,
            int decoderOwnedShardBufferSizeBytes,
            long decoderOwnedLogicalAssemblyBytes,
            int matrixRows,
            int matrixColumns,
            long requestBoundarySnapshotBytes,
            long resultBoundarySnapshotBytes,
            long earlierStripeBytesRetained,
            long wholeObjectBytesRetained) {

        /** Compatibility constructor for the original decoder-workspace-only measurement. */
        public WorkspaceUsage(
                int shardBufferCount,
                int shardBufferSizeBytes,
                long logicalStripeBytes,
                int matrixRows,
                int matrixColumns,
                long earlierStripeBytesRetained,
                long wholeObjectBytesRetained) {
            this(shardBufferCount, shardBufferSizeBytes, logicalStripeBytes,
                    matrixRows, matrixColumns, 0, 0,
                    earlierStripeBytesRetained, wholeObjectBytesRetained);
        }

        /** Compatibility accessor; reports decoder-owned shard buffers only. */
        public int shardBufferCount() {
            return decoderOwnedShardBufferCount;
        }

        /** Compatibility accessor; reports the decoder-owned shard-buffer size. */
        public int shardBufferSizeBytes() {
            return decoderOwnedShardBufferSizeBytes;
        }

        /** Compatibility accessor; reports the decoder-owned logical assembly size. */
        public long logicalStripeBytes() {
            return decoderOwnedLogicalAssemblyBytes;
        }
    }
}
