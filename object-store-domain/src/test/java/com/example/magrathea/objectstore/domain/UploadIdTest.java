package com.example.magrathea.objectstore.domain;

import com.example.magrathea.objectstore.domain.valueobject.UploadId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UploadIdTest {

    @Test
    void shouldCreateUploadId() {
        UploadId id = UploadId.of("abc123XYZ");
        assertEquals("abc123XYZ", id.value());
    }

    @Test
    void shouldRejectBlankUploadId() {
        assertThrows(IllegalArgumentException.class, () -> UploadId.of(""));
        assertThrows(IllegalArgumentException.class, () -> UploadId.of("  "));
    }

    @Test
    void shouldRejectNonAlphanumericUploadId() {
        assertThrows(IllegalArgumentException.class, () -> UploadId.of("abc-123"));
        assertThrows(IllegalArgumentException.class, () -> UploadId.of("hello world"));
    }

    @Test
    void shouldGenerateValidUploadId() {
        UploadId id = UploadId.generate();
        assertNotNull(id.value());
        assertTrue(id.value().matches("^[a-zA-Z0-9]+$"));
    }
}
