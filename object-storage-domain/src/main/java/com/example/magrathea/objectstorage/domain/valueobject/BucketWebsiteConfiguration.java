package com.example.magrathea.objectstorage.domain.valueobject;

import java.util.List;
import java.util.Objects;

/**
 * AWS S3 bucket website configuration — value object.
 * Pure domain — NO framework dependencies.
 */
public record BucketWebsiteConfiguration(
    String bucketName,
    String indexDocument,
    String errorDocument,
    String redirectAllRequestsTo,
    String routingRuleRedirectHost,
    String routingRuleRedirectProtocol
) {
    public BucketWebsiteConfiguration {
        Objects.requireNonNull(bucketName);
    }

    public boolean hasWebsite() {
        return (indexDocument != null && !indexDocument.isBlank())
            || (redirectAllRequestsTo != null && !redirectAllRequestsTo.isBlank());
    }
}
