package com.example.magrathea.s3api.dto.query;

import com.example.magrathea.objectstore.domain.aggregate.Bucket;
import com.example.magrathea.objectstore.domain.valueobject.BucketOwnershipControls;
import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import tools.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import java.util.Optional;

/**
 * Response for GET /{bucket}?ownershipControls (GetBucketOwnershipControls).
 */
@JacksonXmlRootElement(localName = "OwnershipControls")
public record BucketOwnershipControlsQuery(
    @JacksonXmlProperty(localName = "ID")
    String ruleId,
    @JacksonXmlProperty(localName = "Ownership")
    String ownership
) {
    public static BucketOwnershipControlsQuery from(Optional<BucketOwnershipControls> controls) {
        var c = controls.orElseThrow(() -> new IllegalArgumentException("No ownership controls"));
        return new BucketOwnershipControlsQuery(c.ruleId(), c.ownership());
    }

}
