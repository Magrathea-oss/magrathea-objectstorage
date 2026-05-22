package com.example.magrathea.s3api.adapter.web;

import com.example.magrathea.objectstorage.application.service.MultipartUploadService;
import com.example.magrathea.s3api.adapter.web.xml.S3XmlResponses;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Multipart upload S3 operations handler.
 * POST /{bucket}/{key}?uploads — CreateMultipartUpload
 * PUT /{bucket}/{key}?uploadId=...&partNumber=... — UploadPart
 * PUT /{bucket}/{key}?uploadId=...&partNumber=...&x-amz-copy-source — UploadPartCopy
 * POST /{bucket}/{key}?uploadId=... — CompleteMultipartUpload
 * DELETE /{bucket}/{key}?uploadId=... — AbortMultipartUpload
 * GET /{bucket}?uploads — ListMultipartUploads
 * GET /{bucket}/{key}?uploadId=... — ListParts
 */
public class S3MultipartHandler {

    private final MultipartUploadService multipartUploadService;

    public S3MultipartHandler(MultipartUploadService multipartUploadService) {
        this.multipartUploadService = multipartUploadService;
    }

    /** POST /{bucket}/{key}?uploads — Initiate multipart upload */
    public Mono<ServerResponse> initiateMultipartUpload(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        var key = request.pathVariable("key");
        try {
            var upload = multipartUploadService.createUpload(bucket, key);
            var result = S3XmlResponses.InitiateMultipartUploadResult.from(
                bucket, key, upload.uploadId().value()
            );
            return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_XML)
                .bodyValue(result);
        } catch (IllegalArgumentException e) {
            return ServerResponse.status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_XML)
                .bodyValue(S3XmlResponses.Error.from("NoSuchBucket", e.getMessage()));
        }
    }

    /** PUT /{bucket}/{key}?uploadId=...&partNumber=... + x-amz-copy-source — UploadPartCopy */
    public Mono<ServerResponse> uploadPartCopy(ServerRequest request) {
        var uploadIdStr = request.queryParam("uploadId").orElse("");
        var partNumberStr = request.queryParam("partNumber").orElse("");
        var uploadId = com.example.magrathea.objectstorage.domain.valueobject.UploadId.of(uploadIdStr);
        int partNumber = Integer.parseInt(partNumberStr);
        var copySource = request.headers().firstHeader("x-amz-copy-source");
        if (copySource == null) {
            return ServerResponse.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_XML)
                .bodyValue(S3XmlResponses.Error.from("InvalidArgument", "x-amz-copy-source header required"));
        }
        try {
            var etag = "\"" + java.util.UUID.randomUUID().toString() + "\"";
            var part = multipartUploadService.uploadPart(uploadId, partNumber, etag, 0);
            var result = S3XmlResponses.UploadPartResult.from(part.etag());
            return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_XML)
                .bodyValue(result);
        } catch (IllegalArgumentException e) {
            return ServerResponse.status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_XML)
                .bodyValue(S3XmlResponses.Error.from("NoSuchUpload", e.getMessage()));
        }
    }

    /** PUT /{bucket}/{key}?uploadId=...&partNumber=... — Upload a part */
    public Mono<ServerResponse> uploadPart(ServerRequest request) {
        var uploadIdStr = request.queryParam("uploadId").orElse("");
        var partNumberStr = request.queryParam("partNumber").orElse("");
        var uploadId = com.example.magrathea.objectstorage.domain.valueobject.UploadId.of(uploadIdStr);
        int partNumber = Integer.parseInt(partNumberStr);
        var size = request.headers().contentLength().orElse(0L);

        return request.bodyToMono(DataBuffer.class)
            .flatMap(dataBuffer -> {
                try {
                    var etag = "\"" + java.util.UUID.randomUUID().toString() + "\"";
                    var part = multipartUploadService.uploadPart(uploadId, partNumber, etag, size);
                    var result = S3XmlResponses.UploadPartResult.from(part.etag());
                    return ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_XML)
                        .bodyValue(result);
                } catch (IllegalArgumentException e) {
                    return ServerResponse.status(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_XML)
                        .bodyValue(S3XmlResponses.Error.from("NoSuchUpload", e.getMessage()));
                }
            });
    }

    /** POST /{bucket}/{key}?uploadId=... — Complete multipart upload */
    public Mono<ServerResponse> completeMultipartUpload(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        var key = request.pathVariable("key");
        var uploadIdStr = request.queryParam("uploadId").orElse("");
        var uploadId = com.example.magrathea.objectstorage.domain.valueobject.UploadId.of(uploadIdStr);

        return Mono.fromFuture(multipartUploadService.completeUpload(uploadId))
            .flatMap(maybeUpload -> {
                if (maybeUpload.isEmpty()) {
                    return ServerResponse.status(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_XML)
                        .bodyValue(S3XmlResponses.Error.from("NoSuchUpload", "UploadId not found"));
                }
                var upload = maybeUpload.get();
                var finalEtag = "\"" + java.util.UUID.randomUUID().toString() + "\"";
                var result = S3XmlResponses.CompleteMultipartUploadResult.from(bucket, key, finalEtag);
                return ServerResponse.ok()
                    .contentType(MediaType.APPLICATION_XML)
                    .bodyValue(result);
            });
    }

    /** DELETE /{bucket}/{key}?uploadId=... — Abort multipart upload */
    public Mono<ServerResponse> abortMultipartUpload(ServerRequest request) {
        var uploadIdStr = request.queryParam("uploadId").orElse("");
        var uploadId = com.example.magrathea.objectstorage.domain.valueobject.UploadId.of(uploadIdStr);

        return Mono.fromFuture(multipartUploadService.abortUpload(uploadId))
            .flatMap(maybeUpload -> {
                if (maybeUpload.isPresent()) {
                    return ServerResponse.noContent().build();
                }
                return ServerResponse.status(HttpStatus.NOT_FOUND)
                    .contentType(MediaType.APPLICATION_XML)
                    .bodyValue(S3XmlResponses.Error.from("NoSuchUpload", "UploadId not found"));
            });
    }

    /** GET /{bucket}?uploads — List multipart uploads */
    public Mono<ServerResponse> listMultipartUploads(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        try {
            var uploads = multipartUploadService.listUploads(bucket).join();
            var entries = uploads.stream()
                .map(u -> S3XmlResponses.UploadEntry.from(
                    u.key().value(), u.uploadId().value(), u.initiated()
                ))
                .toList();
            var result = S3XmlResponses.ListMultipartUploadsResult.from(bucket, entries);
            return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_XML)
                .bodyValue(result);
        } catch (IllegalArgumentException e) {
            return ServerResponse.status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_XML)
                .bodyValue(S3XmlResponses.Error.from("NoSuchBucket", e.getMessage()));
        }
    }

    /** GET /{bucket}/{key}?uploadId=... — List parts */
    public Mono<ServerResponse> listParts(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        var key = request.pathVariable("key");
        var uploadIdStr = request.queryParam("uploadId").orElse("");
        var uploadId = com.example.magrathea.objectstorage.domain.valueobject.UploadId.of(uploadIdStr);

        return Mono.fromFuture(multipartUploadService.listParts(uploadId))
            .flatMap(maybeUpload -> {
                if (maybeUpload.isEmpty()) {
                    return ServerResponse.status(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_XML)
                        .bodyValue(S3XmlResponses.Error.from("NoSuchUpload", "UploadId not found"));
                }
                var upload = maybeUpload.get();
                var partEntries = upload.parts().stream()
                    .map(S3XmlResponses.PartEntry::from)
                    .toList();
                var result = S3XmlResponses.ListPartsResult.from(
                    bucket, key, upload.uploadId().value(), partEntries
                );
                return ServerResponse.ok()
                    .contentType(MediaType.APPLICATION_XML)
                    .bodyValue(result);
            });
    }
}
