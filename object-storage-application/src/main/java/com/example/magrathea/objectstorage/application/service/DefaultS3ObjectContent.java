package com.example.magrathea.objectstorage.application.service;

import com.example.magrathea.objectstorage.domain.aggregate.S3Object;
import com.example.magrathea.objectstorage.domain.aggregate.S3ObjectContent;
import org.springframework.core.io.buffer.DataBuffer;
import reactor.core.publisher.Flux;

/**
 * Application implementation of the domain S3ObjectContent contract.
 */
public record DefaultS3ObjectContent(
    S3Object.Id objectId,
    Flux<DataBuffer> content
) implements S3ObjectContent {
}
