package com.example.magrathea.objectstorage.domain.valueobject;

import java.util.List;
import java.util.Objects;

/**
 * AWS S3 bucket replication configuration — value object.
 * Pure domain — NO framework dependencies.
 */
public record BucketReplicationConfiguration(
    String bucketName,
    String role,
    List<ReplicationRule> rules
) {
    public BucketReplicationConfiguration {
        Objects.requireNonNull(bucketName);
        if (rules != null) {
            rules = List.copyOf(rules);
        }
    }

    public record ReplicationRule(
        String id,
        String status,
        String prefix,
        String destinationBucket,
        String destinationStorageClass,
        boolean replicaKmsKeyId,
        boolean deleteMarkerReplicationEnabled
    ) {}

    public boolean hasReplication() {
        return rules != null && !rules.isEmpty();
    }
}
