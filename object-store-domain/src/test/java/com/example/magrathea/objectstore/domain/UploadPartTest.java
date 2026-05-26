package com.example.magrathea.objectstore.domain;

import com.example.magrathea.objectstore.domain.valueobject.PartNumber;
import com.example.magrathea.objectstore.domain.valueobject.UploadPart;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UploadPartTest {

    @Test
    void shouldCreateUploadPart() {
        UploadPart part = UploadPart.create(PartNumber.of(1), "\"abc123\"", 1024);
        assertEquals(1, part.partNumber().value());
        assertEquals("\"abc123\"", part.etag());
        assertEquals(1024, part.size());
        assertNotNull(part.lastModified());
    }

    @Test
    void shouldRejectNegativeSize() {
        assertThrows(IllegalArgumentException.class,
            () -> UploadPart.create(PartNumber.of(1), "\"etag\"", -1));
    }

    @Test
    void shouldRejectNullPartNumber() {
        assertThrows(NullPointerException.class,
            () -> UploadPart.create(null, "\"etag\"", 1024));
    }

    @Test
    void shouldRejectNullEtag() {
        assertThrows(NullPointerException.class,
            () -> UploadPart.create(PartNumber.of(1), null, 1024));
    }
}
