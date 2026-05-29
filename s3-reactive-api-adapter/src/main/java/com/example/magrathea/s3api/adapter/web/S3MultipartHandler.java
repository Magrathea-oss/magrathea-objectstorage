package com.example.magrathea.s3api.adapter.web;

import com.example.magrathea.objectstore.domain.aggregate.Bucket;
import com.example.magrathea.objectstore.domain.aggregate.MultipartUpload;
import com.example.magrathea.objectstore.domain.valueobject.ObjectKey;
import com.example.magrathea.objectstore.domain.valueobject.PartNumber;
import com.example.magrathea.objectstore.domain.valueobject.UploadId;
import com.example.magrathea.reactive.application.service.ReactiveBucketService;
import com.example.magrathea.reactive.application.service.ReactiveMultipartUploadService;
import com.example.magrathea.s3api.dto.query.ErrorQuery;
import com.example.magrathea.s3api.dto.query.InitiateMultipartUploadQuery;
import com.example.magrathea.s3api.dto.query.UploadPartResultQuery;
import com.example.magrathea.s3api.dto.query.CompleteMultipartUploadQuery;
import com.example.magrathea.s3api.dto.query.ListMultipartUploadsQuery;
import com.example.magrathea.s3api.dto.query.ListPartsQuery;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.DigestUtils;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Multipart upload S3 operations handler.
 * Minimal handler — delegates bucket existence checks to service, removes handler-level checks.
 * POST /{bucket}/{key}?uploads — CreateMultipartUpload
 * PUT /{bucket}/{key}?uploadId=...&partNumber=... — UploadPart
 * PUT /{bucket}/{key}?uploadId=...&partNumber=...&x-amz-copy-source — UploadPartCopy
 * POST /{bucket}/{key}?uploadId=... — CompleteMultipartUpload
 * DELETE /{bucket}/{key}?uploadId=... — AbortMultipartUpload
 * GET /{bucket}?uploads — ListMultipartUploads
 * GET /{bucket}/{key}?uploadId=... — ListParts
 */
public class S3MultipartHandler {

    private final ReactiveMultipartUploadService multipartUploadService;
    private final ReactiveBucketService bucketService;

    public S3MultipartHandler(ReactiveMultipartUploadService multipartUploadService,
                               ReactiveBucketService bucketService) {
        this.multipartUploadService = multipartUploadService;
        this.bucketService = bucketService;
    }

    /** POST /{bucket}/{key}?uploads — Initiate multipart upload */
    public Mono<ServerResponse> initiateMultipartUpload(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        var key = request.pathVariable("key");
        return bucketService.findByName(bucket)
            .flatMap(b -> {
                var uploadId = UploadId.generate();
                var upload = MultipartUpload.create(
                    MultipartUpload.Id.generate(), b.id(), ObjectKey.of(bucket, key), uploadId
                );
                return multipartUploadService.saveUpload(upload)
                    .then(ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_XML)
                        .bodyValue(InitiateMultipartUploadQuery.from(
                            bucket, key, upload.uploadId().value())));
            })
            .onErrorResume(ex -> ServerResponse.status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_XML)
                .bodyValue(ErrorQuery.from("NoSuchBucket", "Bucket not found")));
    }

    /** PUT /{bucket}/{key}?uploadId=...&partNumber=... + x-amz-copy-source — UploadPartCopy */
    public Mono<ServerResponse> uploadPartCopy(ServerRequest request) {
        var uploadIdStr = request.queryParam("uploadId").orElse("");
        var partNumberStr = request.queryParam("partNumber").orElse("");
        var uploadId = UploadId.of(uploadIdStr);
        int partNumber = Integer.parseInt(partNumberStr);
        var copySource = request.headers().firstHeader("x-amz-copy-source");
        if (copySource == null) {
            return ServerResponse.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_XML)
                .bodyValue(ErrorQuery.from("InvalidArgument", "x-amz-copy-source header required"));
        }
        return multipartUploadService.findById(uploadId)
            .flatMap(upload -> {
                var etag = "\"" + DigestUtils.md5DigestAsHex((uploadIdStr + "-" + partNumberStr).getBytes()) + "\"";
                var part = com.example.magrathea.objectstore.domain.valueobject.UploadPart.create(
                    PartNumber.of(partNumber), etag, 0
                );
                var updated = upload.withPart(part);
                return multipartUploadService.saveUpload(updated)
                    .then(ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_XML)
                        .bodyValue(UploadPartResultQuery.from(etag)));
            })
            .switchIfEmpty(ServerResponse.status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_XML)
                .bodyValue(ErrorQuery.from("NoSuchUpload", "UploadId not found")));
    }

    /** PUT /{bucket}/{key}?uploadId=...&partNumber=... — Upload a part */
    public Mono<ServerResponse> uploadPart(ServerRequest request) {
        var uploadIdStr = request.queryParam("uploadId").orElse("");
        var partNumberStr = request.queryParam("partNumber").orElse("");
        var uploadId = UploadId.of(uploadIdStr);
        int partNumber = Integer.parseInt(partNumberStr);
        var size = request.headers().contentLength().orElse(0L);

        return multipartUploadService.findById(uploadId)
            .flatMap(upload -> {
                var etag = "\"" + DigestUtils.md5DigestAsHex((uploadIdStr + "-" + partNumberStr).getBytes()) + "\"";
                var part = com.example.magrathea.objectstore.domain.valueobject.UploadPart.create(
                    PartNumber.of(partNumber), etag, size
                );
                var updated = upload.withPart(part);
                return multipartUploadService.saveUpload(updated)
                    .then(ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_XML)
                        .bodyValue(UploadPartResultQuery.from(etag)));
            })
            .switchIfEmpty(ServerResponse.status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_XML)
                .bodyValue(ErrorQuery.from("NoSuchUpload", "UploadId not found")));
    }

    /** POST /{bucket}/{key}?uploadId=... — Complete multipart upload */
    public Mono<ServerResponse> completeMultipartUpload(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        var key = request.pathVariable("key");
        var uploadIdStr = request.queryParam("uploadId").orElse("");
        var uploadId = UploadId.of(uploadIdStr);

        return multipartUploadService.findById(uploadId)
            .flatMap(upload -> {
                var completed = upload.withCompleted();
                return multipartUploadService.saveUpload(completed)
                    .then(Mono.defer(() -> {
                        var finalEtag = "\"" + DigestUtils.md5DigestAsHex(uploadIdStr.getBytes()) + "\"";
                        var result = CompleteMultipartUploadQuery.from(bucket, key, finalEtag);
                        return ServerResponse.ok()
                            .contentType(MediaType.APPLICATION_XML)
                            .bodyValue(result);
                    }));
            })
            .switchIfEmpty(ServerResponse.status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_XML)
                .bodyValue(ErrorQuery.from("NoSuchUpload", "UploadId not found")));
    }

    /** DELETE /{bucket}/{key}?uploadId=... — Abort multipart upload */
    public Mono<ServerResponse> abortMultipartUpload(ServerRequest request) {
        var uploadIdStr = request.queryParam("uploadId").orElse("");
        var uploadId = UploadId.of(uploadIdStr);

        return multipartUploadService.findById(uploadId)
            .flatMap(upload -> {
                var aborted = upload.withAborted();
                return multipartUploadService.saveUpload(aborted)
                    .then(ServerResponse.noContent().build());
            })
            .switchIfEmpty(ServerResponse.status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_XML)
                .bodyValue(ErrorQuery.from("NoSuchUpload", "UploadId not found")));
    }

    /** GET /{bucket}?uploads — List multipart uploads */
    public Mono<ServerResponse> listMultipartUploads(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        return bucketService.findByName(bucket)
            .flatMap(b -> {
                Flux<ListMultipartUploadsQuery.UploadEntry> entries = multipartUploadService.findByBucket(b.id())
                    .map(u -> ListMultipartUploadsQuery.UploadEntry.from(
                        u.key().key(), u.uploadId().value(), u.initiated()
                    ));
                return ListMultipartUploadsQuery.from(bucket, entries)
                    .flatMap(result -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_XML)
                        .bodyValue(result));
            })
            .onErrorResume(ex -> ServerResponse.status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_XML)
                .bodyValue(ErrorQuery.from("NoSuchBucket", "Bucket not found")));
    }

    /** GET /{bucket}/{key}?uploadId=... — List parts */
    public Mono<ServerResponse> listParts(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        var key = request.pathVariable("key");
        var uploadIdStr = request.queryParam("uploadId").orElse("");
        var uploadId = UploadId.of(uploadIdStr);

        return multipartUploadService.findById(uploadId)
            .flatMap(upload -> {
                Flux<ListPartsQuery.PartEntry> partEntries = Flux.fromIterable(upload.parts())
                    .map(p -> ListPartsQuery.PartEntry.from(p.partNumber().value(), p.etag()));
                return ListPartsQuery.from(
                    bucket, key, upload.uploadId().value(), partEntries
                )
                .flatMap(result -> ServerResponse.ok()
                    .contentType(MediaType.APPLICATION_XML)
                    .bodyValue(result));
            })
            .switchIfEmpty(ServerResponse.status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_XML)
                .bodyValue(ErrorQuery.from("NoSuchUpload", "UploadId not found")));
    }
}
