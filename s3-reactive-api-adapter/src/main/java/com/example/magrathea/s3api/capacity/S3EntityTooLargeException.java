package com.example.magrathea.s3api.capacity;

/** Internal streaming signal mapped to the bounded S3 EntityTooLarge response. */
public final class S3EntityTooLargeException extends RuntimeException {
    public S3EntityTooLargeException(long maximumBytes) {
        super("Request body exceeds the configured " + maximumBytes + " byte limit");
    }
}
