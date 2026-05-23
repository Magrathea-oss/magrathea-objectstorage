package com.example.magrathea.s3api.dto.query;

import com.example.magrathea.objectstorage.domain.valueobject.BucketEncryptionConfiguration;
import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import tools.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import java.util.Optional;

/**
 * Response for GET /{bucket}?encryption (GetBucketEncryption).
 */
@JacksonXmlRootElement(localName = "ServerSideEncryptionConfiguration")
public record BucketEncryptionQuery(
    @JacksonXmlProperty(localName = "RuleID")
    String ruleId,
    @JacksonXmlProperty(localName = "Algorithm")
    String algorithm,
    @JacksonXmlProperty(localName = "KMSKeyId")
    String kmsKeyId
) {
    public static BucketEncryptionQuery from(Optional<BucketEncryptionConfiguration> config) {
        var c = config.orElseThrow(() -> new IllegalArgumentException("No encryption configuration"));
        return new BucketEncryptionQuery(c.ruleId(), c.algorithm(), c.kmsKeyId());
    }
}
