package com.example.magrathea.storageengine.domain.valueobject;

import com.example.magrathea.storageengine.domain.TestFixtures;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link StoragePolicy}.
 * Pure JUnit 5 — no Spring, no Mockito, no reactive imports.
 */
class StoragePolicyTest {

    @Test
    void of_allPresent_returnsValidPolicy() {
        StoragePolicy policy = TestFixtures.aStoragePolicy();
        assertNotNull(policy);
        assertEquals(StorageClassId.STANDARD, policy.id());
        assertTrue(policy.dedup().isPresent());
        assertTrue(policy.compression().isPresent());
        assertTrue(policy.encryption().isPresent());
        assertTrue(policy.erasureCoding().isPresent());
        assertNotNull(policy.replication());
    }

    @Test
    void minimal_factory_hasAllOptionalsEmpty() {
        StoragePolicy policy = StoragePolicy.minimal(StorageClassId.STANDARD);
        assertTrue(policy.dedup().isEmpty(),
                "minimal policy must have no dedup");
        assertTrue(policy.compression().isEmpty(),
                "minimal policy must have no compression");
        assertTrue(policy.encryption().isEmpty(),
                "minimal policy must have no encryption");
        assertTrue(policy.erasureCoding().isEmpty(),
                "minimal policy must have no erasure coding");
    }

    @Test
    void minimal_factory_hasReplicationFactor1() {
        StoragePolicy policy = StoragePolicy.minimal(StorageClassId.STANDARD);
        assertEquals(1, policy.replication().factor(),
                "minimal policy must have replication factor = 1");
    }

    @Test
    void null_id_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () ->
                StoragePolicy.of(null, Optional.empty(), Optional.empty(),
                        Optional.empty(), Optional.empty(), ReplicationConfig.of(1)));
    }

    @Test
    void null_dedup_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () ->
                StoragePolicy.of(StorageClassId.STANDARD, null, Optional.empty(),
                        Optional.empty(), Optional.empty(), ReplicationConfig.of(1)));
    }

    @Test
    void null_replication_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () ->
                StoragePolicy.of(StorageClassId.STANDARD, Optional.empty(), Optional.empty(),
                        Optional.empty(), Optional.empty(), null));
    }

    /**
     * Confirms that chunkSize and alignment are accessible inside DedupConfig,
     * not as a separate step or value object outside it.
     */
    @Test
    void dedupContainsChunkSizeAndAlignment() {
        StoragePolicy policy = TestFixtures.aStoragePolicy();
        assertTrue(policy.dedup().isPresent());
        DedupConfig dedup = policy.dedup().get();
        assertTrue(dedup.chunkSize() > 0,
                "chunkSize must be a positive field inside DedupConfig");
        assertNotNull(dedup.alignment(),
                "alignment must be a non-null field inside DedupConfig");
    }

    // -------------------------------------------------------------------------
    // Combination invariants
    // -------------------------------------------------------------------------

    @Test
    void erasureCodingWithReplicationFactor1_isAllowed() {
        // EC + replication factor=1 (single copy, EC handles redundancy) is valid.
        assertDoesNotThrow(() -> StoragePolicy.of(
                StorageClassId.STANDARD,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.of(TestFixtures.anErasureCodingConfig()),
                ReplicationConfig.of(1)));
    }

    @Test
    void erasureCodingWithReplicationFactorGreaterThan1_throwsIllegalArgumentException() {
        // EC + replication factor > 1 stacks two redundancy mechanisms and is rejected.
        assertThrows(IllegalArgumentException.class, () -> StoragePolicy.of(
                StorageClassId.STANDARD,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.of(TestFixtures.anErasureCodingConfig()),
                ReplicationConfig.of(3)),
                "EC + replication factor > 1 must throw IllegalArgumentException");
    }

    @Test
    void dedupWithEC_andReplication1_isAllowed() {
        // Dedup + EC + replication=1 is valid: dedup and EC are independent concerns.
        assertDoesNotThrow(() -> StoragePolicy.of(
                StorageClassId.STANDARD,
                Optional.of(TestFixtures.aBucketDedupConfig()),
                Optional.empty(),
                Optional.empty(),
                Optional.of(TestFixtures.anErasureCodingConfig()),
                ReplicationConfig.of(1)));
    }

    @Test
    void noEC_withHighReplicationFactor_isAllowed() {
        // No EC + replication factor=5 is valid: replication handles redundancy.
        assertDoesNotThrow(() -> StoragePolicy.of(
                StorageClassId.STANDARD,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                ReplicationConfig.of(5)));
    }
}
