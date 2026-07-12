package com.example.magrathea.storageengine.application.port;

/** Raised before body consumption when an atomic logical-byte reservation exceeds quota. */
public final class BucketQuotaExceededException extends RuntimeException {
    private final BucketCapacityPort.BucketCapacity capacity;
    private final long requestedBytes;

    public BucketQuotaExceededException(
            BucketCapacityPort.BucketCapacity capacity, long requestedBytes) {
        super("Bucket quota exceeded: bucket=" + capacity.bucket()
                + " requestedBytes=" + requestedBytes
                + " usedBytes=" + capacity.usedBytes()
                + " reservedBytes=" + capacity.reservedBytes()
                + " quotaBytes=" + capacity.quotaBytes());
        this.capacity = capacity;
        this.requestedBytes = requestedBytes;
    }

    public BucketCapacityPort.BucketCapacity capacity() {
        return capacity;
    }

    public long requestedBytes() {
        return requestedBytes;
    }
}
