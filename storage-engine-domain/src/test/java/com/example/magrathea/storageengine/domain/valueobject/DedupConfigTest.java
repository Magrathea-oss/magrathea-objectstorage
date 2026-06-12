package com.example.magrathea.storageengine.domain.valueobject;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link DedupConfig}.
 * Pure JUnit 5 — no Spring, no Mockito, no reactive imports.
 */
class DedupConfigTest {

    @Test
    void of_withDefaults_usesOneMBChunkSize() {
        DedupConfig config = DedupConfig.of(DedupScope.BUCKET_LEVEL, FingerprintAlgorithm.SHA256);
        assertEquals(DedupConfig.DEFAULT_CHUNK_SIZE, config.chunkSize(),
                "Default factory must produce 1 MB chunk size");
    }

    @Test
    void of_customChunkSize_accepted() {
        long custom = 512_000L; // 500 KB
        DedupConfig config = DedupConfig.of(DedupScope.BUCKET_LEVEL, FingerprintAlgorithm.BLAKE2,
                custom, ChunkAlignment.BLOCK_BOUNDARY);
        assertEquals(custom, config.chunkSize());
        assertEquals(ChunkAlignment.BLOCK_BOUNDARY, config.alignment());
    }

    @Test
    void chunkSize_belowMinimum_throwsIllegalArgumentException() {
        // 4095 is one byte below the allowed minimum of 4096
        assertThrows(IllegalArgumentException.class, () ->
                DedupConfig.of(DedupScope.BUCKET_LEVEL, FingerprintAlgorithm.SHA256, 4095L, ChunkAlignment.NONE));
    }

    @Test
    void chunkSize_atMinimum_accepted() {
        // 4096 is the exact minimum — must not throw
        assertDoesNotThrow(() ->
                DedupConfig.of(DedupScope.BUCKET_LEVEL, FingerprintAlgorithm.SHA256, 4096L, ChunkAlignment.NONE));
    }

    @Test
    void chunkSize_aboveMaximum_throwsIllegalArgumentException() {
        // 1_073_741_825 is one byte above the allowed maximum (1 GB)
        assertThrows(IllegalArgumentException.class, () ->
                DedupConfig.of(DedupScope.BUCKET_LEVEL, FingerprintAlgorithm.SHA256,
                        1_073_741_825L, ChunkAlignment.NONE));
    }

    @Test
    void chunkSize_atMaximum_accepted() {
        // 1_073_741_824 is exactly 1 GB — must not throw
        assertDoesNotThrow(() ->
                DedupConfig.of(DedupScope.BUCKET_LEVEL, FingerprintAlgorithm.SHA256,
                        1_073_741_824L, ChunkAlignment.NONE));
    }

    @Test
    void nullScope_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () ->
                DedupConfig.of(null, FingerprintAlgorithm.SHA256));
    }

    @Test
    void nullAlgorithm_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () ->
                DedupConfig.of(DedupScope.BUCKET_LEVEL, null));
    }

    @Test
    void nullAlignment_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () ->
                new DedupConfig(DedupScope.BUCKET_LEVEL, FingerprintAlgorithm.SHA256, 65536L, null));
    }

    /**
     * Confirms that chunking configuration (chunkSize and alignment) lives inside
     * DedupConfig rather than as a separate value object or step.
     */
    @Test
    void chunkingIsInsideDedupConfig() {
        DedupConfig config = DedupConfig.of(DedupScope.BUCKET_LEVEL, FingerprintAlgorithm.SHA256);
        // Both properties must be accessible directly from DedupConfig
        assertTrue(config.chunkSize() > 0,
                "chunkSize must be a positive field inside DedupConfig");
        assertNotNull(config.alignment(),
                "alignment must be a non-null field inside DedupConfig");
    }
}
