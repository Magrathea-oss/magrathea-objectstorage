package com.example.magrathea.objectstore.domain;

import com.example.magrathea.objectstore.domain.valueobject.BucketPolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure JUnit test for BucketPolicy value object.
 */
class BucketPolicyTest {

    @Test
    void createValidPolicy() {
        var policy = new BucketPolicy("test-bucket", "{\"Effect\":\"Allow\"}");
        assertEquals("test-bucket", policy.bucketName());
        assertEquals("{\"Effect\":\"Allow\"}", policy.policyJson());
        assertTrue(policy.hasPolicy());
    }

    @Test
    void nullBucketNameThrows() {
        assertThrows(NullPointerException.class, () -> new BucketPolicy(null, "{}"));
    }

    @Test
    void nullPolicyJsonThrows() {
        assertThrows(NullPointerException.class, () -> new BucketPolicy("bucket", null));
    }

    @Test
    void blankPolicyIsNotHasPolicy() {
        var policy = new BucketPolicy("bucket", "");
        assertFalse(policy.hasPolicy());
    }
}
