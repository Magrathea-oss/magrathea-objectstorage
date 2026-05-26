package com.example.magrathea.objectstore.domain;

import com.example.magrathea.objectstore.domain.valueobject.BucketLifecycleConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.BucketLifecycleConfiguration.LifecycleRule;
import com.example.magrathea.objectstore.domain.valueobject.BucketLifecycleConfiguration.Expiration;
import com.example.magrathea.objectstore.domain.valueobject.BucketLifecycleConfiguration.NoncurrentVersionExpiration;
import com.example.magrathea.objectstore.domain.valueobject.BucketLifecycleConfiguration.AbortIncompleteMultipartUpload;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure JUnit test for BucketLifecycleConfiguration value object.
 */
class BucketLifecycleConfigurationTest {

    @Test
    void createValidConfiguration() {
        var expiration = new Expiration("30", null);
        var rule = new LifecycleRule("expire-30", "Enabled", "/", expiration, null, null);
        var config = new BucketLifecycleConfiguration("test-bucket", List.of(rule));
        assertEquals("test-bucket", config.bucketName());
        assertTrue(config.hasRules());
        assertEquals("expire-30", config.rules().getFirst().id());
        assertEquals("Enabled", config.rules().getFirst().status());
    }

    @Test
    void emptyRulesIsNotHasRules() {
        var config = new BucketLifecycleConfiguration("test-bucket", null);
        assertFalse(config.hasRules());
    }

    @Test
    void nullBucketNameThrows() {
        assertThrows(NullPointerException.class, () -> new BucketLifecycleConfiguration(null, null));
    }

    @Test
    void nullStatusThrows() {
        assertThrows(NullPointerException.class, () -> new LifecycleRule("id", null, null, null, null, null));
    }

    @Test
    void expirationWithDays() {
        var expiration = new Expiration("30", null);
        assertEquals("30", expiration.days());
        assertNull(expiration.date());
    }

    @Test
    void expirationWithDate() {
        var expiration = new Expiration(null, "2026-01-01");
        assertNull(expiration.days());
        assertEquals("2026-01-01", expiration.date());
    }

    @Test
    void noncurrentVersionExpiration() {
        var ncv = new NoncurrentVersionExpiration("10");
        assertEquals("10", ncv.noncurrentDays());
    }

    @Test
    void abortIncompleteMultipartUpload() {
        var abort = new AbortIncompleteMultipartUpload("5");
        assertEquals("5", abort.daysAfterInitiation());
    }
}
