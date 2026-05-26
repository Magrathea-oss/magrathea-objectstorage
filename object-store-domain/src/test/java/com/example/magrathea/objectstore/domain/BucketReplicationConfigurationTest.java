package com.example.magrathea.objectstore.domain;

import com.example.magrathea.objectstore.domain.valueobject.BucketReplicationConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.BucketReplicationConfiguration.ReplicationRule;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure JUnit test for BucketReplicationConfiguration value object.
 */
class BucketReplicationConfigurationTest {

    @Test
    void createValid() {
        var config = new BucketReplicationConfiguration("test-bucket", "arn:aws:iam:role", null);
        assertEquals("test-bucket", config.bucketName());
        assertEquals("arn:aws:iam:role", config.role());
    }

    @Test
    void nullBucketNameThrows() {
        assertThrows(NullPointerException.class, () -> new BucketReplicationConfiguration(null, null, null));
    }

    @Test
    void withRules() {
        var rule = new ReplicationRule("rule1", "Enabled", "/", "dest-bucket", "STANDARD", false, false);
        var config = new BucketReplicationConfiguration("bucket", "role", List.of(rule));
        assertEquals(1, config.rules().size());
        assertEquals("rule1", config.rules().getFirst().id());
        assertEquals("Enabled", config.rules().getFirst().status());
    }
}
