package com.example.magrathea.reactive.infrastructure.adapter.persistence;

import com.example.magrathea.objectstore.domain.aggregate.Bucket;

public class BucketNotFoundException extends RuntimeException {
    private final Bucket.Id bucketId;

    public BucketNotFoundException(Bucket.Id bucketId) {
        super("Bucket not found: " + bucketId.value());
        this.bucketId = bucketId;
    }

    public Bucket.Id bucketId() { return bucketId; }
}
