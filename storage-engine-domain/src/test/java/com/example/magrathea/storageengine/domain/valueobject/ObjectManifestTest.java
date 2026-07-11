package com.example.magrathea.storageengine.domain.valueobject;

import com.example.magrathea.storageengine.domain.TestFixtures;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ObjectManifest} and {@link StorageArtifactReferenceDescriptor}.
 * Covers chunk reference correctness, collection immutability, and
 * construction invariants.
 * Pure JUnit 5 — no Spring, no Mockito, no reactive imports.
 */
class ObjectManifestTest {

    // -------------------------------------------------------------------------
    // Construction invariants
    // -------------------------------------------------------------------------

    @Test
    void manifest_chunkCountMismatch_throwsIllegalArgumentException() {
        // chunkCount = 1 but chunks list is empty — must fail
        assertThrows(IllegalArgumentException.class, () ->
                buildManifest(1, List.of()),
                "chunkCount != chunks.size() must throw IllegalArgumentException");
    }

    @Test
    void manifest_negativeChunkCount_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () ->
                buildManifest(-1, List.of()),
                "chunkCount < 0 must throw IllegalArgumentException");
    }

    @Test
    void manifest_negativeTotalOriginalSize_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () ->
                new ObjectManifest(
                        ManifestId.generate(),
                        ObjectId.of("obj-1"),
                        VersionId.of("v1"),
                        StorageClassId.STANDARD,
                        TestFixtures.aBucketDevice(TestFixtures.aBucketRef()),
                        DeviceConfigurationHash.of("hash-abc"),
                        aTrace(),
                        List.of(),
                        0, -1L, 0L, List.of()),
                "totalOriginalSize < 0 must throw IllegalArgumentException");
    }

    // -------------------------------------------------------------------------
    // Chunk reference storage and retrieval
    // -------------------------------------------------------------------------

    @Test
    void manifest_emptyChunks_isValidForZeroSizeObject() {
        ObjectManifest manifest = buildManifest(0, List.of());
        assertNotNull(manifest);
        assertEquals(0, manifest.chunkCount());
        assertTrue(manifest.chunks().isEmpty());
    }

    @Test
    void manifest_withOneChunk_storesAndReturnsIt() {
        StorageArtifactReferenceDescriptor chunk = aChunk();
        ObjectManifest manifest = buildManifest(1, List.of(chunk));

        assertEquals(1, manifest.chunkCount());
        assertEquals(1, manifest.chunks().size());
        assertEquals(chunk.chunkId(), manifest.chunks().get(0).chunkId());
    }

    @Test
    void manifest_withThreeChunks_storesAllInOrder() {
        StorageArtifactReferenceDescriptor c1 = aChunk();
        StorageArtifactReferenceDescriptor c2 = aChunk();
        StorageArtifactReferenceDescriptor c3 = aChunk();
        ObjectManifest manifest = buildManifest(3, List.of(c1, c2, c3));

        assertEquals(3, manifest.chunkCount());
        assertEquals(c1.chunkId(), manifest.chunks().get(0).chunkId());
        assertEquals(c2.chunkId(), manifest.chunks().get(1).chunkId());
        assertEquals(c3.chunkId(), manifest.chunks().get(2).chunkId());
    }

    // -------------------------------------------------------------------------
    // Collection immutability — ObjectManifest.chunks()
    // -------------------------------------------------------------------------

    @Test
    void manifest_chunks_returnedListIsImmutable() {
        StorageArtifactReferenceDescriptor chunk = aChunk();
        ObjectManifest manifest = buildManifest(1, List.of(chunk));

        // Mutations to the returned list must not affect the manifest's internal state.
        assertThrows(UnsupportedOperationException.class, () ->
                manifest.chunks().add(aChunk()),
                "chunks() must return an immutable list");
    }

    @Test
    void manifest_passedMutableList_changesDoNotAffectManifest() {
        List<StorageArtifactReferenceDescriptor> mutable = new ArrayList<>();
        mutable.add(aChunk());
        ObjectManifest manifest = buildManifest(1, mutable);

        // Modifying the original list after construction must not affect the manifest.
        mutable.add(aChunk());

        assertEquals(1, manifest.chunkCount(),
                "Manifest chunkCount must not change when source list is mutated after construction");
        assertEquals(1, manifest.chunks().size(),
                "Manifest chunks must not grow when source list is mutated after construction");
    }

    // -------------------------------------------------------------------------
    // Collection immutability — StorageArtifactReferenceDescriptor
    // -------------------------------------------------------------------------

    @Test
    void chunkDescriptor_stepChecksums_isImmutable() {
        List<StepChecksumDescriptor> mutableChecksums = new ArrayList<>();
        mutableChecksums.add(aStepChecksum());
        StorageArtifactReferenceDescriptor descriptor = StorageArtifactReferenceDescriptor(mutableChecksums, List.of());

        assertThrows(UnsupportedOperationException.class, () ->
                descriptor.stepChecksums().add(aStepChecksum()),
                "stepChecksums() must return an immutable list");
    }

    @Test
    void chunkDescriptor_locations_isImmutable() {
        List<NodeId> mutableLocations = new ArrayList<>();
        mutableLocations.add(NodeId.of("node-1"));
        StorageArtifactReferenceDescriptor descriptor = StorageArtifactReferenceDescriptor(List.of(), mutableLocations);

        assertThrows(UnsupportedOperationException.class, () ->
                descriptor.locations().add(NodeId.of("node-extra")),
                "locations() must return an immutable list");
    }

    @Test
    void chunkDescriptor_mutatingSourceLists_doesNotAffectDescriptor() {
        List<StepChecksumDescriptor> mutableChecksums = new ArrayList<>();
        mutableChecksums.add(aStepChecksum());
        List<NodeId> mutableLocations = new ArrayList<>();
        mutableLocations.add(NodeId.of("node-1"));

        StorageArtifactReferenceDescriptor descriptor = StorageArtifactReferenceDescriptor(mutableChecksums, mutableLocations);

        // Mutate original lists after construction
        mutableChecksums.add(aStepChecksum());
        mutableLocations.add(NodeId.of("node-2"));

        assertEquals(1, descriptor.stepChecksums().size(),
                "stepChecksums must not grow when source list is mutated");
        assertEquals(1, descriptor.locations().size(),
                "locations must not grow when source list is mutated");
    }

    @Test
    void chunkDescriptor_nullStepChecksums_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () ->
                new com.example.magrathea.storageengine.domain.valueobject.StorageArtifactReferenceDescriptor(
                        ChunkId.generate(),
                        Fingerprint.of(FingerprintAlgorithm.SHA256, "abc123"),
                        1024L, 800L,
                        null,
                        ContentHash.of(ChecksumAlgorithm.SHA256, "deadbeef"),
                        List.of()));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static ObjectManifest buildManifest(int chunkCount, List<StorageArtifactReferenceDescriptor> chunks) {
        return new ObjectManifest(
                ManifestId.generate(),
                ObjectId.of("obj-manifest-test"),
                VersionId.of("v1"),
                StorageClassId.STANDARD,
                TestFixtures.aBucketDevice(TestFixtures.aBucketRef()),
                DeviceConfigurationHash.of("cfg-hash-01"),
                aTrace(),
                List.of(PolicyDecision.of(
                        StepId.DEDUP, PolicyDecisionStatus.DISABLED,
                        PolicyDecisionReason.of("DEDUP_DISABLED", "Dedup not configured"))),
                chunkCount,
                chunkCount == 0 ? 0L : 4096L * chunkCount,
                chunkCount == 0 ? 0L : 3000L * chunkCount,
                chunks);
    }

    private static StorageArtifactReferenceDescriptor aChunk() {
        return new StorageArtifactReferenceDescriptor(
                ChunkId.generate(),
                Fingerprint.of(FingerprintAlgorithm.SHA256, "fp-" + System.nanoTime()),
                4096L, 3000L,
                List.of(),
                ContentHash.of(ChecksumAlgorithm.SHA256, "hash-" + System.nanoTime()),
                List.of(NodeId.of("node-1")));
    }

    /** Helper using positional factory to build StorageArtifactReferenceDescriptor with custom lists. */
    private static StorageArtifactReferenceDescriptor StorageArtifactReferenceDescriptor(
            List<StepChecksumDescriptor> stepChecksums,
            List<NodeId> locations) {
        return new com.example.magrathea.storageengine.domain.valueobject.StorageArtifactReferenceDescriptor(
                ChunkId.generate(),
                Fingerprint.of(FingerprintAlgorithm.SHA256, "fp-abc"),
                4096L, 3000L,
                stepChecksums,
                ContentHash.of(ChecksumAlgorithm.SHA256, "hash-abc"),
                locations);
    }

    private static StepChecksumDescriptor aStepChecksum() {
        return StepChecksumDescriptor.of(
                StepId.COMPRESS,
                ContentHash.of(ChecksumAlgorithm.SHA256, "input-" + System.nanoTime()),
                ContentHash.of(ChecksumAlgorithm.SHA256, "output-" + System.nanoTime()));
    }

    private static UploadCompletionTrace aTrace() {
        return new UploadCompletionTrace(
                UploadMode.SINGLE_OBJECT,
                Optional.empty(),
                ContentHash.of(ChecksumAlgorithm.SHA256, "trace-hash-001"),
                true,
                4096L,
                true,
                Optional.empty());
    }
}
