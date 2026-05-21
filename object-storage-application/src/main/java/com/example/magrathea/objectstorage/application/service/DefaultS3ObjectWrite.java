package com.example.magrathea.objectstorage.application.service;

import com.example.magrathea.objectstorage.domain.aggregate.S3Object;
import com.example.magrathea.objectstorage.domain.aggregate.S3ObjectWrite;
import org.springframework.core.io.buffer.DataBuffer;
import reactor.core.publisher.Flux;

/**
 * Application implementation of the domain S3ObjectWrite contract.
 * Carries the framework-specific content stream at the application boundary.
 */
public record DefaultS3ObjectWrite(
    S3Object s3Object,
    Flux<DataBuffer> content
) implements S3ObjectWrite {
}
