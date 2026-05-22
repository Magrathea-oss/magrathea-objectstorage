package com.example.magrathea.objectstorage.domain;

import com.example.magrathea.objectstorage.domain.valueobject.BucketWebsiteConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure JUnit test for BucketWebsiteConfiguration value object.
 */
class BucketWebsiteConfigurationTest {

    @Test
    void createValid() {
        var config = new BucketWebsiteConfiguration("test-bucket", "index.html", null, null, null, null);
        assertEquals("test-bucket", config.bucketName());
        assertEquals("index.html", config.indexDocument());
    }

    @Test
    void nullBucketNameThrows() {
        assertThrows(NullPointerException.class, () -> new BucketWebsiteConfiguration(null, null, null, null, null, null));
    }

    @Test
    void withErrorDocument() {
        var config = new BucketWebsiteConfiguration("bucket", null, "error.html", null, null, null);
        assertNull(config.indexDocument());
        assertEquals("error.html", config.errorDocument());
    }

    @Test
    void withRedirect() {
        var config = new BucketWebsiteConfiguration("bucket", null, null, "https://example.com", null, null);
        assertEquals("https://example.com", config.redirectAllRequestsTo());
    }
}
