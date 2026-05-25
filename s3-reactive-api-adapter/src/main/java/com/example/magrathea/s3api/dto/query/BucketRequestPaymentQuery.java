package com.example.magrathea.s3api.dto.query;

import com.example.magrathea.objectstorage.domain.aggregate.Bucket;
import com.example.magrathea.objectstorage.domain.valueobject.BucketRequestPaymentConfiguration;
import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import tools.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import java.util.Optional;

/**
 * Response for GET /{bucket}?requestPayment (GetBucketRequestPayment).
 */
@JacksonXmlRootElement(localName = "RequestPaymentConfiguration")
public record BucketRequestPaymentQuery(
    @JacksonXmlProperty(localName = "Payer")
    String payer
) {
    public static BucketRequestPaymentQuery from(Optional<BucketRequestPaymentConfiguration> config) {
        var c = config.orElseThrow(() -> new IllegalArgumentException("No request payment configuration"));
        return new BucketRequestPaymentQuery(c.payer());
    }

}
