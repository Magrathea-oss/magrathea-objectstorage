package com.example.magrathea.s3api.adapter.web;

import com.example.magrathea.objectstore.domain.aggregate.ActiveS3Object;
import com.example.magrathea.objectstore.domain.aggregate.Bucket;
import com.example.magrathea.objectstore.domain.aggregate.MultipartUpload;
import com.example.magrathea.objectstore.domain.valueobject.ObjectChecksum;
import com.example.magrathea.objectstore.domain.valueobject.ObjectKey;
import com.example.magrathea.objectstore.domain.valueobject.PartNumber;
import com.example.magrathea.objectstore.domain.valueobject.UploadId;
import com.example.magrathea.reactive.application.service.ReactiveMultipartUploadService;
import com.example.magrathea.reactive.application.service.ReactiveObjectService;
import com.example.magrathea.s3api.adapter.web.headers.S3RequestExtractor;
import com.example.magrathea.s3api.capacity.S3CapacityProperties;
import com.example.magrathea.s3api.dto.query.ErrorQuery;
import com.example.magrathea.s3api.dto.query.InitiateMultipartUploadQuery;
import com.example.magrathea.s3api.dto.query.UploadPartResultQuery;
import com.example.magrathea.s3api.dto.query.UploadPartCopyResultQuery;
import com.example.magrathea.s3api.dto.query.CompleteMultipartUploadQuery;
import com.example.magrathea.s3api.dto.query.ListMultipartUploadsQuery;
import com.example.magrathea.s3api.dto.query.ListPartsQuery;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.xml.parsers.DocumentBuilderFactory;

/**
 * Multipart upload S3 operations handler.
 * Minimal handler — extracts HTTP headers, delegates to service, converts response.
 * No bucket check, no ETag computation.
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
    private final ReactiveObjectService objectService;
    private final S3MultipartPartStore partStore;
    private final S3CapacityProperties capacityProperties;

    public S3MultipartHandler(ReactiveMultipartUploadService multipartUploadService,
                              ReactiveObjectService objectService,
                              S3MultipartPartStore partStore) {
        this(multipartUploadService, objectService, partStore, new S3CapacityProperties());
    }

    public S3MultipartHandler(ReactiveMultipartUploadService multipartUploadService,
                              ReactiveObjectService objectService,
                              S3MultipartPartStore partStore,
                              S3CapacityProperties capacityProperties) {
        this.multipartUploadService = multipartUploadService;
        this.objectService = objectService;
        this.partStore = partStore;
        this.capacityProperties = capacityProperties;
    }

    /** POST /{bucket}/{key}?uploads — Initiate multipart upload */
    public Mono<ServerResponse> initiateMultipartUpload(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        var key = S3RequestExtractor.extractObjectKeyValue(request);
        var uploadId = UploadId.generate();
        // TODO: bucket check postponed — service/repository handles bucket resolution
        var upload = MultipartUpload.create(
            MultipartUpload.Id.generate(), Bucket.Id.of(bucket), ObjectKey.of(bucket, key), uploadId
        );
        return multipartUploadService.saveUpload(upload)
            .then(ServerResponse.ok()
                .contentType(MediaType.APPLICATION_XML)
                .bodyValue(InitiateMultipartUploadQuery.from(
                    bucket, key, upload.uploadId().value())));
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
        var source = S3WebSupport.decodeCopySource(copySource);
        if (source.isEmpty()) {
            return ServerResponse.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_XML)
                .bodyValue(ErrorQuery.from("InvalidArgument", "Invalid x-amz-copy-source header"));
        }
        var sourceObjectKey = ObjectKey.of(source.get()[0], source.get()[1]);
        var targetPartNumber = PartNumber.of(partNumber);

        return multipartUploadService.findById(uploadId)
            .flatMap(upload -> {
                if (!upload.isActive()) {
                    return xmlError(HttpStatus.NOT_FOUND, "NoSuchUpload", "UploadId not found");
                }
                return objectService.getObject(sourceObjectKey)
                .flatMap(sourceObject -> {
                    if (capacityProperties.isEnabled()
                        && sourceObject.size() > capacityProperties.getMaxMultipartPartBytes()) {
                        return xmlError(HttpStatus.PAYLOAD_TOO_LARGE, "EntityTooLarge",
                            "The copied part exceeds the configured maximum allowed part size");
                    }
                    return partStore.savePart(uploadId, targetPartNumber, objectService.getContent(sourceObjectKey))
                    .flatMap(storedPart -> {
                        var part = com.example.magrathea.objectstore.domain.valueobject.UploadPart.create(
                            targetPartNumber, storedPart.etag(), storedPart.size());
                        var updated = upload.withPart(part);
                        return multipartUploadService.saveUpload(updated)
                            .then(ServerResponse.ok()
                                .header("ETag", storedPart.etag())
                                .contentType(MediaType.APPLICATION_XML)
                                .bodyValue(UploadPartCopyResultQuery.from(storedPart.etag())));
                    });
                })
                .switchIfEmpty(ServerResponse.status(HttpStatus.NOT_FOUND)
                    .contentType(MediaType.APPLICATION_XML)
                    .bodyValue(ErrorQuery.from("NoSuchKey", "Copy source not found")));
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

        return multipartUploadService.findById(uploadId)
            .flatMap(upload -> {
                if (!upload.isActive()) {
                    return xmlError(HttpStatus.NOT_FOUND, "NoSuchUpload", "UploadId not found");
                }
                return partStore.savePart(uploadId, PartNumber.of(partNumber), request.bodyToFlux(DataBuffer.class))
                    .flatMap(storedPart -> {
                        var part = com.example.magrathea.objectstore.domain.valueobject.UploadPart.create(
                            PartNumber.of(partNumber), storedPart.etag(), storedPart.size()
                        );
                        var updated = upload.withPart(part);
                        return multipartUploadService.saveUpload(updated)
                            .then(ServerResponse.ok()
                                .header("ETag", storedPart.etag())
                                .contentType(MediaType.APPLICATION_XML)
                                .bodyValue(UploadPartResultQuery.from(storedPart.etag())));
                    });
            })
            .switchIfEmpty(ServerResponse.status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_XML)
                .bodyValue(ErrorQuery.from("NoSuchUpload", "UploadId not found")));
    }

    /** POST /{bucket}/{key}?uploadId=... — Complete multipart upload */
    public Mono<ServerResponse> completeMultipartUpload(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        var key = S3RequestExtractor.extractObjectKeyValue(request);
        var uploadIdStr = request.queryParam("uploadId").orElse("");
        var uploadId = UploadId.of(uploadIdStr);

        return request.bodyToMono(String.class)
            .defaultIfEmpty("")
            .flatMap(body -> {
                List<CompletePartSpec> requestedParts;
                try {
                    requestedParts = parseCompleteMultipartUploadParts(body);
                } catch (IllegalArgumentException ex) {
                    return xmlError(HttpStatus.BAD_REQUEST, "MalformedXML",
                        "The XML you provided was not well-formed or did not validate against our published schema");
                }
                return multipartUploadService.findById(uploadId)
                    .flatMap(upload -> {
                        if (!upload.isActive()) {
                            return xmlError(HttpStatus.NOT_FOUND, "NoSuchUpload", "UploadId not found");
                        }
                        var sortedParts = resolveCompleteParts(upload, requestedParts);
                        if (sortedParts == null) {
                            return xmlError(HttpStatus.BAD_REQUEST, "InvalidPart", "One or more of the specified parts could not be found or had an invalid ETag");
                        }
                        String finalEtag;
                        if (sortedParts.isEmpty()) {
                            finalEtag = "\"\"";
                        } else {
                            var partEtags = sortedParts.stream()
                                .map(com.example.magrathea.objectstore.domain.valueobject.UploadPart::etag)
                                .collect(Collectors.toList());
                            finalEtag = ETagComputer.computeMultipartETag(partEtags);
                        }

                        var objectKey = ObjectKey.of(bucket, key);
                        long totalSize;
                        try {
                            totalSize = sortedParts.stream().mapToLong(part -> part.size()).reduce(0L, Math::addExact);
                        } catch (ArithmeticException overflow) {
                            totalSize = Long.MAX_VALUE;
                        }
                        if (capacityProperties.isEnabled()
                            && totalSize > capacityProperties.getMaxAssembledMultipartBytes()) {
                            var aborted = upload.withAborted();
                            return multipartUploadService.saveUpload(aborted)
                                .then(partStore.deleteUpload(uploadId))
                                .then(xmlError(HttpStatus.PAYLOAD_TOO_LARGE, "EntityTooLarge",
                                    "The assembled object exceeds the configured maximum allowed object size"));
                        }
                        var completedObject = ActiveS3Object.create(
                                objectKey,
                                "STANDARD",
                                Map.of(),
                                null,
                                ObjectChecksum.of(Set.of()),
                                totalSize)
                            .withEtag(finalEtag);
                        var assembledContent = Flux.concat(sortedParts.stream()
                            .map(part -> partStore.readPart(uploadId, part.partNumber()))
                            .toList());
                        var completed = upload.withCompleted();
                        var result = CompleteMultipartUploadQuery.from(bucket, key, finalEtag);

                        return objectService.saveObjectWithContent(completedObject, assembledContent, "STANDARD")
                            .then(multipartUploadService.saveUpload(completed))
                            .then(partStore.deleteUpload(uploadId))
                            .then(ServerResponse.ok()
                                .contentType(MediaType.APPLICATION_XML)
                                .bodyValue(result));
                    })
                    .switchIfEmpty(xmlError(HttpStatus.NOT_FOUND, "NoSuchUpload", "UploadId not found"));
            });
    }

    /** DELETE /{bucket}/{key}?uploadId=... — Abort multipart upload */
    public Mono<ServerResponse> abortMultipartUpload(ServerRequest request) {
        var uploadIdStr = request.queryParam("uploadId").orElse("");
        var uploadId = UploadId.of(uploadIdStr);

        return multipartUploadService.findById(uploadId)
            .flatMap(upload -> {
                var aborted = upload.withAborted();
                return multipartUploadService.saveUpload(aborted)
                    .then(partStore.deleteUpload(uploadId))
                    .then(ServerResponse.noContent().build());
            })
            .switchIfEmpty(ServerResponse.status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_XML)
                .bodyValue(ErrorQuery.from("NoSuchUpload", "UploadId not found")));
    }

    /** GET /{bucket}?uploads — List multipart uploads */
    public Mono<ServerResponse> listMultipartUploads(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        // TODO: bucket check postponed — service/repository handles bucket resolution
        Flux<ListMultipartUploadsQuery.UploadEntry> entries = multipartUploadService.findByBucket(Bucket.Id.of(bucket))
            .filter(MultipartUpload::isActive)
            .map(u -> ListMultipartUploadsQuery.UploadEntry.from(
                u.key().key(), u.uploadId().value(), u.initiated()
            ));
        return ListMultipartUploadsQuery.from(bucket, entries)
            .flatMap(result -> ServerResponse.ok()
                .contentType(MediaType.APPLICATION_XML)
                .bodyValue(result));
    }

    /** GET /{bucket}/{key}?uploadId=... — List parts */
    public Mono<ServerResponse> listParts(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        var key = S3RequestExtractor.extractObjectKeyValue(request);
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

    private static Mono<ServerResponse> xmlError(HttpStatus status, String code, String message) {
        return ServerResponse.status(status)
            .contentType(MediaType.APPLICATION_XML)
            .bodyValue(ErrorQuery.from(code, message));
    }

    private static List<CompletePartSpec> parseCompleteMultipartUploadParts(String body) {
        if (body == null || body.isBlank()) {
            return List.of();
        }
        var trimmed = body.trim();
        if (!trimmed.startsWith("<")) {
            throw new IllegalArgumentException("CompleteMultipartUpload body is not XML");
        }
        try {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setExpandEntityReferences(false);
            var document = factory.newDocumentBuilder()
                .parse(new ByteArrayInputStream(trimmed.getBytes(StandardCharsets.UTF_8)));
            var root = document.getDocumentElement();
            if (root == null || !"CompleteMultipartUpload".equals(root.getNodeName())) {
                throw new IllegalArgumentException("Unexpected root element");
            }
            var nodes = root.getElementsByTagName("Part");
            var parts = new ArrayList<CompletePartSpec>();
            for (int i = 0; i < nodes.getLength(); i++) {
                var children = nodes.item(i).getChildNodes();
                String partNumber = null;
                String etag = null;
                for (int j = 0; j < children.getLength(); j++) {
                    var child = children.item(j);
                    if ("PartNumber".equals(child.getNodeName())) {
                        partNumber = child.getTextContent();
                    } else if ("ETag".equals(child.getNodeName())) {
                        etag = child.getTextContent();
                    }
                }
                if (partNumber == null || partNumber.isBlank()) {
                    throw new IllegalArgumentException("PartNumber is required");
                }
                parts.add(new CompletePartSpec(Integer.parseInt(partNumber.trim()), etag));
            }
            return parts;
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Malformed CompleteMultipartUpload XML", ex);
        }
    }

    private static List<com.example.magrathea.objectstore.domain.valueobject.UploadPart> resolveCompleteParts(
        MultipartUpload upload,
        List<CompletePartSpec> requestedParts
    ) {
        var availableByNumber = upload.parts().stream()
            .collect(Collectors.toMap(part -> part.partNumber().value(), part -> part, (left, right) -> right));
        if (requestedParts == null || requestedParts.isEmpty()) {
            return upload.parts().stream()
                .sorted(Comparator.comparingInt(part -> part.partNumber().value()))
                .collect(Collectors.toList());
        }
        var resolved = new ArrayList<com.example.magrathea.objectstore.domain.valueobject.UploadPart>();
        for (var requested : requestedParts) {
            var actual = availableByNumber.get(requested.partNumber());
            if (actual == null) {
                return null;
            }
            if (requested.etag() != null && !requested.etag().isBlank()
                && !normalizeEtag(requested.etag()).equals(normalizeEtag(actual.etag()))) {
                return null;
            }
            resolved.add(actual);
        }
        return resolved;
    }

    private static String normalizeEtag(String etag) {
        return etag == null ? "" : etag.trim().replace("&quot;", "\"");
    }

    private record CompletePartSpec(int partNumber, String etag) {
    }
}
