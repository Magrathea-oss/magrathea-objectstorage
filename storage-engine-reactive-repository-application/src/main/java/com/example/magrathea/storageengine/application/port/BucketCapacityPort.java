package com.example.magrathea.storageengine.application.port;

import reactor.core.publisher.Mono;

/**
 * Durable logical-byte quota accounting for storage-engine buckets.
 * Reservations are atomic and remain private until an object reference commits.
 */
public interface BucketCapacityPort {

    Mono<Reservation> reserve(String bucket, String objectKey, long requestedBytes, long replacedBytes);

    Mono<BucketCapacity> configureQuota(String bucket, long quotaBytes);

    Mono<BucketCapacity> capacity(String bucket);

    /** Adjusts an active reservation to the measured streaming byte count before publication. */
    Mono<Reservation> resize(Reservation reservation, long requestedBytes);

    record Reservation(
            String id,
            String bucket,
            String objectKey,
            long requestedBytes,
            long replacedBytes) {
    }

    record BucketCapacity(
            String bucket,
            long usedBytes,
            long reservedBytes,
            long quotaBytes,
            long rejectedReservations,
            long lastRejectedBytes) {
        public boolean limited() {
            return quotaBytes >= 0;
        }
    }

    Mono<BucketCapacity> commit(Reservation reservation, long committedBytes);

    Mono<Void> release(Reservation reservation);
}
