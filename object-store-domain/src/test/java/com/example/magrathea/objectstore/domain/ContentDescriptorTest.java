package com.example.magrathea.objectstore.domain;

import com.example.magrathea.objectstore.domain.aggregate.ContentDescriptor;
import com.example.magrathea.objectstore.domain.valueobject.ChecksumAlgorithm;
import com.example.magrathea.objectstore.domain.valueobject.ChecksumValue;
import com.example.magrathea.objectstore.domain.valueobject.ObjectChecksum;

import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure JUnit test for ContentDescriptor value record — NO Spring context.
 */
class ContentDescriptorTest {

    private static ObjectChecksum someChecksum() {
        return ObjectChecksum.of(Set.of(
            new ChecksumValue(ChecksumAlgorithm.SHA256, "abc123def456")));
    }

    @Test
    void of_createsWithAllFields() {
        var descriptor = ContentDescriptor.of(100, someChecksum(),
            "text/plain", "inline", "gzip", "en");
        assertEquals(100, descriptor.size());
        assertTrue(descriptor.contentType().isPresent());
        assertEquals("text/plain", descriptor.contentType().get());
        assertTrue(descriptor.contentDisposition().isPresent());
        assertEquals("inline", descriptor.contentDisposition().get());
        assertTrue(descriptor.contentEncoding().isPresent());
        assertEquals("gzip", descriptor.contentEncoding().get());
        assertTrue(descriptor.contentLanguage().isPresent());
        assertEquals("en", descriptor.contentLanguage().get());
    }

    @Test
    void of_createsWithRequiredOnly() {
        var descriptor = ContentDescriptor.of(100, someChecksum());
        assertEquals(100, descriptor.size());
        assertTrue(descriptor.contentType().isEmpty());
        assertTrue(descriptor.contentDisposition().isEmpty());
        assertTrue(descriptor.contentEncoding().isEmpty());
        assertTrue(descriptor.contentLanguage().isEmpty());
    }

    @Test
    void of_withNullOptionalFields() {
        var descriptor = ContentDescriptor.of(100, someChecksum(),
            null, null, null, null);
        assertEquals(100, descriptor.size());
        assertTrue(descriptor.contentType().isEmpty());
        assertTrue(descriptor.contentDisposition().isEmpty());
        assertTrue(descriptor.contentEncoding().isEmpty());
        assertTrue(descriptor.contentLanguage().isEmpty());
    }

    @Test
    void of_nullChecksum_throws() {
        assertThrows(NullPointerException.class,
            () -> ContentDescriptor.of(100, null));
    }

    @Test
    void of_negativeSize_throws() {
        assertThrows(IllegalArgumentException.class,
            () -> ContentDescriptor.of(-1, someChecksum()));
    }

    @Test
    void constructor_nullOptional_throws() {
        assertThrows(NullPointerException.class,
            () -> new ContentDescriptor(100, someChecksum(),
                null, Optional.empty(), Optional.empty(), Optional.empty()));
    }

    @Test
    void recordsEquals() {
        var d1 = ContentDescriptor.of(100, someChecksum(), "text/plain", null, null, null);
        var d2 = ContentDescriptor.of(100, someChecksum(), "text/plain", null, null, null);
        assertEquals(d1, d2);
        assertEquals(d1.hashCode(), d2.hashCode());
    }

    @Test
    void recordsToString() {
        var descriptor = ContentDescriptor.of(100, someChecksum());
        assertTrue(descriptor.toString().contains("size=100"));
    }
}
