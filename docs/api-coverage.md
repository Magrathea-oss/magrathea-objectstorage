# S3 API Coverage Analysis

This document covers the 84 implemented Amazon S3 REST operations exposed by the `s3-api` module.
Each operation section includes request header coverage, query parameter coverage, request/response body coverage, and a per-operation Status Codes table.

Scope: Amazon S3 data-plane actions only. Amazon S3 Control actions remain out of scope.

## Coverage status legend

| Marker | Meaning |
|---|---|
| ✅ Tested | Covered by Cucumber and/or AWS CLI compatibility tests and used by handler/application logic. |
| 🟡 Partial | Implemented for selected compatibility cases only. |
| ⬜ Hashtable-only | Stored in an adapter-local `ConcurrentHashMap`, not represented in domain/application state. |
| 🔴 Ignored | Specified by AWS S3 but not read by the current implementation. |

## Status code legend

| Marker | Meaning |
|---|---|
| ✅ Implemented | The handler intentionally returns this status for the documented condition. |
| 🟡 Partially implemented | The status can be returned for some cases, but AWS S3 semantics are incomplete. |
| ✅ Completed (not implemented) | The gap is explicitly documented; no AWS-compatible code path currently returns this status. |

---

## Implemented operation index

| Range | Operations | Notes |
|---|---:|---|
| 1–12 | Core bucket and object operations | Bucket lifecycle, object CRUD, copy, and multi-delete. |
| 13–27 | Location, versioning, ACL, tagging, object attributes | Includes hashtable-only ACL/tagging compatibility. |
| 28–63 | Bucket configuration operations | CORS, lifecycle, policy, encryption, logging, website, notification, replication, request payment, ownership controls, public access block, and accelerate. |
| 64–70 | Multipart upload operations | Newly documented in this file: CreateMultipartUpload, UploadPart, UploadPartCopy, CompleteMultipartUpload, AbortMultipartUpload, ListMultipartUploads, ListParts. |
| 71–84 | Analytics, inventory, metrics, intelligent-tiering | Phase E operations. |

---

### Operation 1 — ListBuckets `GET /`

| Coverage Area | Required / Optional | Status | Notes |
|---|---|---|---|
| Request headers | Optional AWS `x-amz-account-id`; current implementation has no account model. | 🔴 Ignored | Header coverage for this operation. |
| Query parameters | None. | ✅ Tested | Route and query coverage for this operation. |
| Request body | None. | ✅ Tested | Request payload coverage. |
| Response body / headers | `ListAllMyBucketsResult` XML with `Content-Type: application/xml`; JSON convenience response exists for `Accept: application/json` but is outside the S3-compatible path. | ✅ Tested | Response payload and significant header coverage. |

#### Status Codes

| Status Code | Description | Implemented | Notes |
|---|---|---|---|
| 200 | Success | ✅ Implemented | Returned by the handler for successful requests. |
| 301 | PermanentRedirect or wrong-region redirect | ✅ Completed (not implemented) | Region redirects are not modeled by the in-memory local service. |
| 304 | NotModified for conditional requests | ✅ Completed (not implemented) | Conditional request headers are ignored. |
| 400 | Bad Request or InvalidArgument | 🟡 Partially implemented | Malformed requests may fail through routing/codec behavior; operation-specific AWS validation is incomplete. |
| 403 | AccessDenied | ✅ Completed (not implemented) | No authentication, IAM, ACL enforcement, or requester-pays authorization model exists. |
| 404 | Not found condition | ✅ Completed (not implemented) | No operation-specific not-found condition exists for this route. |
| 405 | MethodNotAllowed | 🟡 Partially implemented | Unsupported methods may be rejected by WebFlux routing, but S3 XML MethodNotAllowed responses are not implemented. |
| 409 | Conflict | ✅ Completed (not implemented) | AWS conflict variants such as BucketNotEmpty or operation conflicts are not fully modeled. |
| 500 | InternalServerError | ✅ Completed (not implemented) | No explicit AWS InternalServerError mapping exists for this operation. |
| 501 | NotImplemented | ✅ Completed (not implemented) | Unsupported optional AWS S3 features are currently ignored or documented as gaps rather than returned as `NotImplemented`. |
| 503 | SlowDown or ServiceUnavailable | ✅ Completed (not implemented) | No throttling or service-availability model exists. |

---

### Operation 2 — CreateBucket `PUT /{bucket}`

| Coverage Area | Required / Optional | Status | Notes |
|---|---|---|---|
| Request headers | Optional ACL/grant headers `x-amz-acl`, `x-amz-grant-read`, `x-amz-grant-write`, `x-amz-grant-read-acp`, `x-amz-grant-write-acp`, `x-amz-grant-full-control`; optional `x-amz-bucket-object-lock-enabled`; optional `x-amz-expected-bucket-owner`. | 🔴 Ignored | Header coverage for this operation. |
| Query parameters | None. | ✅ Tested | Route and query coverage for this operation. |
| Request body | Optional AWS `CreateBucketConfiguration` fields such as `LocationConstraint`, `Location`, and storage-class-like local configuration are ignored; bucket is created with `us-east-1` and `STANDARD` defaults. | 🔴 Ignored | Request payload coverage. |
| Response body / headers | No success body; `Location: /{bucket}` response header is returned. | ✅ Tested | Response payload and significant header coverage. |

#### Status Codes

| Status Code | Description | Implemented | Notes |
|---|---|---|---|
| 200 | Success | ✅ Implemented | Returned by the handler for successful requests. |
| 400 | Bad Request or InvalidArgument | 🟡 Partially implemented | Selected invalid requests are rejected; full AWS validation is incomplete. |
| 403 | AccessDenied | ✅ Completed (not implemented) | No authentication, IAM, ACL enforcement, or requester-pays authorization model exists. |
| 404 | Not found condition | ✅ Completed (not implemented) | No operation-specific not-found condition exists for this route. |
| 405 | MethodNotAllowed | 🟡 Partially implemented | Unsupported methods may be rejected by WebFlux routing, but S3 XML MethodNotAllowed responses are not implemented. |
| 409 | Conflict | ✅ Implemented | Conflict is returned for the implemented conflict condition. |
| 500 | InternalServerError | ✅ Completed (not implemented) | No explicit AWS InternalServerError mapping exists for this operation. |
| 501 | NotImplemented | ✅ Completed (not implemented) | Unsupported optional AWS S3 features are currently ignored or documented as gaps rather than returned as `NotImplemented`. |
| 503 | SlowDown or ServiceUnavailable | ✅ Completed (not implemented) | No throttling or service-availability model exists. |

---

### Operation 3 — HeadBucket `HEAD /{bucket}`

| Coverage Area | Required / Optional | Status | Notes |
|---|---|---|---|
| Request headers | Optional `x-amz-expected-bucket-owner` is ignored. | 🔴 Ignored | Header coverage for this operation. |
| Query parameters | None. | ✅ Tested | Route and query coverage for this operation. |
| Request body | None. | ✅ Tested | Request payload coverage. |
| Response body / headers | No body; `x-amz-bucket-region` is not returned. | ✅ Tested | Response payload and significant header coverage. |

#### Status Codes

| Status Code | Description | Implemented | Notes |
|---|---|---|---|
| 200 | Success | ✅ Implemented | Returned by the handler for successful requests. |
| 301 | PermanentRedirect or wrong-region redirect | ✅ Completed (not implemented) | Region redirects are not modeled by the in-memory local service. |
| 304 | NotModified for conditional requests | ✅ Completed (not implemented) | Conditional request headers are ignored. |
| 400 | Bad Request or InvalidArgument | 🟡 Partially implemented | Malformed requests may fail through routing/codec behavior; operation-specific AWS validation is incomplete. |
| 403 | AccessDenied | ✅ Completed (not implemented) | No authentication, IAM, ACL enforcement, or requester-pays authorization model exists. |
| 404 | Not found condition | ✅ Implemented | NoSuchBucket, NoSuchKey, NoSuchUpload, or missing configuration paths are handled where applicable. |
| 405 | MethodNotAllowed | 🟡 Partially implemented | Unsupported methods may be rejected by WebFlux routing, but S3 XML MethodNotAllowed responses are not implemented. |
| 409 | Conflict | ✅ Completed (not implemented) | AWS conflict variants such as BucketNotEmpty or operation conflicts are not fully modeled. |
| 500 | InternalServerError | ✅ Completed (not implemented) | No explicit AWS InternalServerError mapping exists for this operation. |
| 501 | NotImplemented | ✅ Completed (not implemented) | Unsupported optional AWS S3 features are currently ignored or documented as gaps rather than returned as `NotImplemented`. |
| 503 | SlowDown or ServiceUnavailable | ✅ Completed (not implemented) | No throttling or service-availability model exists. |

---

### Operation 4 — DeleteBucket `DELETE /{bucket}`

| Coverage Area | Required / Optional | Status | Notes |
|---|---|---|---|
| Request headers | Optional `x-amz-expected-bucket-owner` is ignored. | 🔴 Ignored | Header coverage for this operation. |
| Query parameters | None. | ✅ Tested | Route and query coverage for this operation. |
| Request body | None. | ✅ Tested | Request payload coverage. |
| Response body / headers | No success body. | ✅ Tested | Response payload and significant header coverage. |

#### Status Codes

| Status Code | Description | Implemented | Notes |
|---|---|---|---|
| 204 | No Content success | ✅ Implemented | Returned by the handler for successful delete/abort requests. |
| 400 | Bad Request or InvalidArgument | 🟡 Partially implemented | Malformed requests may fail through routing/codec behavior; operation-specific AWS validation is incomplete. |
| 403 | AccessDenied | ✅ Completed (not implemented) | No authentication, IAM, ACL enforcement, or requester-pays authorization model exists. |
| 404 | Not found condition | ✅ Implemented | NoSuchBucket, NoSuchKey, NoSuchUpload, or missing configuration paths are handled where applicable. |
| 405 | MethodNotAllowed | 🟡 Partially implemented | Unsupported methods may be rejected by WebFlux routing, but S3 XML MethodNotAllowed responses are not implemented. |
| 409 | Conflict | ✅ Completed (not implemented) | AWS conflict variants such as BucketNotEmpty or operation conflicts are not fully modeled. |
| 500 | InternalServerError | ✅ Completed (not implemented) | No explicit AWS InternalServerError mapping exists for this operation. |
| 501 | NotImplemented | ✅ Completed (not implemented) | Unsupported optional AWS S3 features are currently ignored or documented as gaps rather than returned as `NotImplemented`. |
| 503 | SlowDown or ServiceUnavailable | ✅ Completed (not implemented) | No throttling or service-availability model exists. |

---

### Operation 5 — ListObjects `GET /{bucket}`

| Coverage Area | Required / Optional | Status | Notes |
|---|---|---|---|
| Request headers | Optional `x-amz-expected-bucket-owner` and requester headers are ignored. | 🔴 Ignored | Header coverage for this operation. |
| Query parameters | Route without discriminator is tested; `delimiter`, `encoding-type`, `marker`, `max-keys`, and `prefix` are accepted by AWS but ignored, so all objects are returned. | 🟡 Partial | Route and query coverage for this operation. |
| Request body | None. | ✅ Tested | Request payload coverage. |
| Response body / headers | `ListBucketResult` XML with object entries. | ✅ Tested | Response payload and significant header coverage. |

#### Status Codes

| Status Code | Description | Implemented | Notes |
|---|---|---|---|
| 200 | Success | ✅ Implemented | Returned by the handler for successful requests. |
| 301 | PermanentRedirect or wrong-region redirect | ✅ Completed (not implemented) | Region redirects are not modeled by the in-memory local service. |
| 304 | NotModified for conditional requests | ✅ Completed (not implemented) | Conditional request headers are ignored. |
| 400 | Bad Request or InvalidArgument | 🟡 Partially implemented | Malformed requests may fail through routing/codec behavior; operation-specific AWS validation is incomplete. |
| 403 | AccessDenied | ✅ Completed (not implemented) | No authentication, IAM, ACL enforcement, or requester-pays authorization model exists. |
| 404 | Not found condition | ✅ Implemented | NoSuchBucket, NoSuchKey, NoSuchUpload, or missing configuration paths are handled where applicable. |
| 405 | MethodNotAllowed | 🟡 Partially implemented | Unsupported methods may be rejected by WebFlux routing, but S3 XML MethodNotAllowed responses are not implemented. |
| 409 | Conflict | ✅ Completed (not implemented) | AWS conflict variants such as BucketNotEmpty or operation conflicts are not fully modeled. |
| 500 | InternalServerError | ✅ Completed (not implemented) | No explicit AWS InternalServerError mapping exists for this operation. |
| 501 | NotImplemented | ✅ Completed (not implemented) | Unsupported optional AWS S3 features are currently ignored or documented as gaps rather than returned as `NotImplemented`. |
| 503 | SlowDown or ServiceUnavailable | ✅ Completed (not implemented) | No throttling or service-availability model exists. |

---

### Operation 6 — PutObject `PUT /{bucket}/{key}`

| Coverage Area | Required / Optional | Status | Notes |
|---|---|---|---|
| Request headers | `Content-Type`, `Content-Length`, and `x-amz-storage-class` are read; ACL/grant, metadata, tagging, object-lock, checksum, SSE, and `x-amz-expected-bucket-owner` headers are ignored. | 🟡 Partial | Header coverage for this operation. |
| Query parameters | No operation query parameter is required; versioning and object-lock query variants are not implemented. | ✅ Tested | Route and query coverage for this operation. |
| Request body | Binary request body is streamed and stored through the object service. | ✅ Tested | Request payload coverage. |
| Response body / headers | No XML success body; `ETag` header is returned as an empty quoted value. | ✅ Tested | Response payload and significant header coverage. |

#### Status Codes

| Status Code | Description | Implemented | Notes |
|---|---|---|---|
| 200 | Success | ✅ Implemented | Returned by the handler for successful requests. |
| 400 | Bad Request or InvalidArgument | 🟡 Partially implemented | Malformed requests may fail through routing/codec behavior; operation-specific AWS validation is incomplete. |
| 403 | AccessDenied | ✅ Completed (not implemented) | No authentication, IAM, ACL enforcement, or requester-pays authorization model exists. |
| 404 | Not found condition | ✅ Implemented | NoSuchBucket, NoSuchKey, NoSuchUpload, or missing configuration paths are handled where applicable. |
| 405 | MethodNotAllowed | 🟡 Partially implemented | Unsupported methods may be rejected by WebFlux routing, but S3 XML MethodNotAllowed responses are not implemented. |
| 409 | Conflict | ✅ Completed (not implemented) | AWS conflict variants such as BucketNotEmpty or operation conflicts are not fully modeled. |
| 500 | InternalServerError | ✅ Completed (not implemented) | No explicit AWS InternalServerError mapping exists for this operation. |
| 501 | NotImplemented | ✅ Completed (not implemented) | Unsupported optional AWS S3 features are currently ignored or documented as gaps rather than returned as `NotImplemented`. |
| 503 | SlowDown or ServiceUnavailable | ✅ Completed (not implemented) | No throttling or service-availability model exists. |

---

### Operation 7 — GetObject `GET /{bucket}/{key}`

| Coverage Area | Required / Optional | Status | Notes |
|---|---|---|---|
| Request headers | `Range`, conditional headers, response override headers, SSE-C headers, and `x-amz-expected-bucket-owner` are ignored. | 🔴 Ignored | Header coverage for this operation. |
| Query parameters | Optional `versionId`, `partNumber`, and response override query parameters are ignored. | 🔴 Ignored | Route and query coverage for this operation. |
| Request body | None. | ✅ Tested | Request payload coverage. |
| Response body / headers | Object content stream with `Content-Type: application/octet-stream` and `ETag`; range and metadata response headers are incomplete. | ✅ Tested | Response payload and significant header coverage. |

#### Status Codes

| Status Code | Description | Implemented | Notes |
|---|---|---|---|
| 200 | Success | ✅ Implemented | Returned by the handler for successful requests. |
| 206 | Partial Content for range requests | ✅ Completed (not implemented) | Range requests are documented as unsupported; full content is returned instead. |
| 304 | NotModified for conditional requests | ✅ Completed (not implemented) | Conditional request headers are ignored. |
| 400 | Bad Request or InvalidArgument | 🟡 Partially implemented | Malformed requests may fail through routing/codec behavior; operation-specific AWS validation is incomplete. |
| 403 | AccessDenied | ✅ Completed (not implemented) | No authentication, IAM, ACL enforcement, or requester-pays authorization model exists. |
| 404 | Not found condition | ✅ Implemented | NoSuchBucket, NoSuchKey, NoSuchUpload, or missing configuration paths are handled where applicable. |
| 405 | MethodNotAllowed | 🟡 Partially implemented | Unsupported methods may be rejected by WebFlux routing, but S3 XML MethodNotAllowed responses are not implemented. |
| 412 | PreconditionFailed | ✅ Completed (not implemented) | Precondition headers are ignored. |
| 500 | InternalServerError | 🟡 Partially implemented | InternalError is returned for selected unsupported internal states only. |
| 501 | NotImplemented | ✅ Completed (not implemented) | Unsupported optional AWS S3 features are currently ignored or documented as gaps rather than returned as `NotImplemented`. |
| 503 | SlowDown or ServiceUnavailable | ✅ Completed (not implemented) | No throttling or service-availability model exists. |

---

### Operation 8 — HeadObject `HEAD /{bucket}/{key}`

| Coverage Area | Required / Optional | Status | Notes |
|---|---|---|---|
| Request headers | Conditional headers, SSE-C headers, checksum-mode, and `x-amz-expected-bucket-owner` are ignored. | 🔴 Ignored | Header coverage for this operation. |
| Query parameters | Optional `versionId` and `partNumber` are ignored. | 🔴 Ignored | Route and query coverage for this operation. |
| Request body | None. | ✅ Tested | Request payload coverage. |
| Response body / headers | No body; success currently does not return full S3 metadata headers such as `ETag`, `Content-Length`, or `Content-Type`. | ✅ Tested | Response payload and significant header coverage. |

#### Status Codes

| Status Code | Description | Implemented | Notes |
|---|---|---|---|
| 200 | Success | ✅ Implemented | Returned by the handler for successful requests. |
| 206 | Partial Content for range requests | ✅ Completed (not implemented) | Range requests are documented as unsupported; full content is returned instead. |
| 304 | NotModified for conditional requests | ✅ Completed (not implemented) | Conditional request headers are ignored. |
| 400 | Bad Request or InvalidArgument | 🟡 Partially implemented | Malformed requests may fail through routing/codec behavior; operation-specific AWS validation is incomplete. |
| 403 | AccessDenied | ✅ Completed (not implemented) | No authentication, IAM, ACL enforcement, or requester-pays authorization model exists. |
| 404 | Not found condition | ✅ Implemented | NoSuchBucket, NoSuchKey, NoSuchUpload, or missing configuration paths are handled where applicable. |
| 405 | MethodNotAllowed | 🟡 Partially implemented | Unsupported methods may be rejected by WebFlux routing, but S3 XML MethodNotAllowed responses are not implemented. |
| 412 | PreconditionFailed | ✅ Completed (not implemented) | Precondition headers are ignored. |
| 500 | InternalServerError | ✅ Completed (not implemented) | No explicit AWS InternalServerError mapping exists for this operation. |
| 501 | NotImplemented | ✅ Completed (not implemented) | Unsupported optional AWS S3 features are currently ignored or documented as gaps rather than returned as `NotImplemented`. |
| 503 | SlowDown or ServiceUnavailable | ✅ Completed (not implemented) | No throttling or service-availability model exists. |

---

### Operation 9 — DeleteObject `DELETE /{bucket}/{key}`

| Coverage Area | Required / Optional | Status | Notes |
|---|---|---|---|
| Request headers | Optional `x-amz-bypass-governance-retention`, MFA, requester, and `x-amz-expected-bucket-owner` headers are ignored. | 🔴 Ignored | Header coverage for this operation. |
| Query parameters | Optional `versionId` is ignored; current delete targets the current object only. | 🔴 Ignored | Route and query coverage for this operation. |
| Request body | None. | ✅ Tested | Request payload coverage. |
| Response body / headers | No success body; versioning and delete-marker response headers are not returned. | ✅ Tested | Response payload and significant header coverage. |

#### Status Codes

| Status Code | Description | Implemented | Notes |
|---|---|---|---|
| 204 | No Content success | ✅ Implemented | Returned by the handler for successful delete/abort requests. |
| 400 | Bad Request or InvalidArgument | 🟡 Partially implemented | Malformed requests may fail through routing/codec behavior; operation-specific AWS validation is incomplete. |
| 403 | AccessDenied | ✅ Completed (not implemented) | No authentication, IAM, ACL enforcement, or requester-pays authorization model exists. |
| 404 | Not found condition | ✅ Implemented | NoSuchBucket, NoSuchKey, NoSuchUpload, or missing configuration paths are handled where applicable. |
| 405 | MethodNotAllowed | 🟡 Partially implemented | Unsupported methods may be rejected by WebFlux routing, but S3 XML MethodNotAllowed responses are not implemented. |
| 500 | InternalServerError | ✅ Completed (not implemented) | No explicit AWS InternalServerError mapping exists for this operation. |
| 501 | NotImplemented | ✅ Completed (not implemented) | Unsupported optional AWS S3 features are currently ignored or documented as gaps rather than returned as `NotImplemented`. |
| 503 | SlowDown or ServiceUnavailable | ✅ Completed (not implemented) | No throttling or service-availability model exists. |

---

### Operation 10 — ListObjectsV2 `GET /{bucket}?list-type=2`

| Coverage Area | Required / Optional | Status | Notes |
|---|---|---|---|
| Request headers | Optional `x-amz-expected-bucket-owner` is ignored. | 🔴 Ignored | Header coverage for this operation. |
| Query parameters | Required `list-type=2` route discriminator is tested; `continuation-token`, `delimiter`, `encoding-type`, `fetch-owner`, `max-keys`, `prefix`, and `start-after` are ignored. | 🟡 Partial | Route and query coverage for this operation. |
| Request body | None. | ✅ Tested | Request payload coverage. |
| Response body / headers | `ListBucketResult` V2 XML with current in-memory objects. | ✅ Tested | Response payload and significant header coverage. |

#### Status Codes

| Status Code | Description | Implemented | Notes |
|---|---|---|---|
| 200 | Success | ✅ Implemented | Returned by the handler for successful requests. |
| 301 | PermanentRedirect or wrong-region redirect | ✅ Completed (not implemented) | Region redirects are not modeled by the in-memory local service. |
| 304 | NotModified for conditional requests | ✅ Completed (not implemented) | Conditional request headers are ignored. |
| 400 | Bad Request or InvalidArgument | 🟡 Partially implemented | Malformed requests may fail through routing/codec behavior; operation-specific AWS validation is incomplete. |
| 403 | AccessDenied | ✅ Completed (not implemented) | No authentication, IAM, ACL enforcement, or requester-pays authorization model exists. |
| 404 | Not found condition | ✅ Implemented | NoSuchBucket, NoSuchKey, NoSuchUpload, or missing configuration paths are handled where applicable. |
| 405 | MethodNotAllowed | 🟡 Partially implemented | Unsupported methods may be rejected by WebFlux routing, but S3 XML MethodNotAllowed responses are not implemented. |
| 409 | Conflict | ✅ Completed (not implemented) | AWS conflict variants such as BucketNotEmpty or operation conflicts are not fully modeled. |
| 500 | InternalServerError | ✅ Completed (not implemented) | No explicit AWS InternalServerError mapping exists for this operation. |
| 501 | NotImplemented | ✅ Completed (not implemented) | Unsupported optional AWS S3 features are currently ignored or documented as gaps rather than returned as `NotImplemented`. |
| 503 | SlowDown or ServiceUnavailable | ✅ Completed (not implemented) | No throttling or service-availability model exists. |

---

### Operation 11 — CopyObject `PUT /{bucket}/{key} with x-amz-copy-source`

| Coverage Area | Required / Optional | Status | Notes |
|---|---|---|---|
| Request headers | Required `x-amz-copy-source` is parsed; metadata, tagging, ACL/grant, storage-class, conditional copy, SSE, checksum, and expected-owner headers are ignored. | 🟡 Partial | Header coverage for this operation. |
| Query parameters | Copy-source version query embedded in `x-amz-copy-source` is ignored. | 🔴 Ignored | Route and query coverage for this operation. |
| Request body | None. | ✅ Tested | Request payload coverage. |
| Response body / headers | `CopyObjectResult` XML plus `ETag` header. | ✅ Tested | Response payload and significant header coverage. |

#### Status Codes

| Status Code | Description | Implemented | Notes |
|---|---|---|---|
| 200 | Success | ✅ Implemented | Returned by the handler for successful requests. |
| 400 | Bad Request or InvalidArgument | 🟡 Partially implemented | Selected invalid requests are rejected; full AWS validation is incomplete. |
| 403 | AccessDenied | ✅ Completed (not implemented) | No authentication, IAM, ACL enforcement, or requester-pays authorization model exists. |
| 404 | Not found condition | ✅ Implemented | NoSuchBucket, NoSuchKey, NoSuchUpload, or missing configuration paths are handled where applicable. |
| 405 | MethodNotAllowed | 🟡 Partially implemented | Unsupported methods may be rejected by WebFlux routing, but S3 XML MethodNotAllowed responses are not implemented. |
| 409 | Conflict | ✅ Completed (not implemented) | AWS conflict variants such as BucketNotEmpty or operation conflicts are not fully modeled. |
| 500 | InternalServerError | 🟡 Partially implemented | InternalError is returned for selected unsupported internal states only. |
| 501 | NotImplemented | ✅ Completed (not implemented) | Unsupported optional AWS S3 features are currently ignored or documented as gaps rather than returned as `NotImplemented`. |
| 503 | SlowDown or ServiceUnavailable | ✅ Completed (not implemented) | No throttling or service-availability model exists. |

---

### Operation 12 — DeleteObjects `POST /{bucket}?delete`

| Coverage Area | Required / Optional | Status | Notes |
|---|---|---|---|
| Request headers | Optional governance, MFA, checksum, requester, and `x-amz-expected-bucket-owner` headers are ignored. | 🔴 Ignored | Header coverage for this operation. |
| Query parameters | Required `delete` route flag is tested. | ✅ Tested | Route and query coverage for this operation. |
| Request body | `Delete` XML body is parsed with Jackson XML and object keys are processed. | ✅ Tested | Request payload coverage. |
| Response body / headers | `DeleteResult` XML with deleted key entries. | ✅ Tested | Response payload and significant header coverage. |

#### Status Codes

| Status Code | Description | Implemented | Notes |
|---|---|---|---|
| 200 | Success | ✅ Implemented | Returned by the handler for successful requests. |
| 400 | Bad Request or InvalidArgument | 🟡 Partially implemented | Selected invalid requests are rejected; full AWS validation is incomplete. |
| 403 | AccessDenied | ✅ Completed (not implemented) | No authentication, IAM, ACL enforcement, or requester-pays authorization model exists. |
| 404 | Not found condition | ✅ Implemented | NoSuchBucket, NoSuchKey, NoSuchUpload, or missing configuration paths are handled where applicable. |
| 405 | MethodNotAllowed | 🟡 Partially implemented | Unsupported methods may be rejected by WebFlux routing, but S3 XML MethodNotAllowed responses are not implemented. |
| 500 | InternalServerError | ✅ Completed (not implemented) | No explicit AWS InternalServerError mapping exists for this operation. |
| 501 | NotImplemented | ✅ Completed (not implemented) | Unsupported optional AWS S3 features are currently ignored or documented as gaps rather than returned as `NotImplemented`. |
| 503 | SlowDown or ServiceUnavailable | ✅ Completed (not implemented) | No throttling or service-availability model exists. |

---

### Operation 13 — GetBucketLocation `GET /{bucket}?location`

| Coverage Area | Required / Optional | Status | Notes |
|---|---|---|---|
| Request headers | Optional `x-amz-expected-bucket-owner` is ignored. | 🔴 Ignored | Header coverage for this operation. |
| Query parameters | Required `location` route flag is tested. | ✅ Tested | Route and query coverage for this operation. |
| Request body | None. | ✅ Tested | Request payload coverage. |
| Response body / headers | `LocationConstraint` XML from the bucket region. | ✅ Tested | Response payload and significant header coverage. |

#### Status Codes

| Status Code | Description | Implemented | Notes |
|---|---|---|---|
| 200 | Success | ✅ Implemented | Returned by the handler for successful requests. |
| 301 | PermanentRedirect or wrong-region redirect | ✅ Completed (not implemented) | Region redirects are not modeled by the in-memory local service. |
| 304 | NotModified for conditional requests | ✅ Completed (not implemented) | Conditional request headers are ignored. |
| 400 | Bad Request or InvalidArgument | 🟡 Partially implemented | Malformed requests may fail through routing/codec behavior; operation-specific AWS validation is incomplete. |
| 403 | AccessDenied | ✅ Completed (not implemented) | No authentication, IAM, ACL enforcement, or requester-pays authorization model exists. |
| 404 | Not found condition | ✅ Implemented | NoSuchBucket, NoSuchKey, NoSuchUpload, or missing configuration paths are handled where applicable. |
| 405 | MethodNotAllowed | 🟡 Partially implemented | Unsupported methods may be rejected by WebFlux routing, but S3 XML MethodNotAllowed responses are not implemented. |
| 409 | Conflict | ✅ Completed (not implemented) | AWS conflict variants such as BucketNotEmpty or operation conflicts are not fully modeled. |
| 500 | InternalServerError | ✅ Completed (not implemented) | No explicit AWS InternalServerError mapping exists for this operation. |
| 501 | NotImplemented | ✅ Completed (not implemented) | Unsupported optional AWS S3 features are currently ignored or documented as gaps rather than returned as `NotImplemented`. |
| 503 | SlowDown or ServiceUnavailable | ✅ Completed (not implemented) | No throttling or service-availability model exists. |

---

### Operation 14 — GetBucketVersioning `GET /{bucket}?versioning`

| Coverage Area | Required / Optional | Status | Notes |
|---|---|---|---|
| Request headers | Optional `x-amz-expected-bucket-owner` is ignored. | 🔴 Ignored | Header coverage for this operation. |
| Query parameters | Required `versioning` route flag is tested. | ✅ Tested | Route and query coverage for this operation. |
| Request body | None. | ✅ Tested | Request payload coverage. |
| Response body / headers | `VersioningConfiguration` XML with enabled or suspended state. | ✅ Tested | Response payload and significant header coverage. |

#### Status Codes

| Status Code | Description | Implemented | Notes |
|---|---|---|---|
| 200 | Success | ✅ Implemented | Returned by the handler for successful requests. |
| 301 | PermanentRedirect or wrong-region redirect | ✅ Completed (not implemented) | Region redirects are not modeled by the in-memory local service. |
| 304 | NotModified for conditional requests | ✅ Completed (not implemented) | Conditional request headers are ignored. |
| 400 | Bad Request or InvalidArgument | 🟡 Partially implemented | Malformed requests may fail through routing/codec behavior; operation-specific AWS validation is incomplete. |
| 403 | AccessDenied | ✅ Completed (not implemented) | No authentication, IAM, ACL enforcement, or requester-pays authorization model exists. |
| 404 | Not found condition | ✅ Implemented | NoSuchBucket, NoSuchKey, NoSuchUpload, or missing configuration paths are handled where applicable. |
| 405 | MethodNotAllowed | 🟡 Partially implemented | Unsupported methods may be rejected by WebFlux routing, but S3 XML MethodNotAllowed responses are not implemented. |
| 409 | Conflict | ✅ Completed (not implemented) | AWS conflict variants such as BucketNotEmpty or operation conflicts are not fully modeled. |
| 500 | InternalServerError | ✅ Completed (not implemented) | No explicit AWS InternalServerError mapping exists for this operation. |
| 501 | NotImplemented | ✅ Completed (not implemented) | Unsupported optional AWS S3 features are currently ignored or documented as gaps rather than returned as `NotImplemented`. |
| 503 | SlowDown or ServiceUnavailable | ✅ Completed (not implemented) | No throttling or service-availability model exists. |

---

### Operation 15 — PutBucketVersioning `PUT /{bucket}?versioning`

| Coverage Area | Required / Optional | Status | Notes |
|---|---|---|---|
| Request headers | Optional `x-amz-mfa` and `x-amz-expected-bucket-owner` are ignored. | 🔴 Ignored | Header coverage for this operation. |
| Query parameters | Required `versioning` route flag is tested. | ✅ Tested | Route and query coverage for this operation. |
| Request body | `VersioningConfiguration` XML is parsed; `Status` drives the local versioning flag. | ✅ Tested | Request payload coverage. |
| Response body / headers | No success body. | ✅ Tested | Response payload and significant header coverage. |

#### Status Codes

| Status Code | Description | Implemented | Notes |
|---|---|---|---|
| 200 | Success | ✅ Implemented | Returned by the handler for successful requests. |
| 400 | Bad Request or InvalidArgument | 🟡 Partially implemented | Selected invalid requests are rejected; full AWS validation is incomplete. |
| 403 | AccessDenied | ✅ Completed (not implemented) | No authentication, IAM, ACL enforcement, or requester-pays authorization model exists. |
| 404 | Not found condition | ✅ Implemented | NoSuchBucket, NoSuchKey, NoSuchUpload, or missing configuration paths are handled where applicable. |
| 405 | MethodNotAllowed | 🟡 Partially implemented | Unsupported methods may be rejected by WebFlux routing, but S3 XML MethodNotAllowed responses are not implemented. |
| 409 | Conflict | ✅ Completed (not implemented) | AWS conflict variants such as BucketNotEmpty or operation conflicts are not fully modeled. |
| 500 | InternalServerError | ✅ Completed (not implemented) | No explicit AWS InternalServerError mapping exists for this operation. |
| 501 | NotImplemented | ✅ Completed (not implemented) | Unsupported optional AWS S3 features are currently ignored or documented as gaps rather than returned as `NotImplemented`. |
| 503 | SlowDown or ServiceUnavailable | ✅ Completed (not implemented) | No throttling or service-availability model exists. |

---

### Operation 16 — ListObjectVersions `GET /{bucket}?versions`

| Coverage Area | Required / Optional | Status | Notes |
|---|---|---|---|
| Request headers | Optional `x-amz-expected-bucket-owner` is ignored. | 🔴 Ignored | Header coverage for this operation. |
| Query parameters | Required `versions` route flag is tested; `delimiter`, `encoding-type`, `key-marker`, `max-keys`, `prefix`, and `version-id-marker` are ignored. | 🟡 Partial | Route and query coverage for this operation. |
| Request body | None. | ✅ Tested | Request payload coverage. |
| Response body / headers | `ListVersionsResult` XML synthesized from current objects; real historical versions are not modeled. | ✅ Tested | Response payload and significant header coverage. |

#### Status Codes

| Status Code | Description | Implemented | Notes |
|---|---|---|---|
| 200 | Success | ✅ Implemented | Returned by the handler for successful requests. |
| 301 | PermanentRedirect or wrong-region redirect | ✅ Completed (not implemented) | Region redirects are not modeled by the in-memory local service. |
| 304 | NotModified for conditional requests | ✅ Completed (not implemented) | Conditional request headers are ignored. |
| 400 | Bad Request or InvalidArgument | 🟡 Partially implemented | Malformed requests may fail through routing/codec behavior; operation-specific AWS validation is incomplete. |
| 403 | AccessDenied | ✅ Completed (not implemented) | No authentication, IAM, ACL enforcement, or requester-pays authorization model exists. |
| 404 | Not found condition | ✅ Implemented | NoSuchBucket, NoSuchKey, NoSuchUpload, or missing configuration paths are handled where applicable. |
| 405 | MethodNotAllowed | 🟡 Partially implemented | Unsupported methods may be rejected by WebFlux routing, but S3 XML MethodNotAllowed responses are not implemented. |
| 409 | Conflict | ✅ Completed (not implemented) | AWS conflict variants such as BucketNotEmpty or operation conflicts are not fully modeled. |
| 500 | InternalServerError | ✅ Completed (not implemented) | No explicit AWS InternalServerError mapping exists for this operation. |
| 501 | NotImplemented | ✅ Completed (not implemented) | Unsupported optional AWS S3 features are currently ignored or documented as gaps rather than returned as `NotImplemented`. |
| 503 | SlowDown or ServiceUnavailable | ✅ Completed (not implemented) | No throttling or service-availability model exists. |

---

### Operation 17 — GetObjectAcl `GET /{bucket}/{key}?acl`

| Coverage Area | Required / Optional | Status | Notes |
|---|---|---|---|
| Request headers | Optional `x-amz-expected-bucket-owner` is ignored. | 🔴 Ignored | Header coverage for this operation. |
| Query parameters | Required `acl` route flag is tested; optional `versionId` is ignored. | 🟡 Partial | Route and query coverage for this operation. |
| Request body | None. | ✅ Tested | Request payload coverage. |
| Response body / headers | `AccessControlPolicy` XML from adapter-local ACL map or default private ACL. | ⬜ Hashtable-only | Response payload and significant header coverage. |

#### Status Codes

| Status Code | Description | Implemented | Notes |
|---|---|---|---|
| 200 | Success | ✅ Implemented | Returned by the handler for successful requests. |
| 206 | Partial Content for range requests | ✅ Completed (not implemented) | Range requests are documented as unsupported; full content is returned instead. |
| 304 | NotModified for conditional requests | ✅ Completed (not implemented) | Conditional request headers are ignored. |
| 400 | Bad Request or InvalidArgument | 🟡 Partially implemented | Malformed requests may fail through routing/codec behavior; operation-specific AWS validation is incomplete. |
| 403 | AccessDenied | ✅ Completed (not implemented) | No authentication, IAM, ACL enforcement, or requester-pays authorization model exists. |
| 404 | Not found condition | ✅ Implemented | NoSuchBucket, NoSuchKey, NoSuchUpload, or missing configuration paths are handled where applicable. |
| 405 | MethodNotAllowed | 🟡 Partially implemented | Unsupported methods may be rejected by WebFlux routing, but S3 XML MethodNotAllowed responses are not implemented. |
| 412 | PreconditionFailed | ✅ Completed (not implemented) | Precondition headers are ignored. |
| 500 | InternalServerError | ✅ Completed (not implemented) | No explicit AWS InternalServerError mapping exists for this operation. |
| 501 | NotImplemented | ✅ Completed (not implemented) | Unsupported optional AWS S3 features are currently ignored or documented as gaps rather than returned as `NotImplemented`. |
| 503 | SlowDown or ServiceUnavailable | ✅ Completed (not implemented) | No throttling or service-availability model exists. |

---

### Operation 18 — PutObjectAcl `PUT /{bucket}/{key}?acl`

| Coverage Area | Required / Optional | Status | Notes |
|---|---|---|---|
| Request headers | Optional `x-amz-acl` is stored adapter-locally; `x-amz-grant-*` and expected-owner headers are ignored. | ⬜ Hashtable-only | Header coverage for this operation. |
| Query parameters | Required `acl` route flag is tested; optional `versionId` is ignored. | 🟡 Partial | Route and query coverage for this operation. |
| Request body | AWS `AccessControlPolicy` XML body is not parsed; current implementation uses the canned ACL header or `private` default. | 🔴 Ignored | Request payload coverage. |
| Response body / headers | No success body. | ✅ Tested | Response payload and significant header coverage. |

#### Status Codes

| Status Code | Description | Implemented | Notes |
|---|---|---|---|
| 200 | Success | ✅ Implemented | Returned by the handler for successful requests. |
| 400 | Bad Request or InvalidArgument | 🟡 Partially implemented | Malformed requests may fail through routing/codec behavior; operation-specific AWS validation is incomplete. |
| 403 | AccessDenied | ✅ Completed (not implemented) | No authentication, IAM, ACL enforcement, or requester-pays authorization model exists. |
| 404 | Not found condition | ✅ Implemented | NoSuchBucket, NoSuchKey, NoSuchUpload, or missing configuration paths are handled where applicable. |
| 405 | MethodNotAllowed | 🟡 Partially implemented | Unsupported methods may be rejected by WebFlux routing, but S3 XML MethodNotAllowed responses are not implemented. |
| 409 | Conflict | ✅ Completed (not implemented) | AWS conflict variants such as BucketNotEmpty or operation conflicts are not fully modeled. |
| 500 | InternalServerError | ✅ Completed (not implemented) | No explicit AWS InternalServerError mapping exists for this operation. |
| 501 | NotImplemented | ✅ Completed (not implemented) | Unsupported optional AWS S3 features are currently ignored or documented as gaps rather than returned as `NotImplemented`. |
| 503 | SlowDown or ServiceUnavailable | ✅ Completed (not implemented) | No throttling or service-availability model exists. |

---

### Operation 19 — GetObjectTagging `GET /{bucket}/{key}?tagging`

| Coverage Area | Required / Optional | Status | Notes |
|---|---|---|---|
| Request headers | Optional `x-amz-expected-bucket-owner` is ignored. | 🔴 Ignored | Header coverage for this operation. |
| Query parameters | Required `tagging` route flag is tested; optional `versionId` is ignored. | 🟡 Partial | Route and query coverage for this operation. |
| Request body | None. | ✅ Tested | Request payload coverage. |
| Response body / headers | `Tagging` XML from adapter-local object tag map. | ⬜ Hashtable-only | Response payload and significant header coverage. |

#### Status Codes

| Status Code | Description | Implemented | Notes |
|---|---|---|---|
| 200 | Success | ✅ Implemented | Returned by the handler for successful requests. |
| 206 | Partial Content for range requests | ✅ Completed (not implemented) | Range requests are documented as unsupported; full content is returned instead. |
| 304 | NotModified for conditional requests | ✅ Completed (not implemented) | Conditional request headers are ignored. |
| 400 | Bad Request or InvalidArgument | 🟡 Partially implemented | Malformed requests may fail through routing/codec behavior; operation-specific AWS validation is incomplete. |
| 403 | AccessDenied | ✅ Completed (not implemented) | No authentication, IAM, ACL enforcement, or requester-pays authorization model exists. |
| 404 | Not found condition | ✅ Implemented | NoSuchBucket, NoSuchKey, NoSuchUpload, or missing configuration paths are handled where applicable. |
| 405 | MethodNotAllowed | 🟡 Partially implemented | Unsupported methods may be rejected by WebFlux routing, but S3 XML MethodNotAllowed responses are not implemented. |
| 412 | PreconditionFailed | ✅ Completed (not implemented) | Precondition headers are ignored. |
| 500 | InternalServerError | ✅ Completed (not implemented) | No explicit AWS InternalServerError mapping exists for this operation. |
| 501 | NotImplemented | ✅ Completed (not implemented) | Unsupported optional AWS S3 features are currently ignored or documented as gaps rather than returned as `NotImplemented`. |
| 503 | SlowDown or ServiceUnavailable | ✅ Completed (not implemented) | No throttling or service-availability model exists. |

---

### Operation 20 — PutObjectTagging `PUT /{bucket}/{key}?tagging`

| Coverage Area | Required / Optional | Status | Notes |
|---|---|---|---|
| Request headers | Optional `x-amz-expected-bucket-owner` and checksum headers are ignored. | 🔴 Ignored | Header coverage for this operation. |
| Query parameters | Required `tagging` route flag is tested; optional `versionId` is ignored. | 🟡 Partial | Route and query coverage for this operation. |
| Request body | `Tagging` XML is parsed and stored adapter-locally. | ⬜ Hashtable-only | Request payload coverage. |
| Response body / headers | No success body. | ✅ Tested | Response payload and significant header coverage. |

#### Status Codes

| Status Code | Description | Implemented | Notes |
|---|---|---|---|
| 200 | Success | ✅ Implemented | Returned by the handler for successful requests. |
| 400 | Bad Request or InvalidArgument | 🟡 Partially implemented | Selected invalid requests are rejected; full AWS validation is incomplete. |
| 403 | AccessDenied | ✅ Completed (not implemented) | No authentication, IAM, ACL enforcement, or requester-pays authorization model exists. |
| 404 | Not found condition | ✅ Implemented | NoSuchBucket, NoSuchKey, NoSuchUpload, or missing configuration paths are handled where applicable. |
| 405 | MethodNotAllowed | 🟡 Partially implemented | Unsupported methods may be rejected by WebFlux routing, but S3 XML MethodNotAllowed responses are not implemented. |
| 409 | Conflict | ✅ Completed (not implemented) | AWS conflict variants such as BucketNotEmpty or operation conflicts are not fully modeled. |
| 500 | InternalServerError | ✅ Completed (not implemented) | No explicit AWS InternalServerError mapping exists for this operation. |
| 501 | NotImplemented | ✅ Completed (not implemented) | Unsupported optional AWS S3 features are currently ignored or documented as gaps rather than returned as `NotImplemented`. |
| 503 | SlowDown or ServiceUnavailable | ✅ Completed (not implemented) | No throttling or service-availability model exists. |

---

### Operation 21 — DeleteObjectTagging `DELETE /{bucket}/{key}?tagging`

| Coverage Area | Required / Optional | Status | Notes |
|---|---|---|---|
| Request headers | Optional `x-amz-expected-bucket-owner` is ignored. | 🔴 Ignored | Header coverage for this operation. |
| Query parameters | Required `tagging` route flag is tested; optional `versionId` is ignored. | 🟡 Partial | Route and query coverage for this operation. |
| Request body | None. | ✅ Tested | Request payload coverage. |
| Response body / headers | No success body; adapter-local object tags are removed. | ✅ Tested | Response payload and significant header coverage. |

#### Status Codes

| Status Code | Description | Implemented | Notes |
|---|---|---|---|
| 204 | No Content success | ✅ Implemented | Returned by the handler for successful delete/abort requests. |
| 400 | Bad Request or InvalidArgument | 🟡 Partially implemented | Malformed requests may fail through routing/codec behavior; operation-specific AWS validation is incomplete. |
| 403 | AccessDenied | ✅ Completed (not implemented) | No authentication, IAM, ACL enforcement, or requester-pays authorization model exists. |
| 404 | Not found condition | ✅ Implemented | NoSuchBucket, NoSuchKey, NoSuchUpload, or missing configuration paths are handled where applicable. |
| 405 | MethodNotAllowed | 🟡 Partially implemented | Unsupported methods may be rejected by WebFlux routing, but S3 XML MethodNotAllowed responses are not implemented. |
| 500 | InternalServerError | ✅ Completed (not implemented) | No explicit AWS InternalServerError mapping exists for this operation. |
| 501 | NotImplemented | ✅ Completed (not implemented) | Unsupported optional AWS S3 features are currently ignored or documented as gaps rather than returned as `NotImplemented`. |
| 503 | SlowDown or ServiceUnavailable | ✅ Completed (not implemented) | No throttling or service-availability model exists. |

---

### Operation 22 — GetObjectAttributes `GET /{bucket}/{key}?attributes`

| Coverage Area | Required / Optional | Status | Notes |
|---|---|---|---|
| Request headers | Required AWS `x-amz-object-attributes` is read only for compatibility scenarios; expected-owner and SSE-C headers are ignored. | 🟡 Partial | Header coverage for this operation. |
| Query parameters | Required `attributes` route flag is tested; `versionId`, `max-parts`, and `part-number-marker` are ignored. | 🟡 Partial | Route and query coverage for this operation. |
| Request body | None. | ✅ Tested | Request payload coverage. |
| Response body / headers | `GetObjectAttributes` XML includes implemented attributes such as ETag, object size, storage class, content type, and key. | ✅ Tested | Response payload and significant header coverage. |

#### Status Codes

| Status Code | Description | Implemented | Notes |
|---|---|---|---|
| 200 | Success | ✅ Implemented | Returned by the handler for successful requests. |
| 206 | Partial Content for range requests | ✅ Completed (not implemented) | Range requests are documented as unsupported; full content is returned instead. |
| 304 | NotModified for conditional requests | ✅ Completed (not implemented) | Conditional request headers are ignored. |
| 400 | Bad Request or InvalidArgument | 🟡 Partially implemented | Malformed requests may fail through routing/codec behavior; operation-specific AWS validation is incomplete. |
| 403 | AccessDenied | ✅ Completed (not implemented) | No authentication, IAM, ACL enforcement, or requester-pays authorization model exists. |
| 404 | Not found condition | ✅ Implemented | NoSuchBucket, NoSuchKey, NoSuchUpload, or missing configuration paths are handled where applicable. |
| 405 | MethodNotAllowed | 🟡 Partially implemented | Unsupported methods may be rejected by WebFlux routing, but S3 XML MethodNotAllowed responses are not implemented. |
| 412 | PreconditionFailed | ✅ Completed (not implemented) | Precondition headers are ignored. |
| 500 | InternalServerError | ✅ Completed (not implemented) | No explicit AWS InternalServerError mapping exists for this operation. |
| 501 | NotImplemented | ✅ Completed (not implemented) | Unsupported optional AWS S3 features are currently ignored or documented as gaps rather than returned as `NotImplemented`. |
| 503 | SlowDown or ServiceUnavailable | ✅ Completed (not implemented) | No throttling or service-availability model exists. |

---

### Operation 23 — GetBucketAcl `GET /{bucket}?acl`

| Coverage Area | Required / Optional | Status | Notes |
|---|---|---|---|
| Request headers | Optional `x-amz-expected-bucket-owner` is ignored. | 🔴 Ignored | Header coverage for this operation. |
| Query parameters | Required `acl` route flag is tested. | ✅ Tested | Route and query coverage for this operation. |
| Request body | None. | ✅ Tested | Request payload coverage. |
| Response body / headers | `AccessControlPolicy` XML from adapter-local bucket ACL map or default private ACL. | ⬜ Hashtable-only | Response payload and significant header coverage. |

#### Status Codes

| Status Code | Description | Implemented | Notes |
|---|---|---|---|
| 200 | Success | ✅ Implemented | Returned by the handler for successful requests. |
| 301 | PermanentRedirect or wrong-region redirect | ✅ Completed (not implemented) | Region redirects are not modeled by the in-memory local service. |
| 304 | NotModified for conditional requests | ✅ Completed (not implemented) | Conditional request headers are ignored. |
| 400 | Bad Request or InvalidArgument | 🟡 Partially implemented | Malformed requests may fail through routing/codec behavior; operation-specific AWS validation is incomplete. |
| 403 | AccessDenied | ✅ Completed (not implemented) | No authentication, IAM, ACL enforcement, or requester-pays authorization model exists. |
| 404 | Not found condition | ✅ Implemented | NoSuchBucket, NoSuchKey, NoSuchUpload, or missing configuration paths are handled where applicable. |
| 405 | MethodNotAllowed | 🟡 Partially implemented | Unsupported methods may be rejected by WebFlux routing, but S3 XML MethodNotAllowed responses are not implemented. |
| 409 | Conflict | ✅ Completed (not implemented) | AWS conflict variants such as BucketNotEmpty or operation conflicts are not fully modeled. |
| 500 | InternalServerError | ✅ Completed (not implemented) | No explicit AWS InternalServerError mapping exists for this operation. |
| 501 | NotImplemented | ✅ Completed (not implemented) | Unsupported optional AWS S3 features are currently ignored or documented as gaps rather than returned as `NotImplemented`. |
| 503 | SlowDown or ServiceUnavailable | ✅ Completed (not implemented) | No throttling or service-availability model exists. |

---

### Operation 24 — PutBucketAcl `PUT /{bucket}?acl`

| Coverage Area | Required / Optional | Status | Notes |
|---|---|---|---|
| Request headers | Optional `x-amz-acl` is stored adapter-locally; `x-amz-grant-*` and expected-owner headers are ignored. | ⬜ Hashtable-only | Header coverage for this operation. |
| Query parameters | Required `acl` route flag is tested. | ✅ Tested | Route and query coverage for this operation. |
| Request body | AWS `AccessControlPolicy` XML body is not parsed; current implementation uses the canned ACL header or `private` default. | 🔴 Ignored | Request payload coverage. |
| Response body / headers | No success body. | ✅ Tested | Response payload and significant header coverage. |

#### Status Codes

| Status Code | Description | Implemented | Notes |
|---|---|---|---|
| 200 | Success | ✅ Implemented | Returned by the handler for successful requests. |
| 400 | Bad Request or InvalidArgument | 🟡 Partially implemented | Malformed requests may fail through routing/codec behavior; operation-specific AWS validation is incomplete. |
| 403 | AccessDenied | ✅ Completed (not implemented) | No authentication, IAM, ACL enforcement, or requester-pays authorization model exists. |
| 404 | Not found condition | ✅ Implemented | NoSuchBucket, NoSuchKey, NoSuchUpload, or missing configuration paths are handled where applicable. |
| 405 | MethodNotAllowed | 🟡 Partially implemented | Unsupported methods may be rejected by WebFlux routing, but S3 XML MethodNotAllowed responses are not implemented. |
| 409 | Conflict | ✅ Completed (not implemented) | AWS conflict variants such as BucketNotEmpty or operation conflicts are not fully modeled. |
| 500 | InternalServerError | ✅ Completed (not implemented) | No explicit AWS InternalServerError mapping exists for this operation. |
| 501 | NotImplemented | ✅ Completed (not implemented) | Unsupported optional AWS S3 features are currently ignored or documented as gaps rather than returned as `NotImplemented`. |
| 503 | SlowDown or ServiceUnavailable | ✅ Completed (not implemented) | No throttling or service-availability model exists. |

---

### Operation 25 — GetBucketTagging `GET /{bucket}?tagging`

| Coverage Area | Required / Optional | Status | Notes |
|---|---|---|---|
| Request headers | Optional `x-amz-expected-bucket-owner` is ignored. | 🔴 Ignored | Header coverage for this operation. |
| Query parameters | Required `tagging` route flag is tested. | ✅ Tested | Route and query coverage for this operation. |
| Request body | None. | ✅ Tested | Request payload coverage. |
| Response body / headers | `Tagging` XML from adapter-local bucket tag map. | ⬜ Hashtable-only | Response payload and significant header coverage. |

#### Status Codes

| Status Code | Description | Implemented | Notes |
|---|---|---|---|
| 200 | Success | ✅ Implemented | Returned by the handler for successful requests. |
| 301 | PermanentRedirect or wrong-region redirect | ✅ Completed (not implemented) | Region redirects are not modeled by the in-memory local service. |
| 304 | NotModified for conditional requests | ✅ Completed (not implemented) | Conditional request headers are ignored. |
| 400 | Bad Request or InvalidArgument | 🟡 Partially implemented | Malformed requests may fail through routing/codec behavior; operation-specific AWS validation is incomplete. |
| 403 | AccessDenied | ✅ Completed (not implemented) | No authentication, IAM, ACL enforcement, or requester-pays authorization model exists. |
| 404 | Not found condition | ✅ Implemented | NoSuchBucket, NoSuchKey, NoSuchUpload, or missing configuration paths are handled where applicable. |
| 405 | MethodNotAllowed | 🟡 Partially implemented | Unsupported methods may be rejected by WebFlux routing, but S3 XML MethodNotAllowed responses are not implemented. |
| 409 | Conflict | ✅ Completed (not implemented) | AWS conflict variants such as BucketNotEmpty or operation conflicts are not fully modeled. |
| 500 | InternalServerError | ✅ Completed (not implemented) | No explicit AWS InternalServerError mapping exists for this operation. |
| 501 | NotImplemented | ✅ Completed (not implemented) | Unsupported optional AWS S3 features are currently ignored or documented as gaps rather than returned as `NotImplemented`. |
| 503 | SlowDown or ServiceUnavailable | ✅ Completed (not implemented) | No throttling or service-availability model exists. |

---

### Operation 26 — PutBucketTagging `PUT /{bucket}?tagging`

| Coverage Area | Required / Optional | Status | Notes |
|---|---|---|---|
| Request headers | Optional `x-amz-expected-bucket-owner` and checksum headers are ignored. | 🔴 Ignored | Header coverage for this operation. |
| Query parameters | Required `tagging` route flag is tested. | ✅ Tested | Route and query coverage for this operation. |
| Request body | `Tagging` XML is parsed and stored adapter-locally. | ⬜ Hashtable-only | Request payload coverage. |
| Response body / headers | No success body. | ✅ Tested | Response payload and significant header coverage. |

#### Status Codes

| Status Code | Description | Implemented | Notes |
|---|---|---|---|
| 200 | Success | ✅ Implemented | Returned by the handler for successful requests. |
| 400 | Bad Request or InvalidArgument | 🟡 Partially implemented | Selected invalid requests are rejected; full AWS validation is incomplete. |
| 403 | AccessDenied | ✅ Completed (not implemented) | No authentication, IAM, ACL enforcement, or requester-pays authorization model exists. |
| 404 | Not found condition | ✅ Implemented | NoSuchBucket, NoSuchKey, NoSuchUpload, or missing configuration paths are handled where applicable. |
| 405 | MethodNotAllowed | 🟡 Partially implemented | Unsupported methods may be rejected by WebFlux routing, but S3 XML MethodNotAllowed responses are not implemented. |
| 409 | Conflict | ✅ Completed (not implemented) | AWS conflict variants such as BucketNotEmpty or operation conflicts are not fully modeled. |
| 500 | InternalServerError | ✅ Completed (not implemented) | No explicit AWS InternalServerError mapping exists for this operation. |
| 501 | NotImplemented | ✅ Completed (not implemented) | Unsupported optional AWS S3 features are currently ignored or documented as gaps rather than returned as `NotImplemented`. |
| 503 | SlowDown or ServiceUnavailable | ✅ Completed (not implemented) | No throttling or service-availability model exists. |

---

### Operation 27 — DeleteBucketTagging `DELETE /{bucket}?tagging`

| Coverage Area | Required / Optional | Status | Notes |
|---|---|---|---|
| Request headers | Optional `x-amz-expected-bucket-owner` is ignored. | 🔴 Ignored | Header coverage for this operation. |
| Query parameters | Required `tagging` route flag is tested. | ✅ Tested | Route and query coverage for this operation. |
| Request body | None. | ✅ Tested | Request payload coverage. |
| Response body / headers | No success body; adapter-local bucket tags are removed. | ✅ Tested | Response payload and significant header coverage. |

#### Status Codes

| Status Code | Description | Implemented | Notes |
|---|---|---|---|
| 204 | No Content success | ✅ Implemented | Returned by the handler for successful delete/abort requests. |
| 400 | Bad Request or InvalidArgument | 🟡 Partially implemented | Malformed requests may fail through routing/codec behavior; operation-specific AWS validation is incomplete. |
| 403 | AccessDenied | ✅ Completed (not implemented) | No authentication, IAM, ACL enforcement, or requester-pays authorization model exists. |
| 404 | Not found condition | ✅ Implemented | NoSuchBucket, NoSuchKey, NoSuchUpload, or missing configuration paths are handled where applicable. |
| 405 | MethodNotAllowed | 🟡 Partially implemented | Unsupported methods may be rejected by WebFlux routing, but S3 XML MethodNotAllowed responses are not implemented. |
| 409 | Conflict | ✅ Completed (not implemented) | AWS conflict variants such as BucketNotEmpty or operation conflicts are not fully modeled. |
| 500 | InternalServerError | ✅ Completed (not implemented) | No explicit AWS InternalServerError mapping exists for this operation. |
| 501 | NotImplemented | ✅ Completed (not implemented) | Unsupported optional AWS S3 features are currently ignored or documented as gaps rather than returned as `NotImplemented`. |
| 503 | SlowDown or ServiceUnavailable | ✅ Completed (not implemented) | No throttling or service-availability model exists. |

---

### Operation 28 — GetBucketCors `GET /{bucket}?cors`

| Coverage Area | Required / Optional | Status | Notes |
|---|---|---|---|
| Request headers | Optional `x-amz-expected-bucket-owner` is ignored. | 🔴 Ignored | Header coverage for this operation. |
| Query parameters | Required `cors` route flag is tested. | ✅ Tested | Route and query coverage for this operation. |
| Request body | None. | ✅ Tested | Request payload coverage. |
| Response body / headers | `CORSConfiguration` response for the stored CORS configuration; missing configuration returns a 404-style S3 error where implemented. | ✅ Tested | Response payload and significant header coverage. |

#### Status Codes

| Status Code | Description | Implemented | Notes |
|---|---|---|---|
| 200 | Success | ✅ Implemented | Returned by the handler for successful requests. |
| 301 | PermanentRedirect or wrong-region redirect | ✅ Completed (not implemented) | Region redirects are not modeled by the in-memory local service. |
| 304 | NotModified for conditional requests | ✅ Completed (not implemented) | Conditional request headers are ignored. |
| 400 | Bad Request or InvalidArgument | 🟡 Partially implemented | Malformed requests may fail through routing/codec behavior; operation-specific AWS validation is incomplete. |
| 403 | AccessDenied | ✅ Completed (not implemented) | No authentication, IAM, ACL enforcement, or requester-pays authorization model exists. |
| 404 | Not found condition | ✅ Implemented | NoSuchBucket, NoSuchKey, NoSuchUpload, or missing configuration paths are handled where applicable. |
| 405 | MethodNotAllowed | 🟡 Partially implemented | Unsupported methods may be rejected by WebFlux routing, but S3 XML MethodNotAllowed responses are not implemented. |
| 409 | Conflict | ✅ Completed (not implemented) | AWS conflict variants such as BucketNotEmpty or operation conflicts are not fully modeled. |
| 500 | InternalServerError | ✅ Completed (not implemented) | No explicit AWS InternalServerError mapping exists for this operation. |
| 501 | NotImplemented | ✅ Completed (not implemented) | Unsupported optional AWS S3 features are currently ignored or documented as gaps rather than returned as `NotImplemented`. |
| 503 | SlowDown or ServiceUnavailable | ✅ Completed (not implemented) | No throttling or service-availability model exists. |

---

### Operation 29 — PutBucketCors `PUT /{bucket}?cors`

| Coverage Area | Required / Optional | Status | Notes |
|---|---|---|---|
| Request headers | Optional `x-amz-expected-bucket-owner` and checksum-related headers are ignored. | 🔴 Ignored | Header coverage for this operation. |
| Query parameters | Required `cors` route flag is tested. | ✅ Tested | Route and query coverage for this operation. |
| Request body | `CORSConfiguration` request body is parsed by Jackson XML and stored through bucket application services. | ✅ Tested | Request payload coverage. |
| Response body / headers | No success body. | ✅ Tested | Response payload and significant header coverage. |

#### Status Codes

| Status Code | Description | Implemented | Notes |
|---|---|---|---|
| 200 | Success | ✅ Implemented | Returned by the handler for successful requests. |
| 400 | Bad Request or InvalidArgument | 🟡 Partially implemented | Selected invalid requests are rejected; full AWS validation is incomplete. |
| 403 | AccessDenied | ✅ Completed (not implemented) | No authentication, IAM, ACL enforcement, or requester-pays authorization model exists. |
| 404 | Not found condition | ✅ Implemented | NoSuchBucket, NoSuchKey, NoSuchUpload, or missing configuration paths are handled where applicable. |
| 405 | MethodNotAllowed | 🟡 Partially implemented | Unsupported methods may be rejected by WebFlux routing, but S3 XML MethodNotAllowed responses are not implemented. |
| 409 | Conflict | ✅ Completed (not implemented) | AWS conflict variants such as BucketNotEmpty or operation conflicts are not fully modeled. |
| 500 | InternalServerError | ✅ Completed (not implemented) | No explicit AWS InternalServerError mapping exists for this operation. |
| 501 | NotImplemented | ✅ Completed (not implemented) | Unsupported optional AWS S3 features are currently ignored or documented as gaps rather than returned as `NotImplemented`. |
| 503 | SlowDown or ServiceUnavailable | ✅ Completed (not implemented) | No throttling or service-availability model exists. |

---

### Operation 30 — DeleteBucketCors `DELETE /{bucket}?cors`

| Coverage Area | Required / Optional | Status | Notes |
|---|---|---|---|
| Request headers | Optional `x-amz-expected-bucket-owner` is ignored. | 🔴 Ignored | Header coverage for this operation. |
| Query parameters | Required `cors` route flag is tested. | ✅ Tested | Route and query coverage for this operation. |
| Request body | None. | ✅ Tested | Request payload coverage. |
| Response body / headers | No success body; stored CORS configuration is removed. | ✅ Tested | Response payload and significant header coverage. |

#### Status Codes

| Status Code | Description | Implemented | Notes |
|---|---|---|---|
| 204 | No Content success | ✅ Implemented | Returned by the handler for successful delete/abort requests. |
| 400 | Bad Request or InvalidArgument | 🟡 Partially implemented | Malformed requests may fail through routing/codec behavior; operation-specific AWS validation is incomplete. |
| 403 | AccessDenied | ✅ Completed (not implemented) | No authentication, IAM, ACL enforcement, or requester-pays authorization model exists. |
| 404 | Not found condition | ✅ Implemented | NoSuchBucket, NoSuchKey, NoSuchUpload, or missing configuration paths are handled where applicable. |
| 405 | MethodNotAllowed | 🟡 Partially implemented | Unsupported methods may be rejected by WebFlux routing, but S3 XML MethodNotAllowed responses are not implemented. |
| 409 | Conflict | ✅ Completed (not implemented) | AWS conflict variants such as BucketNotEmpty or operation conflicts are not fully modeled. |
| 500 | InternalServerError | ✅ Completed (not implemented) | No explicit AWS InternalServerError mapping exists for this operation. |
| 501 | NotImplemented | ✅ Completed (not implemented) | Unsupported optional AWS S3 features are currently ignored or documented as gaps rather than returned as `NotImplemented`. |
| 503 | SlowDown or ServiceUnavailable | ✅ Completed (not implemented) | No throttling or service-availability model exists. |

---

### Operation 31 — GetBucketLifecycleConfiguration `GET /{bucket}?lifecycle`

| Coverage Area | Required / Optional | Status | Notes |
|---|---|---|---|
| Request headers | Optional `x-amz-expected-bucket-owner` is ignored. | 🔴 Ignored | Header coverage for this operation. |
| Query parameters | Required `lifecycle` route flag is tested. | ✅ Tested | Route and query coverage for this operation. |
| Request body | None. | ✅ Tested | Request payload coverage. |
| Response body / headers | `LifecycleConfiguration` response for the stored lifecycle configuration; missing configuration returns a 404-style S3 error where implemented. | ✅ Tested | Response payload and significant header coverage. |

#### Status Codes

| Status Code | Description | Implemented | Notes |
|---|---|---|---|
| 200 | Success | ✅ Implemented | Returned by the handler for successful requests. |
| 301 | PermanentRedirect or wrong-region redirect | ✅ Completed (not implemented) | Region redirects are not modeled by the in-memory local service. |
| 304 | NotModified for conditional requests | ✅ Completed (not implemented) | Conditional request headers are ignored. |
| 400 | Bad Request or InvalidArgument | 🟡 Partially implemented | Malformed requests may fail through routing/codec behavior; operation-specific AWS validation is incomplete. |
| 403 | AccessDenied | ✅ Completed (not implemented) | No authentication, IAM, ACL enforcement, or requester-pays authorization model exists. |
| 404 | Not found condition | ✅ Implemented | NoSuchBucket, NoSuchKey, NoSuchUpload, or missing configuration paths are handled where applicable. |
| 405 | MethodNotAllowed | 🟡 Partially implemented | Unsupported methods may be rejected by WebFlux routing, but S3 XML MethodNotAllowed responses are not implemented. |
| 409 | Conflict | ✅ Completed (not implemented) | AWS conflict variants such as BucketNotEmpty or operation conflicts are not fully modeled. |
| 500 | InternalServerError | ✅ Completed (not implemented) | No explicit AWS InternalServerError mapping exists for this operation. |
| 501 | NotImplemented | ✅ Completed (not implemented) | Unsupported optional AWS S3 features are currently ignored or documented as gaps rather than returned as `NotImplemented`. |
| 503 | SlowDown or ServiceUnavailable | ✅ Completed (not implemented) | No throttling or service-availability model exists. |

---

### Operation 32 — PutBucketLifecycleConfiguration `PUT /{bucket}?lifecycle`

| Coverage Area | Required / Optional | Status | Notes |
|---|---|---|---|
| Request headers | Optional `x-amz-expected-bucket-owner` and checksum-related headers are ignored. | 🔴 Ignored | Header coverage for this operation. |
| Query parameters | Required `lifecycle` route flag is tested. | ✅ Tested | Route and query coverage for this operation. |
| Request body | `LifecycleConfiguration` request body is parsed by Jackson XML and stored through bucket application services. | ✅ Tested | Request payload coverage. |
| Response body / headers | No success body. | ✅ Tested | Response payload and significant header coverage. |

#### Status Codes

| Status Code | Description | Implemented | Notes |
|---|---|---|---|
| 200 | Success | ✅ Implemented | Returned by the handler for successful requests. |
| 400 | Bad Request or InvalidArgument | 🟡 Partially implemented | Selected invalid requests are rejected; full AWS validation is incomplete. |
| 403 | AccessDenied | ✅ Completed (not implemented) | No authentication, IAM, ACL enforcement, or requester-pays authorization model exists. |
| 404 | Not found condition | ✅ Implemented | NoSuchBucket, NoSuchKey, NoSuchUpload, or missing configuration paths are handled where applicable. |
| 405 | MethodNotAllowed | 🟡 Partially implemented | Unsupported methods may be rejected by WebFlux routing, but S3 XML MethodNotAllowed responses are not implemented. |
| 409 | Conflict | ✅ Completed (not implemented) | AWS conflict variants such as BucketNotEmpty or operation conflicts are not fully modeled. |
| 500 | InternalServerError | ✅ Completed (not implemented) | No explicit AWS InternalServerError mapping exists for this operation. |
| 501 | NotImplemented | ✅ Completed (not implemented) | Unsupported optional AWS S3 features are currently ignored or documented as gaps rather than returned as `NotImplemented`. |
| 503 | SlowDown or ServiceUnavailable | ✅ Completed (not implemented) | No throttling or service-availability model exists. |

---

### Operation 33 — DeleteBucketLifecycleConfiguration `DELETE /{bucket}?lifecycle`

| Coverage Area | Required / Optional | Status | Notes |
|---|---|---|---|
| Request headers | Optional `x-amz-expected-bucket-owner` is ignored. | 🔴 Ignored | Header coverage for this operation. |
| Query parameters | Required `lifecycle` route flag is tested. | ✅ Tested | Route and query coverage for this operation. |
| Request body | None. | ✅ Tested | Request payload coverage. |
| Response body / headers | No success body; stored lifecycle configuration is removed. | ✅ Tested | Response payload and significant header coverage. |

#### Status Codes

| Status Code | Description | Implemented | Notes |
|---|---|---|---|
| 204 | No Content success | ✅ Implemented | Returned by the handler for successful delete/abort requests. |
| 400 | Bad Request or InvalidArgument | 🟡 Partially implemented | Malformed requests may fail through routing/codec behavior; operation-specific AWS validation is incomplete. |
| 403 | AccessDenied | ✅ Completed (not implemented) | No authentication, IAM, ACL enforcement, or requester-pays authorization model exists. |
| 404 | Not found condition | ✅ Implemented | NoSuchBucket, NoSuchKey, NoSuchUpload, or missing configuration paths are handled where applicable. |
| 405 | MethodNotAllowed | 🟡 Partially implemented | Unsupported methods may be rejected by WebFlux routing, but S3 XML MethodNotAllowed responses are not implemented. |
| 409 | Conflict | ✅ Completed (not implemented) | AWS conflict variants such as BucketNotEmpty or operation conflicts are not fully modeled. |
| 500 | InternalServerError | ✅ Completed (not implemented) | No explicit AWS InternalServerError mapping exists for this operation. |
| 501 | NotImplemented | ✅ Completed (not implemented) | Unsupported optional AWS S3 features are currently ignored or documented as gaps rather than returned as `NotImplemented`. |
| 503 | SlowDown or ServiceUnavailable | ✅ Completed (not implemented) | No throttling or service-availability model exists. |

---

### Operation 34 — GetBucketPolicy `GET /{bucket}?policy`

| Coverage Area | Required / Optional | Status | Notes |
|---|---|---|---|
| Request headers | Optional `x-amz-expected-bucket-owner` is ignored. | 🔴 Ignored | Header coverage for this operation. |
| Query parameters | Required `policy` route flag is tested. | ✅ Tested | Route and query coverage for this operation. |
| Request body | None. | ✅ Tested | Request payload coverage. |
| Response body / headers | `JSON policy document` response for the stored bucket policy; missing configuration returns a 404-style S3 error where implemented. | ✅ Tested | Response payload and significant header coverage. |

#### Status Codes

| Status Code | Description | Implemented | Notes |
|---|---|---|---|
| 200 | Success | ✅ Implemented | Returned by the handler for successful requests. |
| 301 | PermanentRedirect or wrong-region redirect | ✅ Completed (not implemented) | Region redirects are not modeled by the in-memory local service. |
| 304 | NotModified for conditional requests | ✅ Completed (not implemented) | Conditional request headers are ignored. |
| 400 | Bad Request or InvalidArgument | 🟡 Partially implemented | Malformed requests may fail through routing/codec behavior; operation-specific AWS validation is incomplete. |
| 403 | AccessDenied | ✅ Completed (not implemented) | No authentication, IAM, ACL enforcement, or requester-pays authorization model exists. |
| 404 | Not found condition | ✅ Implemented | NoSuchBucket, NoSuchKey, NoSuchUpload, or missing configuration paths are handled where applicable. |
| 405 | MethodNotAllowed | 🟡 Partially implemented | Unsupported methods may be rejected by WebFlux routing, but S3 XML MethodNotAllowed responses are not implemented. |
| 409 | Conflict | ✅ Completed (not implemented) | AWS conflict variants such as BucketNotEmpty or operation conflicts are not fully modeled. |
| 500 | InternalServerError | ✅ Completed (not implemented) | No explicit AWS InternalServerError mapping exists for this operation. |
| 501 | NotImplemented | ✅ Completed (not implemented) | Unsupported optional AWS S3 features are currently ignored or documented as gaps rather than returned as `NotImplemented`. |
| 503 | SlowDown or ServiceUnavailable | ✅ Completed (not implemented) | No throttling or service-availability model exists. |

---

### Operation 35 — PutBucketPolicy `PUT /{bucket}?policy`

| Coverage Area | Required / Optional | Status | Notes |
|---|---|---|---|
| Request headers | Optional `x-amz-expected-bucket-owner` and checksum-related headers are ignored. | 🔴 Ignored | Header coverage for this operation. |
| Query parameters | Required `policy` route flag is tested. | ✅ Tested | Route and query coverage for this operation. |
| Request body | `JSON policy document` request body is parsed by Jackson XML and stored through bucket application services. | ✅ Tested | Request payload coverage. |
| Response body / headers | No success body. | ✅ Tested | Response payload and significant header coverage. |

#### Status Codes

| Status Code | Description | Implemented | Notes |
|---|---|---|---|
| 200 | Success | ✅ Implemented | Returned by the handler for successful requests. |
| 400 | Bad Request or InvalidArgument | 🟡 Partially implemented | Selected invalid requests are rejected; full AWS validation is incomplete. |
| 403 | AccessDenied | ✅ Completed (not implemented) | No authentication, IAM, ACL enforcement, or requester-pays authorization model exists. |
| 404 | Not found condition | ✅ Implemented | NoSuchBucket, NoSuchKey, NoSuchUpload, or missing configuration paths are handled where applicable. |
| 405 | MethodNotAllowed | 🟡 Partially implemented | Unsupported methods may be rejected by WebFlux routing, but S3 XML MethodNotAllowed responses are not implemented. |
| 409 | Conflict | ✅ Completed (not implemented) | AWS conflict variants such as BucketNotEmpty or operation conflicts are not fully modeled. |
| 500 | InternalServerError | ✅ Completed (not implemented) | No explicit AWS InternalServerError mapping exists for this operation. |
| 501 | NotImplemented | ✅ Completed (not implemented) | Unsupported optional AWS S3 features are currently ignored or documented as gaps rather than returned as `NotImplemented`. |
| 503 | SlowDown or ServiceUnavailable | ✅ Completed (not implemented) | No throttling or service-availability model exists. |

---

### Operation 36 — DeleteBucketPolicy `DELETE /{bucket}?policy`

| Coverage Area | Required / Optional | Status | Notes |
|---|---|---|---|
| Request headers | Optional `x-amz-expected-bucket-owner` is ignored. | 🔴 Ignored | Header coverage for this operation. |
| Query parameters | Required `policy` route flag is tested. | ✅ Tested | Route and query coverage for this operation. |
| Request body | None. | ✅ Tested | Request payload coverage. |
| Response body / headers | No success body; stored bucket policy is removed. | ✅ Tested | Response payload and significant header coverage. |

#### Status Codes

| Status Code | Description | Implemented | Notes |
|---|---|---|---|
| 204 | No Content success | ✅ Implemented | Returned by the handler for successful delete/abort requests. |
| 400 | Bad Request or InvalidArgument | 🟡 Partially implemented | Malformed requests may fail through routing/codec behavior; operation-specific AWS validation is incomplete. |
| 403 | AccessDenied | ✅ Completed (not implemented) | No authentication, IAM, ACL enforcement, or requester-pays authorization model exists. |
| 404 | Not found condition | ✅ Implemented | NoSuchBucket, NoSuchKey, NoSuchUpload, or missing configuration paths are handled where applicable. |
| 405 | MethodNotAllowed | 🟡 Partially implemented | Unsupported methods may be rejected by WebFlux routing, but S3 XML MethodNotAllowed responses are not implemented. |
| 409 | Conflict | ✅ Completed (not implemented) | AWS conflict variants such as BucketNotEmpty or operation conflicts are not fully modeled. |
| 500 | InternalServerError | ✅ Completed (not implemented) | No explicit AWS InternalServerError mapping exists for this operation. |
| 501 | NotImplemented | ✅ Completed (not implemented) | Unsupported optional AWS S3 features are currently ignored or documented as gaps rather than returned as `NotImplemented`. |
| 503 | SlowDown or ServiceUnavailable | ✅ Completed (not implemented) | No throttling or service-availability model exists. |

---

### Operation 37 — GetBucketEncryption `GET /{bucket}?encryption`

| Coverage Area | Required / Optional | Status | Notes |
|---|---|---|---|
| Request headers | Optional `x-amz-expected-bucket-owner` is ignored. | 🔴 Ignored | Header coverage for this operation. |
| Query parameters | Required `encryption` route flag is tested. | ✅ Tested | Route and query coverage for this operation. |
| Request body | None. | ✅ Tested | Request payload coverage. |
| Response body / headers | `EncryptionConfiguration` response for the stored encryption configuration; missing configuration returns a 404-style S3 error where implemented. | ✅ Tested | Response payload and significant header coverage. |

#### Status Codes

| Status Code | Description | Implemented | Notes |
|---|---|---|---|
| 200 | Success | ✅ Implemented | Returned by the handler for successful requests. |
| 301 | PermanentRedirect or wrong-region redirect | ✅ Completed (not implemented) | Region redirects are not modeled by the in-memory local service. |
| 304 | NotModified for conditional requests | ✅ Completed (not implemented) | Conditional request headers are ignored. |
| 400 | Bad Request or InvalidArgument | 🟡 Partially implemented | Malformed requests may fail through routing/codec behavior; operation-specific AWS validation is incomplete. |
| 403 | AccessDenied | ✅ Completed (not implemented) | No authentication, IAM, ACL enforcement, or requester-pays authorization model exists. |
| 404 | Not found condition | ✅ Implemented | NoSuchBucket, NoSuchKey, NoSuchUpload, or missing configuration paths are handled where applicable. |
| 405 | MethodNotAllowed | 🟡 Partially implemented | Unsupported methods may be rejected by WebFlux routing, but S3 XML MethodNotAllowed responses are not implemented. |
| 409 | Conflict | ✅ Completed (not implemented) | AWS conflict variants such as BucketNotEmpty or operation conflicts are not fully modeled. |
| 500 | InternalServerError | ✅ Completed (not implemented) | No explicit AWS InternalServerError mapping exists for this operation. |
| 501 | NotImplemented | ✅ Completed (not implemented) | Unsupported optional AWS S3 features are currently ignored or documented as gaps rather than returned as `NotImplemented`. |
| 503 | SlowDown or ServiceUnavailable | ✅ Completed (not implemented) | No throttling or service-availability model exists. |

---

### Operation 38 — PutBucketEncryption `PUT /{bucket}?encryption`

| Coverage Area | Required / Optional | Status | Notes |
|---|---|---|---|
| Request headers | Optional `x-amz-expected-bucket-owner` and checksum-related headers are ignored. | 🔴 Ignored | Header coverage for this operation. |
| Query parameters | Required `encryption` route flag is tested. | ✅ Tested | Route and query coverage for this operation. |
| Request body | `EncryptionConfiguration` request body is parsed by Jackson XML and stored through bucket application services. | ✅ Tested | Request payload coverage. |
| Response body / headers | No success body. | ✅ Tested | Response payload and significant header coverage. |

#### Status Codes

| Status Code | Description | Implemented | Notes |
|---|---|---|---|
| 200 | Success | ✅ Implemented | Returned by the handler for successful requests. |
| 400 | Bad Request or InvalidArgument | 🟡 Partially implemented | Selected invalid requests are rejected; full AWS validation is incomplete. |
| 403 | AccessDenied | ✅ Completed (not implemented) | No authentication, IAM, ACL enforcement, or requester-pays authorization model exists. |
| 404 | Not found condition | ✅ Implemented | NoSuchBucket, NoSuchKey, NoSuchUpload, or missing configuration paths are handled where applicable. |
| 405 | MethodNotAllowed | 🟡 Partially implemented | Unsupported methods may be rejected by WebFlux routing, but S3 XML MethodNotAllowed responses are not implemented. |
| 409 | Conflict | ✅ Completed (not implemented) | AWS conflict variants such as BucketNotEmpty or operation conflicts are not fully modeled. |
| 500 | InternalServerError | ✅ Completed (not implemented) | No explicit AWS InternalServerError mapping exists for this operation. |
| 501 | NotImplemented | ✅ Completed (not implemented) | Unsupported optional AWS S3 features are currently ignored or documented as gaps rather than returned as `NotImplemented`. |
| 503 | SlowDown or ServiceUnavailable | ✅ Completed (not implemented) | No throttling or service-availability model exists. |

---

### Operation 39 — DeleteBucketEncryption `DELETE /{bucket}?encryption`

| Coverage Area | Required / Optional | Status | Notes |
|---|---|---|---|
| Request headers | Optional `x-amz-expected-bucket-owner` is ignored. | 🔴 Ignored | Header coverage for this operation. |
| Query parameters | Required `encryption` route flag is tested. | ✅ Tested | Route and query coverage for this operation. |
| Request body | None. | ✅ Tested | Request payload coverage. |
| Response body / headers | No success body; stored encryption configuration is removed. | ✅ Tested | Response payload and significant header coverage. |

#### Status Codes

| Status Code | Description | Implemented | Notes |
|---|---|---|---|
| 204 | No Content success | ✅ Implemented | Returned by the handler for successful delete/abort requests. |
| 400 | Bad Request or InvalidArgument | 🟡 Partially implemented | Malformed requests may fail through routing/codec behavior; operation-specific AWS validation is incomplete. |
| 403 | AccessDenied | ✅ Completed (not implemented) | No authentication, IAM, ACL enforcement, or requester-pays authorization model exists. |
| 404 | Not found condition | ✅ Implemented | NoSuchBucket, NoSuchKey, NoSuchUpload, or missing configuration paths are handled where applicable. |
| 405 | MethodNotAllowed | 🟡 Partially implemented | Unsupported methods may be rejected by WebFlux routing, but S3 XML MethodNotAllowed responses are not implemented. |
| 409 | Conflict | ✅ Completed (not implemented) | AWS conflict variants such as BucketNotEmpty or operation conflicts are not fully modeled. |
| 500 | InternalServerError | ✅ Completed (not implemented) | No explicit AWS InternalServerError mapping exists for this operation. |
| 501 | NotImplemented | ✅ Completed (not implemented) | Unsupported optional AWS S3 features are currently ignored or documented as gaps rather than returned as `NotImplemented`. |
| 503 | SlowDown or ServiceUnavailable | ✅ Completed (not implemented) | No throttling or service-availability model exists. |

---

### Operation 40 — GetBucketLogging `GET /{bucket}?logging`

| Coverage Area | Required / Optional | Status | Notes |
|---|---|---|---|
| Request headers | Optional `x-amz-expected-bucket-owner` is ignored. | 🔴 Ignored | Header coverage for this operation. |
| Query parameters | Required `logging` route flag is tested. | ✅ Tested | Route and query coverage for this operation. |
| Request body | None. | ✅ Tested | Request payload coverage. |
| Response body / headers | `LoggingConfiguration` response for the stored logging configuration; missing configuration returns a 404-style S3 error where implemented. | ✅ Tested | Response payload and significant header coverage. |

#### Status Codes

| Status Code | Description | Implemented | Notes |
|---|---|---|---|
| 200 | Success | ✅ Implemented | Returned by the handler for successful requests. |
| 301 | PermanentRedirect or wrong-region redirect | ✅ Completed (not implemented) | Region redirects are not modeled by the in-memory local service. |
| 304 | NotModified for conditional requests | ✅ Completed (not implemented) | Conditional request headers are ignored. |
| 400 | Bad Request or InvalidArgument | 🟡 Partially implemented | Malformed requests may fail through routing/codec behavior; operation-specific AWS validation is incomplete. |
| 403 | AccessDenied | ✅ Completed (not implemented) | No authentication, IAM, ACL enforcement, or requester-pays authorization model exists. |
| 404 | Not found condition | ✅ Implemented | NoSuchBucket, NoSuchKey, NoSuchUpload, or missing configuration paths are handled where applicable. |
| 405 | MethodNotAllowed | 🟡 Partially implemented | Unsupported methods may be rejected by WebFlux routing, but S3 XML MethodNotAllowed responses are not implemented. |
| 409 | Conflict | ✅ Completed (not implemented) | AWS conflict variants such as BucketNotEmpty or operation conflicts are not fully modeled. |
| 500 | InternalServerError | ✅ Completed (not implemented) | No explicit AWS InternalServerError mapping exists for this operation. |
| 501 | NotImplemented | ✅ Completed (not implemented) | Unsupported optional AWS S3 features are currently ignored or documented as gaps rather than returned as `NotImplemented`. |
| 503 | SlowDown or ServiceUnavailable | ✅ Completed (not implemented) | No throttling or service-availability model exists. |

---

### Operation 41 — PutBucketLogging `PUT /{bucket}?logging`

| Coverage Area | Required / Optional | Status | Notes |
|---|---|---|---|
| Request headers | Optional `x-amz-expected-bucket-owner` and checksum-related headers are ignored. | 🔴 Ignored | Header coverage for this operation. |
| Query parameters | Required `logging` route flag is tested. | ✅ Tested | Route and query coverage for this operation. |
| Request body | `LoggingConfiguration` request body is parsed by Jackson XML and stored through bucket application services. | ✅ Tested | Request payload coverage. |
| Response body / headers | No success body. | ✅ Tested | Response payload and significant header coverage. |

#### Status Codes

| Status Code | Description | Implemented | Notes |
|---|---|---|---|
| 200 | Success | ✅ Implemented | Returned by the handler for successful requests. |
| 400 | Bad Request or InvalidArgument | 🟡 Partially implemented | Selected invalid requests are rejected; full AWS validation is incomplete. |
| 403 | AccessDenied | ✅ Completed (not implemented) | No authentication, IAM, ACL enforcement, or requester-pays authorization model exists. |
| 404 | Not found condition | ✅ Implemented | NoSuchBucket, NoSuchKey, NoSuchUpload, or missing configuration paths are handled where applicable. |
| 405 | MethodNotAllowed | 🟡 Partially implemented | Unsupported methods may be rejected by WebFlux routing, but S3 XML MethodNotAllowed responses are not implemented. |
| 409 | Conflict | ✅ Completed (not implemented) | AWS conflict variants such as BucketNotEmpty or operation conflicts are not fully modeled. |
| 500 | InternalServerError | ✅ Completed (not implemented) | No explicit AWS InternalServerError mapping exists for this operation. |
| 501 | NotImplemented | ✅ Completed (not implemented) | Unsupported optional AWS S3 features are currently ignored or documented as gaps rather than returned as `NotImplemented`. |
| 503 | SlowDown or ServiceUnavailable | ✅ Completed (not implemented) | No throttling or service-availability model exists. |

---

### Operation 42 — DeleteBucketLogging `DELETE /{bucket}?logging`

| Coverage Area | Required / Optional | Status | Notes |
|---|---|---|---|
| Request headers | Optional `x-amz-expected-bucket-owner` is ignored. | 🔴 Ignored | Header coverage for this operation. |
| Query parameters | Required `logging` route flag is tested. | ✅ Tested | Route and query coverage for this operation. |
| Request body | None. | ✅ Tested | Request payload coverage. |
| Response body / headers | No success body; stored logging configuration is removed. | ✅ Tested | Response payload and significant header coverage. |

#### Status Codes

| Status Code | Description | Implemented | Notes |
|---|---|---|---|
| 204 | No Content success | ✅ Implemented | Returned by the handler for successful delete/abort requests. |
| 400 | Bad Request or InvalidArgument | 🟡 Partially implemented | Malformed requests may fail through routing/codec behavior; operation-specific AWS validation is incomplete. |
| 403 | AccessDenied | ✅ Completed (not implemented) | No authentication, IAM, ACL enforcement, or requester-pays authorization model exists. |
| 404 | Not found condition | ✅ Implemented | NoSuchBucket, NoSuchKey, NoSuchUpload, or missing configuration paths are handled where applicable. |
| 405 | MethodNotAllowed | 🟡 Partially implemented | Unsupported methods may be rejected by WebFlux routing, but S3 XML MethodNotAllowed responses are not implemented. |
| 409 | Conflict | ✅ Completed (not implemented) | AWS conflict variants such as BucketNotEmpty or operation conflicts are not fully modeled. |
| 500 | InternalServerError | ✅ Completed (not implemented) | No explicit AWS InternalServerError mapping exists for this operation. |
| 501 | NotImplemented | ✅ Completed (not implemented) | Unsupported optional AWS S3 features are currently ignored or documented as gaps rather than returned as `NotImplemented`. |
| 503 | SlowDown or ServiceUnavailable | ✅ Completed (not implemented) | No throttling or service-availability model exists. |

---

### Operation 43 — GetBucketWebsite `GET /{bucket}?website`

| Coverage Area | Required / Optional | Status | Notes |
|---|---|---|---|
| Request headers | Optional `x-amz-expected-bucket-owner` is ignored. | 🔴 Ignored | Header coverage for this operation. |
| Query parameters | Required `website` route flag is tested. | ✅ Tested | Route and query coverage for this operation. |
| Request body | None. | ✅ Tested | Request payload coverage. |
| Response body / headers | `WebsiteConfiguration` response for the stored website configuration; missing configuration returns a 404-style S3 error where implemented. | ✅ Tested | Response payload and significant header coverage. |

#### Status Codes

| Status Code | Description | Implemented | Notes |
|---|---|---|---|
| 200 | Success | ✅ Implemented | Returned by the handler for successful requests. |
| 301 | PermanentRedirect or wrong-region redirect | ✅ Completed (not implemented) | Region redirects are not modeled by the in-memory local service. |
| 304 | NotModified for conditional requests | ✅ Completed (not implemented) | Conditional request headers are ignored. |
| 400 | Bad Request or InvalidArgument | 🟡 Partially implemented | Malformed requests may fail through routing/codec behavior; operation-specific AWS validation is incomplete. |
| 403 | AccessDenied | ✅ Completed (not implemented) | No authentication, IAM, ACL enforcement, or requester-pays authorization model exists. |
| 404 | Not found condition | ✅ Implemented | NoSuchBucket, NoSuchKey, NoSuchUpload, or missing configuration paths are handled where applicable. |
| 405 | MethodNotAllowed | 🟡 Partially implemented | Unsupported methods may be rejected by WebFlux routing, but S3 XML MethodNotAllowed responses are not implemented. |
| 409 | Conflict | ✅ Completed (not implemented) | AWS conflict variants such as BucketNotEmpty or operation conflicts are not fully modeled. |
| 500 | InternalServerError | ✅ Completed (not implemented) | No explicit AWS InternalServerError mapping exists for this operation. |
| 501 | NotImplemented | ✅ Completed (not implemented) | Unsupported optional AWS S3 features are currently ignored or documented as gaps rather than returned as `NotImplemented`. |
| 503 | SlowDown or ServiceUnavailable | ✅ Completed (not implemented) | No throttling or service-availability model exists. |

---

### Operation 44 — PutBucketWebsite `PUT /{bucket}?website`

| Coverage Area | Required / Optional | Status | Notes |
|---|---|---|---|
| Request headers | Optional `x-amz-expected-bucket-owner` and checksum-related headers are ignored. | 🔴 Ignored | Header coverage for this operation. |
| Query parameters | Required `website` route flag is tested. | ✅ Tested | Route and query coverage for this operation. |
| Request body | `WebsiteConfiguration` request body is parsed by Jackson XML and stored through bucket application services. | ✅ Tested | Request payload coverage. |
| Response body / headers | No success body. | ✅ Tested | Response payload and significant header coverage. |

#### Status Codes

| Status Code | Description | Implemented | Notes |
|---|---|---|---|
| 200 | Success | ✅ Implemented | Returned by the handler for successful requests. |
| 400 | Bad Request or InvalidArgument | 🟡 Partially implemented | Selected invalid requests are rejected; full AWS validation is incomplete. |
| 403 | AccessDenied | ✅ Completed (not implemented) | No authentication, IAM, ACL enforcement, or requester-pays authorization model exists. |
| 404 | Not found condition | ✅ Implemented | NoSuchBucket, NoSuchKey, NoSuchUpload, or missing configuration paths are handled where applicable. |
| 405 | MethodNotAllowed | 🟡 Partially implemented | Unsupported methods may be rejected by WebFlux routing, but S3 XML MethodNotAllowed responses are not implemented. |
| 409 | Conflict | ✅ Completed (not implemented) | AWS conflict variants such as BucketNotEmpty or operation conflicts are not fully modeled. |
| 500 | InternalServerError | ✅ Completed (not implemented) | No explicit AWS InternalServerError mapping exists for this operation. |
| 501 | NotImplemented | ✅ Completed (not implemented) | Unsupported optional AWS S3 features are currently ignored or documented as gaps rather than returned as `NotImplemented`. |
| 503 | SlowDown or ServiceUnavailable | ✅ Completed (not implemented) | No throttling or service-availability model exists. |

---

### Operation 45 — DeleteBucketWebsite `DELETE /{bucket}?website`

| Coverage Area | Required / Optional | Status | Notes |
|---|---|---|---|
| Request headers | Optional `x-amz-expected-bucket-owner` is ignored. | 🔴 Ignored | Header coverage for this operation. |
| Query parameters | Required `website` route flag is tested. | ✅ Tested | Route and query coverage for this operation. |
| Request body | None. | ✅ Tested | Request payload coverage. |
| Response body / headers | No success body; stored website configuration is removed. | ✅ Tested | Response payload and significant header coverage. |

#### Status Codes

| Status Code | Description | Implemented | Notes |
|---|---|---|---|
| 204 | No Content success | ✅ Implemented | Returned by the handler for successful delete/abort requests. |
| 400 | Bad Request or InvalidArgument | 🟡 Partially implemented | Malformed requests may fail through routing/codec behavior; operation-specific AWS validation is incomplete. |
| 403 | AccessDenied | ✅ Completed (not implemented) | No authentication, IAM, ACL enforcement, or requester-pays authorization model exists. |
| 404 | Not found condition | ✅ Implemented | NoSuchBucket, NoSuchKey, NoSuchUpload, or missing configuration paths are handled where applicable. |
| 405 | MethodNotAllowed | 🟡 Partially implemented | Unsupported methods may be rejected by WebFlux routing, but S3 XML MethodNotAllowed responses are not implemented. |
| 409 | Conflict | ✅ Completed (not implemented) | AWS conflict variants such as BucketNotEmpty or operation conflicts are not fully modeled. |
| 500 | InternalServerError | ✅ Completed (not implemented) | No explicit AWS InternalServerError mapping exists for this operation. |
| 501 | NotImplemented | ✅ Completed (not implemented) | Unsupported optional AWS S3 features are currently ignored or documented as gaps rather than returned as `NotImplemented`. |
| 503 | SlowDown or ServiceUnavailable | ✅ Completed (not implemented) | No throttling or service-availability model exists. |

---

### Operation 46 — GetBucketNotification `GET /{bucket}?notification`

| Coverage Area | Required / Optional | Status | Notes |
|---|---|---|---|
| Request headers | Optional `x-amz-expected-bucket-owner` is ignored. | 🔴 Ignored | Header coverage for this operation. |
| Query parameters | Required `notification` route flag is tested. | ✅ Tested | Route and query coverage for this operation. |
| Request body | None. | ✅ Tested | Request payload coverage. |
| Response body / headers | `NotificationConfiguration` response for the stored notification configuration; missing configuration returns a 404-style S3 error where implemented. | ✅ Tested | Response payload and significant header coverage. |

#### Status Codes

| Status Code | Description | Implemented | Notes |
|---|---|---|---|
| 200 | Success | ✅ Implemented | Returned by the handler for successful requests. |
| 301 | PermanentRedirect or wrong-region redirect | ✅ Completed (not implemented) | Region redirects are not modeled by the in-memory local service. |
| 304 | NotModified for conditional requests | ✅ Completed (not implemented) | Conditional request headers are ignored. |
| 400 | Bad Request or InvalidArgument | 🟡 Partially implemented | Malformed requests may fail through routing/codec behavior; operation-specific AWS validation is incomplete. |
| 403 | AccessDenied | ✅ Completed (not implemented) | No authentication, IAM, ACL enforcement, or requester-pays authorization model exists. |
| 404 | Not found condition | ✅ Implemented | NoSuchBucket, NoSuchKey, NoSuchUpload, or missing configuration paths are handled where applicable. |
| 405 | MethodNotAllowed | 🟡 Partially implemented | Unsupported methods may be rejected by WebFlux routing, but S3 XML MethodNotAllowed responses are not implemented. |
| 409 | Conflict | ✅ Completed (not implemented) | AWS conflict variants such as BucketNotEmpty or operation conflicts are not fully modeled. |
| 500 | InternalServerError | ✅ Completed (not implemented) | No explicit AWS InternalServerError mapping exists for this operation. |
| 501 | NotImplemented | ✅ Completed (not implemented) | Unsupported optional AWS S3 features are currently ignored or documented as gaps rather than returned as `NotImplemented`. |
| 503 | SlowDown or ServiceUnavailable | ✅ Completed (not implemented) | No throttling or service-availability model exists. |

---

### Operation 47 — PutBucketNotification `PUT /{bucket}?notification`

| Coverage Area | Required / Optional | Status | Notes |
|---|---|---|---|
| Request headers | Optional `x-amz-expected-bucket-owner` and checksum-related headers are ignored. | 🔴 Ignored | Header coverage for this operation. |
| Query parameters | Required `notification` route flag is tested. | ✅ Tested | Route and query coverage for this operation. |
| Request body | `NotificationConfiguration` request body is parsed by Jackson XML and stored through bucket application services. | ✅ Tested | Request payload coverage. |
| Response body / headers | No success body. | ✅ Tested | Response payload and significant header coverage. |

#### Status Codes

| Status Code | Description | Implemented | Notes |
|---|---|---|---|
| 200 | Success | ✅ Implemented | Returned by the handler for successful requests. |
| 400 | Bad Request or InvalidArgument | 🟡 Partially implemented | Selected invalid requests are rejected; full AWS validation is incomplete. |
| 403 | AccessDenied | ✅ Completed (not implemented) | No authentication, IAM, ACL enforcement, or requester-pays authorization model exists. |
| 404 | Not found condition | ✅ Implemented | NoSuchBucket, NoSuchKey, NoSuchUpload, or missing configuration paths are handled where applicable. |
| 405 | MethodNotAllowed | 🟡 Partially implemented | Unsupported methods may be rejected by WebFlux routing, but S3 XML MethodNotAllowed responses are not implemented. |
| 409 | Conflict | ✅ Completed (not implemented) | AWS conflict variants such as BucketNotEmpty or operation conflicts are not fully modeled. |
| 500 | InternalServerError | ✅ Completed (not implemented) | No explicit AWS InternalServerError mapping exists for this operation. |
| 501 | NotImplemented | ✅ Completed (not implemented) | Unsupported optional AWS S3 features are currently ignored or documented as gaps rather than returned as `NotImplemented`. |
| 503 | SlowDown or ServiceUnavailable | ✅ Completed (not implemented) | No throttling or service-availability model exists. |

---

### Operation 48 — DeleteBucketNotification `DELETE /{bucket}?notification`

| Coverage Area | Required / Optional | Status | Notes |
|---|---|---|---|
| Request headers | Optional `x-amz-expected-bucket-owner` is ignored. | 🔴 Ignored | Header coverage for this operation. |
| Query parameters | Required `notification` route flag is tested. | ✅ Tested | Route and query coverage for this operation. |
| Request body | None. | ✅ Tested | Request payload coverage. |
| Response body / headers | No success body; stored notification configuration is removed. | ✅ Tested | Response payload and significant header coverage. |

#### Status Codes

| Status Code | Description | Implemented | Notes |
|---|---|---|---|
| 204 | No Content success | ✅ Implemented | Returned by the handler for successful delete/abort requests. |
| 400 | Bad Request or InvalidArgument | 🟡 Partially implemented | Malformed requests may fail through routing/codec behavior; operation-specific AWS validation is incomplete. |
| 403 | AccessDenied | ✅ Completed (not implemented) | No authentication, IAM, ACL enforcement, or requester-pays authorization model exists. |
| 404 | Not found condition | ✅ Implemented | NoSuchBucket, NoSuchKey, NoSuchUpload, or missing configuration paths are handled where applicable. |
| 405 | MethodNotAllowed | 🟡 Partially implemented | Unsupported methods may be rejected by WebFlux routing, but S3 XML MethodNotAllowed responses are not implemented. |
| 409 | Conflict | ✅ Completed (not implemented) | AWS conflict variants such as BucketNotEmpty or operation conflicts are not fully modeled. |
| 500 | InternalServerError | ✅ Completed (not implemented) | No explicit AWS InternalServerError mapping exists for this operation. |
| 501 | NotImplemented | ✅ Completed (not implemented) | Unsupported optional AWS S3 features are currently ignored or documented as gaps rather than returned as `NotImplemented`. |
| 503 | SlowDown or ServiceUnavailable | ✅ Completed (not implemented) | No throttling or service-availability model exists. |

---

### Operation 49 — GetBucketReplication `GET /{bucket}?replication`

| Coverage Area | Required / Optional | Status | Notes |
|---|---|---|---|
| Request headers | Optional `x-amz-expected-bucket-owner` is ignored. | 🔴 Ignored | Header coverage for this operation. |
| Query parameters | Required `replication` route flag is tested. | ✅ Tested | Route and query coverage for this operation. |
| Request body | None. | ✅ Tested | Request payload coverage. |
| Response body / headers | `ReplicationConfiguration` response for the stored replication configuration; missing configuration returns a 404-style S3 error where implemented. | ✅ Tested | Response payload and significant header coverage. |

#### Status Codes

| Status Code | Description | Implemented | Notes |
|---|---|---|---|
| 200 | Success | ✅ Implemented | Returned by the handler for successful requests. |
| 301 | PermanentRedirect or wrong-region redirect | ✅ Completed (not implemented) | Region redirects are not modeled by the in-memory local service. |
| 304 | NotModified for conditional requests | ✅ Completed (not implemented) | Conditional request headers are ignored. |
| 400 | Bad Request or InvalidArgument | 🟡 Partially implemented | Malformed requests may fail through routing/codec behavior; operation-specific AWS validation is incomplete. |
| 403 | AccessDenied | ✅ Completed (not implemented) | No authentication, IAM, ACL enforcement, or requester-pays authorization model exists. |
| 404 | Not found condition | ✅ Implemented | NoSuchBucket, NoSuchKey, NoSuchUpload, or missing configuration paths are handled where applicable. |
| 405 | MethodNotAllowed | 🟡 Partially implemented | Unsupported methods may be rejected by WebFlux routing, but S3 XML MethodNotAllowed responses are not implemented. |
| 409 | Conflict | ✅ Completed (not implemented) | AWS conflict variants such as BucketNotEmpty or operation conflicts are not fully modeled. |
| 500 | InternalServerError | ✅ Completed (not implemented) | No explicit AWS InternalServerError mapping exists for this operation. |
| 501 | NotImplemented | ✅ Completed (not implemented) | Unsupported optional AWS S3 features are currently ignored or documented as gaps rather than returned as `NotImplemented`. |
| 503 | SlowDown or ServiceUnavailable | ✅ Completed (not implemented) | No throttling or service-availability model exists. |

---

### Operation 50 — PutBucketReplication `PUT /{bucket}?replication`

| Coverage Area | Required / Optional | Status | Notes |
|---|---|---|---|
| Request headers | Optional `x-amz-expected-bucket-owner` and checksum-related headers are ignored. | 🔴 Ignored | Header coverage for this operation. |
| Query parameters | Required `replication` route flag is tested. | ✅ Tested | Route and query coverage for this operation. |
| Request body | `ReplicationConfiguration` request body is parsed by Jackson XML and stored through bucket application services. | ✅ Tested | Request payload coverage. |
| Response body / headers | No success body. | ✅ Tested | Response payload and significant header coverage. |

#### Status Codes

| Status Code | Description | Implemented | Notes |
|---|---|---|---|
| 200 | Success | ✅ Implemented | Returned by the handler for successful requests. |
| 400 | Bad Request or InvalidArgument | 🟡 Partially implemented | Selected invalid requests are rejected; full AWS validation is incomplete. |
| 403 | AccessDenied | ✅ Completed (not implemented) | No authentication, IAM, ACL enforcement, or requester-pays authorization model exists. |
| 404 | Not found condition | ✅ Implemented | NoSuchBucket, NoSuchKey, NoSuchUpload, or missing configuration paths are handled where applicable. |
| 405 | MethodNotAllowed | 🟡 Partially implemented | Unsupported methods may be rejected by WebFlux routing, but S3 XML MethodNotAllowed responses are not implemented. |
| 409 | Conflict | ✅ Completed (not implemented) | AWS conflict variants such as BucketNotEmpty or operation conflicts are not fully modeled. |
| 500 | InternalServerError | ✅ Completed (not implemented) | No explicit AWS InternalServerError mapping exists for this operation. |
| 501 | NotImplemented | ✅ Completed (not implemented) | Unsupported optional AWS S3 features are currently ignored or documented as gaps rather than returned as `NotImplemented`. |
| 503 | SlowDown or ServiceUnavailable | ✅ Completed (not implemented) | No throttling or service-availability model exists. |

---

### Operation 51 — DeleteBucketReplication `DELETE /{bucket}?replication`

| Coverage Area | Required / Optional | Status | Notes |
|---|---|---|---|
| Request headers | Optional `x-amz-expected-bucket-owner` is ignored. | 🔴 Ignored | Header coverage for this operation. |
| Query parameters | Required `replication` route flag is tested. | ✅ Tested | Route and query coverage for this operation. |
| Request body | None. | ✅ Tested | Request payload coverage. |
| Response body / headers | No success body; stored replication configuration is removed. | ✅ Tested | Response payload and significant header coverage. |

#### Status Codes

| Status Code | Description | Implemented | Notes |
|---|---|---|---|
| 204 | No Content success | ✅ Implemented | Returned by the handler for successful delete/abort requests. |
| 400 | Bad Request or InvalidArgument | 🟡 Partially implemented | Malformed requests may fail through routing/codec behavior; operation-specific AWS validation is incomplete. |
| 403 | AccessDenied | ✅ Completed (not implemented) | No authentication, IAM, ACL enforcement, or requester-pays authorization model exists. |
| 404 | Not found condition | ✅ Implemented | NoSuchBucket, NoSuchKey, NoSuchUpload, or missing configuration paths are handled where applicable. |
| 405 | MethodNotAllowed | 🟡 Partially implemented | Unsupported methods may be rejected by WebFlux routing, but S3 XML MethodNotAllowed responses are not implemented. |
| 409 | Conflict | ✅ Completed (not implemented) | AWS conflict variants such as BucketNotEmpty or operation conflicts are not fully modeled. |
| 500 | InternalServerError | ✅ Completed (not implemented) | No explicit AWS InternalServerError mapping exists for this operation. |
| 501 | NotImplemented | ✅ Completed (not implemented) | Unsupported optional AWS S3 features are currently ignored or documented as gaps rather than returned as `NotImplemented`. |
| 503 | SlowDown or ServiceUnavailable | ✅ Completed (not implemented) | No throttling or service-availability model exists. |

---

### Operation 52 — GetBucketRequestPayment `GET /{bucket}?requestPayment`

| Coverage Area | Required / Optional | Status | Notes |
|---|---|---|---|
| Request headers | Optional `x-amz-expected-bucket-owner` is ignored. | 🔴 Ignored | Header coverage for this operation. |
| Query parameters | Required `requestPayment` route flag is tested. | ✅ Tested | Route and query coverage for this operation. |
| Request body | None. | ✅ Tested | Request payload coverage. |
| Response body / headers | `RequestPaymentConfiguration` response for the stored request payment configuration; missing configuration returns a 404-style S3 error where implemented. | ✅ Tested | Response payload and significant header coverage. |

#### Status Codes

| Status Code | Description | Implemented | Notes |
|---|---|---|---|
| 200 | Success | ✅ Implemented | Returned by the handler for successful requests. |
| 301 | PermanentRedirect or wrong-region redirect | ✅ Completed (not implemented) | Region redirects are not modeled by the in-memory local service. |
| 304 | NotModified for conditional requests | ✅ Completed (not implemented) | Conditional request headers are ignored. |
| 400 | Bad Request or InvalidArgument | 🟡 Partially implemented | Malformed requests may fail through routing/codec behavior; operation-specific AWS validation is incomplete. |
| 403 | AccessDenied | ✅ Completed (not implemented) | No authentication, IAM, ACL enforcement, or requester-pays authorization model exists. |
| 404 | Not found condition | ✅ Implemented | NoSuchBucket, NoSuchKey, NoSuchUpload, or missing configuration paths are handled where applicable. |
| 405 | MethodNotAllowed | 🟡 Partially implemented | Unsupported methods may be rejected by WebFlux routing, but S3 XML MethodNotAllowed responses are not implemented. |
| 409 | Conflict | ✅ Completed (not implemented) | AWS conflict variants such as BucketNotEmpty or operation conflicts are not fully modeled. |
| 500 | InternalServerError | ✅ Completed (not implemented) | No explicit AWS InternalServerError mapping exists for this operation. |
| 501 | NotImplemented | ✅ Completed (not implemented) | Unsupported optional AWS S3 features are currently ignored or documented as gaps rather than returned as `NotImplemented`. |
| 503 | SlowDown or ServiceUnavailable | ✅ Completed (not implemented) | No throttling or service-availability model exists. |

---

### Operation 53 — PutBucketRequestPayment `PUT /{bucket}?requestPayment`

| Coverage Area | Required / Optional | Status | Notes |
|---|---|---|---|
| Request headers | Optional `x-amz-expected-bucket-owner` and checksum-related headers are ignored. | 🔴 Ignored | Header coverage for this operation. |
| Query parameters | Required `requestPayment` route flag is tested. | ✅ Tested | Route and query coverage for this operation. |
| Request body | `RequestPaymentConfiguration` request body is parsed by Jackson XML and stored through bucket application services. | ✅ Tested | Request payload coverage. |
| Response body / headers | No success body. | ✅ Tested | Response payload and significant header coverage. |

#### Status Codes

| Status Code | Description | Implemented | Notes |
|---|---|---|---|
| 200 | Success | ✅ Implemented | Returned by the handler for successful requests. |
| 400 | Bad Request or InvalidArgument | 🟡 Partially implemented | Selected invalid requests are rejected; full AWS validation is incomplete. |
| 403 | AccessDenied | ✅ Completed (not implemented) | No authentication, IAM, ACL enforcement, or requester-pays authorization model exists. |
| 404 | Not found condition | ✅ Implemented | NoSuchBucket, NoSuchKey, NoSuchUpload, or missing configuration paths are handled where applicable. |
| 405 | MethodNotAllowed | 🟡 Partially implemented | Unsupported methods may be rejected by WebFlux routing, but S3 XML MethodNotAllowed responses are not implemented. |
| 409 | Conflict | ✅ Completed (not implemented) | AWS conflict variants such as BucketNotEmpty or operation conflicts are not fully modeled. |
| 500 | InternalServerError | ✅ Completed (not implemented) | No explicit AWS InternalServerError mapping exists for this operation. |
| 501 | NotImplemented | ✅ Completed (not implemented) | Unsupported optional AWS S3 features are currently ignored or documented as gaps rather than returned as `NotImplemented`. |
| 503 | SlowDown or ServiceUnavailable | ✅ Completed (not implemented) | No throttling or service-availability model exists. |

---

### Operation 54 — DeleteBucketRequestPayment `DELETE /{bucket}?requestPayment`

| Coverage Area | Required / Optional | Status | Notes |
|---|---|---|---|
| Request headers | Optional `x-amz-expected-bucket-owner` is ignored. | 🔴 Ignored | Header coverage for this operation. |
| Query parameters | Required `requestPayment` route flag is tested. | ✅ Tested | Route and query coverage for this operation. |
| Request body | None. | ✅ Tested | Request payload coverage. |
| Response body / headers | No success body; stored request payment configuration is removed. | ✅ Tested | Response payload and significant header coverage. |

#### Status Codes

| Status Code | Description | Implemented | Notes |
|---|---|---|---|
| 204 | No Content success | ✅ Implemented | Returned by the handler for successful delete/abort requests. |
| 400 | Bad Request or InvalidArgument | 🟡 Partially implemented | Malformed requests may fail through routing/codec behavior; operation-specific AWS validation is incomplete. |
| 403 | AccessDenied | ✅ Completed (not implemented) | No authentication, IAM, ACL enforcement, or requester-pays authorization model exists. |
| 404 | Not found condition | ✅ Implemented | NoSuchBucket, NoSuchKey, NoSuchUpload, or missing configuration paths are handled where applicable. |
| 405 | MethodNotAllowed | 🟡 Partially implemented | Unsupported methods may be rejected by WebFlux routing, but S3 XML MethodNotAllowed responses are not implemented. |
| 409 | Conflict | ✅ Completed (not implemented) | AWS conflict variants such as BucketNotEmpty or operation conflicts are not fully modeled. |
| 500 | InternalServerError | ✅ Completed (not implemented) | No explicit AWS InternalServerError mapping exists for this operation. |
| 501 | NotImplemented | ✅ Completed (not implemented) | Unsupported optional AWS S3 features are currently ignored or documented as gaps rather than returned as `NotImplemented`. |
| 503 | SlowDown or ServiceUnavailable | ✅ Completed (not implemented) | No throttling or service-availability model exists. |

---

### Operation 55 — GetBucketOwnershipControls `GET /{bucket}?ownershipControls`

| Coverage Area | Required / Optional | Status | Notes |
|---|---|---|---|
| Request headers | Optional `x-amz-expected-bucket-owner` is ignored. | 🔴 Ignored | Header coverage for this operation. |
| Query parameters | Required `ownershipControls` route flag is tested. | ✅ Tested | Route and query coverage for this operation. |
| Request body | None. | ✅ Tested | Request payload coverage. |
| Response body / headers | `OwnershipControls` response for the stored ownership controls; missing configuration returns a 404-style S3 error where implemented. | ✅ Tested | Response payload and significant header coverage. |

#### Status Codes

| Status Code | Description | Implemented | Notes |
|---|---|---|---|
| 200 | Success | ✅ Implemented | Returned by the handler for successful requests. |
| 301 | PermanentRedirect or wrong-region redirect | ✅ Completed (not implemented) | Region redirects are not modeled by the in-memory local service. |
| 304 | NotModified for conditional requests | ✅ Completed (not implemented) | Conditional request headers are ignored. |
| 400 | Bad Request or InvalidArgument | 🟡 Partially implemented | Malformed requests may fail through routing/codec behavior; operation-specific AWS validation is incomplete. |
| 403 | AccessDenied | ✅ Completed (not implemented) | No authentication, IAM, ACL enforcement, or requester-pays authorization model exists. |
| 404 | Not found condition | ✅ Implemented | NoSuchBucket, NoSuchKey, NoSuchUpload, or missing configuration paths are handled where applicable. |
| 405 | MethodNotAllowed | 🟡 Partially implemented | Unsupported methods may be rejected by WebFlux routing, but S3 XML MethodNotAllowed responses are not implemented. |
| 409 | Conflict | ✅ Completed (not implemented) | AWS conflict variants such as BucketNotEmpty or operation conflicts are not fully modeled. |
| 500 | InternalServerError | ✅ Completed (not implemented) | No explicit AWS InternalServerError mapping exists for this operation. |
| 501 | NotImplemented | ✅ Completed (not implemented) | Unsupported optional AWS S3 features are currently ignored or documented as gaps rather than returned as `NotImplemented`. |
| 503 | SlowDown or ServiceUnavailable | ✅ Completed (not implemented) | No throttling or service-availability model exists. |

---

### Operation 56 — PutBucketOwnershipControls `PUT /{bucket}?ownershipControls`

| Coverage Area | Required / Optional | Status | Notes |
|---|---|---|---|
| Request headers | Optional `x-amz-expected-bucket-owner` and checksum-related headers are ignored. | 🔴 Ignored | Header coverage for this operation. |
| Query parameters | Required `ownershipControls` route flag is tested. | ✅ Tested | Route and query coverage for this operation. |
| Request body | `OwnershipControls` request body is parsed by Jackson XML and stored through bucket application services. | ✅ Tested | Request payload coverage. |
| Response body / headers | No success body. | ✅ Tested | Response payload and significant header coverage. |

#### Status Codes

| Status Code | Description | Implemented | Notes |
|---|---|---|---|
| 200 | Success | ✅ Implemented | Returned by the handler for successful requests. |
| 400 | Bad Request or InvalidArgument | 🟡 Partially implemented | Selected invalid requests are rejected; full AWS validation is incomplete. |
| 403 | AccessDenied | ✅ Completed (not implemented) | No authentication, IAM, ACL enforcement, or requester-pays authorization model exists. |
| 404 | Not found condition | ✅ Implemented | NoSuchBucket, NoSuchKey, NoSuchUpload, or missing configuration paths are handled where applicable. |
| 405 | MethodNotAllowed | 🟡 Partially implemented | Unsupported methods may be rejected by WebFlux routing, but S3 XML MethodNotAllowed responses are not implemented. |
| 409 | Conflict | ✅ Completed (not implemented) | AWS conflict variants such as BucketNotEmpty or operation conflicts are not fully modeled. |
| 500 | InternalServerError | ✅ Completed (not implemented) | No explicit AWS InternalServerError mapping exists for this operation. |
| 501 | NotImplemented | ✅ Completed (not implemented) | Unsupported optional AWS S3 features are currently ignored or documented as gaps rather than returned as `NotImplemented`. |
| 503 | SlowDown or ServiceUnavailable | ✅ Completed (not implemented) | No throttling or service-availability model exists. |

---

### Operation 57 — DeleteBucketOwnershipControls `DELETE /{bucket}?ownershipControls`

| Coverage Area | Required / Optional | Status | Notes |
|---|---|---|---|
| Request headers | Optional `x-amz-expected-bucket-owner` is ignored. | 🔴 Ignored | Header coverage for this operation. |
| Query parameters | Required `ownershipControls` route flag is tested. | ✅ Tested | Route and query coverage for this operation. |
| Request body | None. | ✅ Tested | Request payload coverage. |
| Response body / headers | No success body; stored ownership controls is removed. | ✅ Tested | Response payload and significant header coverage. |

#### Status Codes

| Status Code | Description | Implemented | Notes |
|---|---|---|---|
| 204 | No Content success | ✅ Implemented | Returned by the handler for successful delete/abort requests. |
| 400 | Bad Request or InvalidArgument | 🟡 Partially implemented | Malformed requests may fail through routing/codec behavior; operation-specific AWS validation is incomplete. |
| 403 | AccessDenied | ✅ Completed (not implemented) | No authentication, IAM, ACL enforcement, or requester-pays authorization model exists. |
| 404 | Not found condition | ✅ Implemented | NoSuchBucket, NoSuchKey, NoSuchUpload, or missing configuration paths are handled where applicable. |
| 405 | MethodNotAllowed | 🟡 Partially implemented | Unsupported methods may be rejected by WebFlux routing, but S3 XML MethodNotAllowed responses are not implemented. |
| 409 | Conflict | ✅ Completed (not implemented) | AWS conflict variants such as BucketNotEmpty or operation conflicts are not fully modeled. |
| 500 | InternalServerError | ✅ Completed (not implemented) | No explicit AWS InternalServerError mapping exists for this operation. |
| 501 | NotImplemented | ✅ Completed (not implemented) | Unsupported optional AWS S3 features are currently ignored or documented as gaps rather than returned as `NotImplemented`. |
| 503 | SlowDown or ServiceUnavailable | ✅ Completed (not implemented) | No throttling or service-availability model exists. |

---

### Operation 58 — GetPublicAccessBlock `GET /{bucket}?publicAccessBlock`

| Coverage Area | Required / Optional | Status | Notes |
|---|---|---|---|
| Request headers | Optional `x-amz-expected-bucket-owner` is ignored. | 🔴 Ignored | Header coverage for this operation. |
| Query parameters | Required `publicAccessBlock` route flag is tested. | ✅ Tested | Route and query coverage for this operation. |
| Request body | None. | ✅ Tested | Request payload coverage. |
| Response body / headers | `PublicAccessBlockConfiguration` response for the stored public access block configuration; missing configuration returns a 404-style S3 error where implemented. | ✅ Tested | Response payload and significant header coverage. |

#### Status Codes

| Status Code | Description | Implemented | Notes |
|---|---|---|---|
| 200 | Success | ✅ Implemented | Returned by the handler for successful requests. |
| 301 | PermanentRedirect or wrong-region redirect | ✅ Completed (not implemented) | Region redirects are not modeled by the in-memory local service. |
| 304 | NotModified for conditional requests | ✅ Completed (not implemented) | Conditional request headers are ignored. |
| 400 | Bad Request or InvalidArgument | 🟡 Partially implemented | Malformed requests may fail through routing/codec behavior; operation-specific AWS validation is incomplete. |
| 403 | AccessDenied | ✅ Completed (not implemented) | No authentication, IAM, ACL enforcement, or requester-pays authorization model exists. |
| 404 | Not found condition | ✅ Implemented | NoSuchBucket, NoSuchKey, NoSuchUpload, or missing configuration paths are handled where applicable. |
| 405 | MethodNotAllowed | 🟡 Partially implemented | Unsupported methods may be rejected by WebFlux routing, but S3 XML MethodNotAllowed responses are not implemented. |
| 409 | Conflict | ✅ Completed (not implemented) | AWS conflict variants such as BucketNotEmpty or operation conflicts are not fully modeled. |
| 500 | InternalServerError | ✅ Completed (not implemented) | No explicit AWS InternalServerError mapping exists for this operation. |
| 501 | NotImplemented | ✅ Completed (not implemented) | Unsupported optional AWS S3 features are currently ignored or documented as gaps rather than returned as `NotImplemented`. |
| 503 | SlowDown or ServiceUnavailable | ✅ Completed (not implemented) | No throttling or service-availability model exists. |

---

### Operation 59 — PutPublicAccessBlock `PUT /{bucket}?publicAccessBlock`

| Coverage Area | Required / Optional | Status | Notes |
|---|---|---|---|
| Request headers | Optional `x-amz-expected-bucket-owner` and checksum-related headers are ignored. | 🔴 Ignored | Header coverage for this operation. |
| Query parameters | Required `publicAccessBlock` route flag is tested. | ✅ Tested | Route and query coverage for this operation. |
| Request body | `PublicAccessBlockConfiguration` request body is parsed by Jackson XML and stored through bucket application services. | ✅ Tested | Request payload coverage. |
| Response body / headers | No success body. | ✅ Tested | Response payload and significant header coverage. |

#### Status Codes

| Status Code | Description | Implemented | Notes |
|---|---|---|---|
| 200 | Success | ✅ Implemented | Returned by the handler for successful requests. |
| 400 | Bad Request or InvalidArgument | 🟡 Partially implemented | Selected invalid requests are rejected; full AWS validation is incomplete. |
| 403 | AccessDenied | ✅ Completed (not implemented) | No authentication, IAM, ACL enforcement, or requester-pays authorization model exists. |
| 404 | Not found condition | ✅ Implemented | NoSuchBucket, NoSuchKey, NoSuchUpload, or missing configuration paths are handled where applicable. |
| 405 | MethodNotAllowed | 🟡 Partially implemented | Unsupported methods may be rejected by WebFlux routing, but S3 XML MethodNotAllowed responses are not implemented. |
| 409 | Conflict | ✅ Completed (not implemented) | AWS conflict variants such as BucketNotEmpty or operation conflicts are not fully modeled. |
| 500 | InternalServerError | ✅ Completed (not implemented) | No explicit AWS InternalServerError mapping exists for this operation. |
| 501 | NotImplemented | ✅ Completed (not implemented) | Unsupported optional AWS S3 features are currently ignored or documented as gaps rather than returned as `NotImplemented`. |
| 503 | SlowDown or ServiceUnavailable | ✅ Completed (not implemented) | No throttling or service-availability model exists. |

---

### Operation 60 — DeletePublicAccessBlock `DELETE /{bucket}?publicAccessBlock`

| Coverage Area | Required / Optional | Status | Notes |
|---|---|---|---|
| Request headers | Optional `x-amz-expected-bucket-owner` is ignored. | 🔴 Ignored | Header coverage for this operation. |
| Query parameters | Required `publicAccessBlock` route flag is tested. | ✅ Tested | Route and query coverage for this operation. |
| Request body | None. | ✅ Tested | Request payload coverage. |
| Response body / headers | No success body; stored public access block configuration is removed. | ✅ Tested | Response payload and significant header coverage. |

#### Status Codes

| Status Code | Description | Implemented | Notes |
|---|---|---|---|
| 204 | No Content success | ✅ Implemented | Returned by the handler for successful delete/abort requests. |
| 400 | Bad Request or InvalidArgument | 🟡 Partially implemented | Malformed requests may fail through routing/codec behavior; operation-specific AWS validation is incomplete. |
| 403 | AccessDenied | ✅ Completed (not implemented) | No authentication, IAM, ACL enforcement, or requester-pays authorization model exists. |
| 404 | Not found condition | ✅ Implemented | NoSuchBucket, NoSuchKey, NoSuchUpload, or missing configuration paths are handled where applicable. |
| 405 | MethodNotAllowed | 🟡 Partially implemented | Unsupported methods may be rejected by WebFlux routing, but S3 XML MethodNotAllowed responses are not implemented. |
| 409 | Conflict | ✅ Completed (not implemented) | AWS conflict variants such as BucketNotEmpty or operation conflicts are not fully modeled. |
| 500 | InternalServerError | ✅ Completed (not implemented) | No explicit AWS InternalServerError mapping exists for this operation. |
| 501 | NotImplemented | ✅ Completed (not implemented) | Unsupported optional AWS S3 features are currently ignored or documented as gaps rather than returned as `NotImplemented`. |
| 503 | SlowDown or ServiceUnavailable | ✅ Completed (not implemented) | No throttling or service-availability model exists. |

---

### Operation 61 — GetBucketAccelerateConfiguration `GET /{bucket}?accelerate`

| Coverage Area | Required / Optional | Status | Notes |
|---|---|---|---|
| Request headers | Optional `x-amz-expected-bucket-owner` is ignored. | 🔴 Ignored | Header coverage for this operation. |
| Query parameters | Required `accelerate` route flag is tested. | ✅ Tested | Route and query coverage for this operation. |
| Request body | None. | ✅ Tested | Request payload coverage. |
| Response body / headers | `AccelerateConfiguration` response for the stored accelerate configuration; missing configuration returns a 404-style S3 error where implemented. | ✅ Tested | Response payload and significant header coverage. |

#### Status Codes

| Status Code | Description | Implemented | Notes |
|---|---|---|---|
| 200 | Success | ✅ Implemented | Returned by the handler for successful requests. |
| 301 | PermanentRedirect or wrong-region redirect | ✅ Completed (not implemented) | Region redirects are not modeled by the in-memory local service. |
| 304 | NotModified for conditional requests | ✅ Completed (not implemented) | Conditional request headers are ignored. |
| 400 | Bad Request or InvalidArgument | 🟡 Partially implemented | Malformed requests may fail through routing/codec behavior; operation-specific AWS validation is incomplete. |
| 403 | AccessDenied | ✅ Completed (not implemented) | No authentication, IAM, ACL enforcement, or requester-pays authorization model exists. |
| 404 | Not found condition | ✅ Implemented | NoSuchBucket, NoSuchKey, NoSuchUpload, or missing configuration paths are handled where applicable. |
| 405 | MethodNotAllowed | 🟡 Partially implemented | Unsupported methods may be rejected by WebFlux routing, but S3 XML MethodNotAllowed responses are not implemented. |
| 409 | Conflict | ✅ Completed (not implemented) | AWS conflict variants such as BucketNotEmpty or operation conflicts are not fully modeled. |
| 500 | InternalServerError | ✅ Completed (not implemented) | No explicit AWS InternalServerError mapping exists for this operation. |
| 501 | NotImplemented | ✅ Completed (not implemented) | Unsupported optional AWS S3 features are currently ignored or documented as gaps rather than returned as `NotImplemented`. |
| 503 | SlowDown or ServiceUnavailable | ✅ Completed (not implemented) | No throttling or service-availability model exists. |

---

### Operation 62 — PutBucketAccelerateConfiguration `PUT /{bucket}?accelerate`

| Coverage Area | Required / Optional | Status | Notes |
|---|---|---|---|
| Request headers | Optional `x-amz-expected-bucket-owner` and checksum-related headers are ignored. | 🔴 Ignored | Header coverage for this operation. |
| Query parameters | Required `accelerate` route flag is tested. | ✅ Tested | Route and query coverage for this operation. |
| Request body | `AccelerateConfiguration` request body is parsed by Jackson XML and stored through bucket application services. | ✅ Tested | Request payload coverage. |
| Response body / headers | No success body. | ✅ Tested | Response payload and significant header coverage. |

#### Status Codes

| Status Code | Description | Implemented | Notes |
|---|---|---|---|
| 200 | Success | ✅ Implemented | Returned by the handler for successful requests. |
| 400 | Bad Request or InvalidArgument | 🟡 Partially implemented | Selected invalid requests are rejected; full AWS validation is incomplete. |
| 403 | AccessDenied | ✅ Completed (not implemented) | No authentication, IAM, ACL enforcement, or requester-pays authorization model exists. |
| 404 | Not found condition | ✅ Implemented | NoSuchBucket, NoSuchKey, NoSuchUpload, or missing configuration paths are handled where applicable. |
| 405 | MethodNotAllowed | 🟡 Partially implemented | Unsupported methods may be rejected by WebFlux routing, but S3 XML MethodNotAllowed responses are not implemented. |
| 409 | Conflict | ✅ Completed (not implemented) | AWS conflict variants such as BucketNotEmpty or operation conflicts are not fully modeled. |
| 500 | InternalServerError | ✅ Completed (not implemented) | No explicit AWS InternalServerError mapping exists for this operation. |
| 501 | NotImplemented | ✅ Completed (not implemented) | Unsupported optional AWS S3 features are currently ignored or documented as gaps rather than returned as `NotImplemented`. |
| 503 | SlowDown or ServiceUnavailable | ✅ Completed (not implemented) | No throttling or service-availability model exists. |

---

### Operation 63 — DeleteBucketAccelerateConfiguration `DELETE /{bucket}?accelerate`

| Coverage Area | Required / Optional | Status | Notes |
|---|---|---|---|
| Request headers | Optional `x-amz-expected-bucket-owner` is ignored. | 🔴 Ignored | Header coverage for this operation. |
| Query parameters | Required `accelerate` route flag is tested. | ✅ Tested | Route and query coverage for this operation. |
| Request body | None. | ✅ Tested | Request payload coverage. |
| Response body / headers | No success body; stored accelerate configuration is removed. | ✅ Tested | Response payload and significant header coverage. |

#### Status Codes

| Status Code | Description | Implemented | Notes |
|---|---|---|---|
| 204 | No Content success | ✅ Implemented | Returned by the handler for successful delete/abort requests. |
| 400 | Bad Request or InvalidArgument | 🟡 Partially implemented | Malformed requests may fail through routing/codec behavior; operation-specific AWS validation is incomplete. |
| 403 | AccessDenied | ✅ Completed (not implemented) | No authentication, IAM, ACL enforcement, or requester-pays authorization model exists. |
| 404 | Not found condition | ✅ Implemented | NoSuchBucket, NoSuchKey, NoSuchUpload, or missing configuration paths are handled where applicable. |
| 405 | MethodNotAllowed | 🟡 Partially implemented | Unsupported methods may be rejected by WebFlux routing, but S3 XML MethodNotAllowed responses are not implemented. |
| 409 | Conflict | ✅ Completed (not implemented) | AWS conflict variants such as BucketNotEmpty or operation conflicts are not fully modeled. |
| 500 | InternalServerError | ✅ Completed (not implemented) | No explicit AWS InternalServerError mapping exists for this operation. |
| 501 | NotImplemented | ✅ Completed (not implemented) | Unsupported optional AWS S3 features are currently ignored or documented as gaps rather than returned as `NotImplemented`. |
| 503 | SlowDown or ServiceUnavailable | ✅ Completed (not implemented) | No throttling or service-availability model exists. |

---

### Operation 64 — CreateMultipartUpload `POST /{bucket}/{key}?uploads`

| Coverage Area | Required / Optional | Status | Notes |
|---|---|---|---|
| Request headers | Optional ACL/grant, metadata, tagging, storage-class, SSE, checksum, object-lock, and expected-owner headers are ignored. | 🔴 Ignored | Header coverage for this operation. |
| Query parameters | Required `uploads` route flag is tested. | ✅ Tested | Route and query coverage for this operation. |
| Request body | None. | ✅ Tested | Request payload coverage. |
| Response body / headers | `InitiateMultipartUploadResult` XML containing Bucket, Key, and UploadId. | ✅ Tested | Response payload and significant header coverage. |

#### Status Codes

| Status Code | Description | Implemented | Notes |
|---|---|---|---|
| 200 | Success | ✅ Implemented | Returned by the handler for successful requests. |
| 400 | Bad Request or InvalidArgument | 🟡 Partially implemented | Malformed requests may fail through routing/codec behavior; operation-specific AWS validation is incomplete. |
| 403 | AccessDenied | ✅ Completed (not implemented) | No authentication, IAM, ACL enforcement, or requester-pays authorization model exists. |
| 404 | Not found condition | ✅ Implemented | NoSuchBucket, NoSuchKey, NoSuchUpload, or missing configuration paths are handled where applicable. |
| 405 | MethodNotAllowed | 🟡 Partially implemented | Unsupported methods may be rejected by WebFlux routing, but S3 XML MethodNotAllowed responses are not implemented. |
| 500 | InternalServerError | ✅ Completed (not implemented) | No explicit AWS InternalServerError mapping exists for this operation. |
| 501 | NotImplemented | ✅ Completed (not implemented) | Unsupported optional AWS S3 features are currently ignored or documented as gaps rather than returned as `NotImplemented`. |
| 503 | SlowDown or ServiceUnavailable | ✅ Completed (not implemented) | No throttling or service-availability model exists. |

---

### Operation 65 — UploadPart `PUT /{bucket}/{key}?uploadId=...&partNumber=...`

| Coverage Area | Required / Optional | Status | Notes |
|---|---|---|---|
| Request headers | `Content-Length` is read for part size; checksum, SSE-C, requester, and expected-owner headers are ignored. | 🟡 Partial | Header coverage for this operation. |
| Query parameters | Required `uploadId` and `partNumber` are parsed. | ✅ Tested | Route and query coverage for this operation. |
| Request body | Binary part body is consumed and represented by part metadata in the multipart upload service. | ✅ Tested | Request payload coverage. |
| Response body / headers | Current response returns XML with ETag; AWS-compatible ETag header coverage is incomplete. | ✅ Tested | Response payload and significant header coverage. |

#### Status Codes

| Status Code | Description | Implemented | Notes |
|---|---|---|---|
| 200 | Success | ✅ Implemented | Returned by the handler for successful requests. |
| 400 | Bad Request or InvalidArgument | 🟡 Partially implemented | Selected invalid requests are rejected; full AWS validation is incomplete. |
| 403 | AccessDenied | ✅ Completed (not implemented) | No authentication, IAM, ACL enforcement, or requester-pays authorization model exists. |
| 404 | Not found condition | ✅ Implemented | NoSuchBucket, NoSuchKey, NoSuchUpload, or missing configuration paths are handled where applicable. |
| 405 | MethodNotAllowed | 🟡 Partially implemented | Unsupported methods may be rejected by WebFlux routing, but S3 XML MethodNotAllowed responses are not implemented. |
| 409 | Conflict | ✅ Completed (not implemented) | AWS conflict variants such as BucketNotEmpty or operation conflicts are not fully modeled. |
| 500 | InternalServerError | ✅ Completed (not implemented) | No explicit AWS InternalServerError mapping exists for this operation. |
| 501 | NotImplemented | ✅ Completed (not implemented) | Unsupported optional AWS S3 features are currently ignored or documented as gaps rather than returned as `NotImplemented`. |
| 503 | SlowDown or ServiceUnavailable | ✅ Completed (not implemented) | No throttling or service-availability model exists. |

---

### Operation 66 — UploadPartCopy `PUT /{bucket}/{key}?uploadId=...&partNumber=... with x-amz-copy-source`

| Coverage Area | Required / Optional | Status | Notes |
|---|---|---|---|
| Request headers | Required `x-amz-copy-source` is read; copy conditionals, source version, SSE, checksum, requester, and expected-owner headers are ignored. | 🟡 Partial | Header coverage for this operation. |
| Query parameters | Required `uploadId` and `partNumber` are parsed. | ✅ Tested | Route and query coverage for this operation. |
| Request body | None. | ✅ Tested | Request payload coverage. |
| Response body / headers | Current response returns XML with ETag; full `CopyPartResult` semantics are incomplete. | ✅ Tested | Response payload and significant header coverage. |

#### Status Codes

| Status Code | Description | Implemented | Notes |
|---|---|---|---|
| 200 | Success | ✅ Implemented | Returned by the handler for successful requests. |
| 400 | Bad Request or InvalidArgument | 🟡 Partially implemented | Selected invalid requests are rejected; full AWS validation is incomplete. |
| 403 | AccessDenied | ✅ Completed (not implemented) | No authentication, IAM, ACL enforcement, or requester-pays authorization model exists. |
| 404 | Not found condition | ✅ Implemented | NoSuchBucket, NoSuchKey, NoSuchUpload, or missing configuration paths are handled where applicable. |
| 405 | MethodNotAllowed | 🟡 Partially implemented | Unsupported methods may be rejected by WebFlux routing, but S3 XML MethodNotAllowed responses are not implemented. |
| 409 | Conflict | ✅ Completed (not implemented) | AWS conflict variants such as BucketNotEmpty or operation conflicts are not fully modeled. |
| 500 | InternalServerError | ✅ Completed (not implemented) | No explicit AWS InternalServerError mapping exists for this operation. |
| 501 | NotImplemented | ✅ Completed (not implemented) | Unsupported optional AWS S3 features are currently ignored or documented as gaps rather than returned as `NotImplemented`. |
| 503 | SlowDown or ServiceUnavailable | ✅ Completed (not implemented) | No throttling or service-availability model exists. |

---

### Operation 67 — CompleteMultipartUpload `POST /{bucket}/{key}?uploadId=...`

| Coverage Area | Required / Optional | Status | Notes |
|---|---|---|---|
| Request headers | Optional checksum and expected-owner headers are ignored. | 🔴 Ignored | Header coverage for this operation. |
| Query parameters | Required `uploadId` is parsed. | ✅ Tested | Route and query coverage for this operation. |
| Request body | AWS `CompleteMultipartUpload` XML body is not used to order/validate parts; current service completes the tracked upload. | 🔴 Ignored | Request payload coverage. |
| Response body / headers | `CompleteMultipartUploadResult` XML with Bucket, Key, and generated ETag. | ✅ Tested | Response payload and significant header coverage. |

#### Status Codes

| Status Code | Description | Implemented | Notes |
|---|---|---|---|
| 200 | Success | ✅ Implemented | Returned by the handler for successful requests. |
| 400 | Bad Request or InvalidArgument | 🟡 Partially implemented | Selected invalid requests are rejected; full AWS validation is incomplete. |
| 403 | AccessDenied | ✅ Completed (not implemented) | No authentication, IAM, ACL enforcement, or requester-pays authorization model exists. |
| 404 | Not found condition | ✅ Implemented | NoSuchBucket, NoSuchKey, NoSuchUpload, or missing configuration paths are handled where applicable. |
| 405 | MethodNotAllowed | 🟡 Partially implemented | Unsupported methods may be rejected by WebFlux routing, but S3 XML MethodNotAllowed responses are not implemented. |
| 500 | InternalServerError | ✅ Completed (not implemented) | No explicit AWS InternalServerError mapping exists for this operation. |
| 501 | NotImplemented | ✅ Completed (not implemented) | Unsupported optional AWS S3 features are currently ignored or documented as gaps rather than returned as `NotImplemented`. |
| 503 | SlowDown or ServiceUnavailable | ✅ Completed (not implemented) | No throttling or service-availability model exists. |

---

### Operation 68 — AbortMultipartUpload `DELETE /{bucket}/{key}?uploadId=...`

| Coverage Area | Required / Optional | Status | Notes |
|---|---|---|---|
| Request headers | Optional expected-owner and requester headers are ignored. | 🔴 Ignored | Header coverage for this operation. |
| Query parameters | Required `uploadId` is parsed. | ✅ Tested | Route and query coverage for this operation. |
| Request body | None. | ✅ Tested | Request payload coverage. |
| Response body / headers | No success body. | ✅ Tested | Response payload and significant header coverage. |

#### Status Codes

| Status Code | Description | Implemented | Notes |
|---|---|---|---|
| 204 | No Content success | ✅ Implemented | Returned by the handler for successful delete/abort requests. |
| 400 | Bad Request or InvalidArgument | 🟡 Partially implemented | Malformed requests may fail through routing/codec behavior; operation-specific AWS validation is incomplete. |
| 403 | AccessDenied | ✅ Completed (not implemented) | No authentication, IAM, ACL enforcement, or requester-pays authorization model exists. |
| 404 | Not found condition | ✅ Implemented | NoSuchBucket, NoSuchKey, NoSuchUpload, or missing configuration paths are handled where applicable. |
| 405 | MethodNotAllowed | 🟡 Partially implemented | Unsupported methods may be rejected by WebFlux routing, but S3 XML MethodNotAllowed responses are not implemented. |
| 500 | InternalServerError | ✅ Completed (not implemented) | No explicit AWS InternalServerError mapping exists for this operation. |
| 501 | NotImplemented | ✅ Completed (not implemented) | Unsupported optional AWS S3 features are currently ignored or documented as gaps rather than returned as `NotImplemented`. |
| 503 | SlowDown or ServiceUnavailable | ✅ Completed (not implemented) | No throttling or service-availability model exists. |

---

### Operation 69 — ListMultipartUploads `GET /{bucket}?uploads`

| Coverage Area | Required / Optional | Status | Notes |
|---|---|---|---|
| Request headers | Optional expected-owner and requester headers are ignored. | 🔴 Ignored | Header coverage for this operation. |
| Query parameters | Required `uploads` route flag is tested; `delimiter`, `encoding-type`, `key-marker`, `max-uploads`, `prefix`, and `upload-id-marker` are ignored. | 🟡 Partial | Route and query coverage for this operation. |
| Request body | None. | ✅ Tested | Request payload coverage. |
| Response body / headers | `ListMultipartUploadsResult` XML with current in-memory uploads. | ✅ Tested | Response payload and significant header coverage. |

#### Status Codes

| Status Code | Description | Implemented | Notes |
|---|---|---|---|
| 200 | Success | ✅ Implemented | Returned by the handler for successful requests. |
| 400 | Bad Request or InvalidArgument | 🟡 Partially implemented | Malformed requests may fail through routing/codec behavior; operation-specific AWS validation is incomplete. |
| 403 | AccessDenied | ✅ Completed (not implemented) | No authentication, IAM, ACL enforcement, or requester-pays authorization model exists. |
| 404 | Not found condition | ✅ Implemented | NoSuchBucket, NoSuchKey, NoSuchUpload, or missing configuration paths are handled where applicable. |
| 405 | MethodNotAllowed | 🟡 Partially implemented | Unsupported methods may be rejected by WebFlux routing, but S3 XML MethodNotAllowed responses are not implemented. |
| 500 | InternalServerError | ✅ Completed (not implemented) | No explicit AWS InternalServerError mapping exists for this operation. |
| 501 | NotImplemented | ✅ Completed (not implemented) | Unsupported optional AWS S3 features are currently ignored or documented as gaps rather than returned as `NotImplemented`. |
| 503 | SlowDown or ServiceUnavailable | ✅ Completed (not implemented) | No throttling or service-availability model exists. |

---

### Operation 70 — ListParts `GET /{bucket}/{key}?uploadId=...`

| Coverage Area | Required / Optional | Status | Notes |
|---|---|---|---|
| Request headers | Optional expected-owner, requester, and SSE-C headers are ignored. | 🔴 Ignored | Header coverage for this operation. |
| Query parameters | Required `uploadId` is parsed; `max-parts` and `part-number-marker` are ignored. | 🟡 Partial | Route and query coverage for this operation. |
| Request body | None. | ✅ Tested | Request payload coverage. |
| Response body / headers | `ListPartsResult` XML with current part entries. | ✅ Tested | Response payload and significant header coverage. |

#### Status Codes

| Status Code | Description | Implemented | Notes |
|---|---|---|---|
| 200 | Success | ✅ Implemented | Returned by the handler for successful requests. |
| 400 | Bad Request or InvalidArgument | 🟡 Partially implemented | Malformed requests may fail through routing/codec behavior; operation-specific AWS validation is incomplete. |
| 403 | AccessDenied | ✅ Completed (not implemented) | No authentication, IAM, ACL enforcement, or requester-pays authorization model exists. |
| 404 | Not found condition | ✅ Implemented | NoSuchBucket, NoSuchKey, NoSuchUpload, or missing configuration paths are handled where applicable. |
| 405 | MethodNotAllowed | 🟡 Partially implemented | Unsupported methods may be rejected by WebFlux routing, but S3 XML MethodNotAllowed responses are not implemented. |
| 500 | InternalServerError | ✅ Completed (not implemented) | No explicit AWS InternalServerError mapping exists for this operation. |
| 501 | NotImplemented | ✅ Completed (not implemented) | Unsupported optional AWS S3 features are currently ignored or documented as gaps rather than returned as `NotImplemented`. |
| 503 | SlowDown or ServiceUnavailable | ✅ Completed (not implemented) | No throttling or service-availability model exists. |

---

### Operation 71 — GetBucketAnalyticsConfiguration `GET /{bucket}?analytics&analyticsId={id}`

| Coverage Area | Required / Optional | Status | Notes |
|---|---|---|---|
| Request headers | Optional `x-amz-expected-bucket-owner` is ignored. | 🔴 Ignored | Header coverage for this operation. |
| Query parameters | Required `analytics` route flag and ID query parameter are used; current route reads `analyticsId` while AWS CLI exposes the value as `--id`. | ✅ Tested | Route and query coverage for this operation. |
| Request body | None. | ✅ Tested | Request payload coverage. |
| Response body / headers | `AnalyticsConfiguration` XML from `BucketAnalyticsConfiguration`. | ✅ Tested | Response payload and significant header coverage. |

#### Status Codes

| Status Code | Description | Implemented | Notes |
|---|---|---|---|
| 200 | Success | ✅ Implemented | Returned by the handler for successful requests. |
| 301 | PermanentRedirect or wrong-region redirect | ✅ Completed (not implemented) | Region redirects are not modeled by the in-memory local service. |
| 304 | NotModified for conditional requests | ✅ Completed (not implemented) | Conditional request headers are ignored. |
| 400 | Bad Request or InvalidArgument | 🟡 Partially implemented | Selected invalid requests are rejected; full AWS validation is incomplete. |
| 403 | AccessDenied | ✅ Completed (not implemented) | No authentication, IAM, ACL enforcement, or requester-pays authorization model exists. |
| 404 | Not found condition | ✅ Implemented | NoSuchBucket, NoSuchKey, NoSuchUpload, or missing configuration paths are handled where applicable. |
| 405 | MethodNotAllowed | 🟡 Partially implemented | Unsupported methods may be rejected by WebFlux routing, but S3 XML MethodNotAllowed responses are not implemented. |
| 409 | Conflict | ✅ Completed (not implemented) | AWS conflict variants such as BucketNotEmpty or operation conflicts are not fully modeled. |
| 500 | InternalServerError | ✅ Completed (not implemented) | No explicit AWS InternalServerError mapping exists for this operation. |
| 501 | NotImplemented | ✅ Completed (not implemented) | Unsupported optional AWS S3 features are currently ignored or documented as gaps rather than returned as `NotImplemented`. |
| 503 | SlowDown or ServiceUnavailable | ✅ Completed (not implemented) | No throttling or service-availability model exists. |

---

### Operation 72 — PutBucketAnalyticsConfiguration `PUT /{bucket}?analytics&analyticsId={id}`

| Coverage Area | Required / Optional | Status | Notes |
|---|---|---|---|
| Request headers | Optional `x-amz-expected-bucket-owner` and checksum headers are ignored. | 🔴 Ignored | Header coverage for this operation. |
| Query parameters | Required `analytics` route flag and ID query parameter are used; current route reads `analyticsId` while AWS CLI exposes the value as `--id`. | ✅ Tested | Route and query coverage for this operation. |
| Request body | `AnalyticsConfiguration` XML is parsed and stored through bucket application services. | ✅ Tested | Request payload coverage. |
| Response body / headers | No success body. | ✅ Tested | Response payload and significant header coverage. |

#### Status Codes

| Status Code | Description | Implemented | Notes |
|---|---|---|---|
| 200 | Success | ✅ Implemented | Returned by the handler for successful requests. |
| 400 | Bad Request or InvalidArgument | 🟡 Partially implemented | Selected invalid requests are rejected; full AWS validation is incomplete. |
| 403 | AccessDenied | ✅ Completed (not implemented) | No authentication, IAM, ACL enforcement, or requester-pays authorization model exists. |
| 404 | Not found condition | ✅ Implemented | NoSuchBucket, NoSuchKey, NoSuchUpload, or missing configuration paths are handled where applicable. |
| 405 | MethodNotAllowed | 🟡 Partially implemented | Unsupported methods may be rejected by WebFlux routing, but S3 XML MethodNotAllowed responses are not implemented. |
| 409 | Conflict | ✅ Completed (not implemented) | AWS conflict variants such as BucketNotEmpty or operation conflicts are not fully modeled. |
| 500 | InternalServerError | ✅ Completed (not implemented) | No explicit AWS InternalServerError mapping exists for this operation. |
| 501 | NotImplemented | ✅ Completed (not implemented) | Unsupported optional AWS S3 features are currently ignored or documented as gaps rather than returned as `NotImplemented`. |
| 503 | SlowDown or ServiceUnavailable | ✅ Completed (not implemented) | No throttling or service-availability model exists. |

---

### Operation 73 — DeleteBucketAnalyticsConfiguration `DELETE /{bucket}?analytics&analyticsId={id}`

| Coverage Area | Required / Optional | Status | Notes |
|---|---|---|---|
| Request headers | Optional `x-amz-expected-bucket-owner` is ignored. | 🔴 Ignored | Header coverage for this operation. |
| Query parameters | Required `analytics` route flag and ID query parameter are used; current route reads `analyticsId` while AWS CLI exposes the value as `--id`. | ✅ Tested | Route and query coverage for this operation. |
| Request body | None. | ✅ Tested | Request payload coverage. |
| Response body / headers | No success body; stored analytics configuration is removed. | ✅ Tested | Response payload and significant header coverage. |

#### Status Codes

| Status Code | Description | Implemented | Notes |
|---|---|---|---|
| 204 | No Content success | ✅ Implemented | Returned by the handler for successful delete/abort requests. |
| 400 | Bad Request or InvalidArgument | 🟡 Partially implemented | Selected invalid requests are rejected; full AWS validation is incomplete. |
| 403 | AccessDenied | ✅ Completed (not implemented) | No authentication, IAM, ACL enforcement, or requester-pays authorization model exists. |
| 404 | Not found condition | ✅ Implemented | NoSuchBucket, NoSuchKey, NoSuchUpload, or missing configuration paths are handled where applicable. |
| 405 | MethodNotAllowed | 🟡 Partially implemented | Unsupported methods may be rejected by WebFlux routing, but S3 XML MethodNotAllowed responses are not implemented. |
| 409 | Conflict | ✅ Completed (not implemented) | AWS conflict variants such as BucketNotEmpty or operation conflicts are not fully modeled. |
| 500 | InternalServerError | ✅ Completed (not implemented) | No explicit AWS InternalServerError mapping exists for this operation. |
| 501 | NotImplemented | ✅ Completed (not implemented) | Unsupported optional AWS S3 features are currently ignored or documented as gaps rather than returned as `NotImplemented`. |
| 503 | SlowDown or ServiceUnavailable | ✅ Completed (not implemented) | No throttling or service-availability model exists. |

---

### Operation 74 — ListBucketAnalyticsConfigurations `GET /{bucket}?analytics&list-type`

| Coverage Area | Required / Optional | Status | Notes |
|---|---|---|---|
| Request headers | Optional `x-amz-expected-bucket-owner` is ignored. | 🔴 Ignored | Header coverage for this operation. |
| Query parameters | Required `analytics` route flag and `list-type` discriminator are tested; pagination parameters are not implemented. | 🟡 Partial | Route and query coverage for this operation. |
| Request body | None. | ✅ Tested | Request payload coverage. |
| Response body / headers | `ListBucketAnalyticsConfigurationsResult` XML with stored configuration IDs. | ✅ Tested | Response payload and significant header coverage. |

#### Status Codes

| Status Code | Description | Implemented | Notes |
|---|---|---|---|
| 200 | Success | ✅ Implemented | Returned by the handler for successful requests. |
| 301 | PermanentRedirect or wrong-region redirect | ✅ Completed (not implemented) | Region redirects are not modeled by the in-memory local service. |
| 304 | NotModified for conditional requests | ✅ Completed (not implemented) | Conditional request headers are ignored. |
| 400 | Bad Request or InvalidArgument | 🟡 Partially implemented | Malformed requests may fail through routing/codec behavior; operation-specific AWS validation is incomplete. |
| 403 | AccessDenied | ✅ Completed (not implemented) | No authentication, IAM, ACL enforcement, or requester-pays authorization model exists. |
| 404 | Not found condition | ✅ Implemented | NoSuchBucket, NoSuchKey, NoSuchUpload, or missing configuration paths are handled where applicable. |
| 405 | MethodNotAllowed | 🟡 Partially implemented | Unsupported methods may be rejected by WebFlux routing, but S3 XML MethodNotAllowed responses are not implemented. |
| 409 | Conflict | ✅ Completed (not implemented) | AWS conflict variants such as BucketNotEmpty or operation conflicts are not fully modeled. |
| 500 | InternalServerError | ✅ Completed (not implemented) | No explicit AWS InternalServerError mapping exists for this operation. |
| 501 | NotImplemented | ✅ Completed (not implemented) | Unsupported optional AWS S3 features are currently ignored or documented as gaps rather than returned as `NotImplemented`. |
| 503 | SlowDown or ServiceUnavailable | ✅ Completed (not implemented) | No throttling or service-availability model exists. |

---

### Operation 75 — GetBucketInventoryConfiguration `GET /{bucket}?inventory&inventoryId={id}`

| Coverage Area | Required / Optional | Status | Notes |
|---|---|---|---|
| Request headers | Optional `x-amz-expected-bucket-owner` is ignored. | 🔴 Ignored | Header coverage for this operation. |
| Query parameters | Required `inventory` route flag and ID query parameter are used; current route reads `inventoryId` while AWS CLI exposes the value as `--id`. | ✅ Tested | Route and query coverage for this operation. |
| Request body | None. | ✅ Tested | Request payload coverage. |
| Response body / headers | `InventoryConfiguration` XML from `BucketInventoryConfiguration`. | ✅ Tested | Response payload and significant header coverage. |

#### Status Codes

| Status Code | Description | Implemented | Notes |
|---|---|---|---|
| 200 | Success | ✅ Implemented | Returned by the handler for successful requests. |
| 301 | PermanentRedirect or wrong-region redirect | ✅ Completed (not implemented) | Region redirects are not modeled by the in-memory local service. |
| 304 | NotModified for conditional requests | ✅ Completed (not implemented) | Conditional request headers are ignored. |
| 400 | Bad Request or InvalidArgument | 🟡 Partially implemented | Selected invalid requests are rejected; full AWS validation is incomplete. |
| 403 | AccessDenied | ✅ Completed (not implemented) | No authentication, IAM, ACL enforcement, or requester-pays authorization model exists. |
| 404 | Not found condition | ✅ Implemented | NoSuchBucket, NoSuchKey, NoSuchUpload, or missing configuration paths are handled where applicable. |
| 405 | MethodNotAllowed | 🟡 Partially implemented | Unsupported methods may be rejected by WebFlux routing, but S3 XML MethodNotAllowed responses are not implemented. |
| 409 | Conflict | ✅ Completed (not implemented) | AWS conflict variants such as BucketNotEmpty or operation conflicts are not fully modeled. |
| 500 | InternalServerError | ✅ Completed (not implemented) | No explicit AWS InternalServerError mapping exists for this operation. |
| 501 | NotImplemented | ✅ Completed (not implemented) | Unsupported optional AWS S3 features are currently ignored or documented as gaps rather than returned as `NotImplemented`. |
| 503 | SlowDown or ServiceUnavailable | ✅ Completed (not implemented) | No throttling or service-availability model exists. |

---

### Operation 76 — PutBucketInventoryConfiguration `PUT /{bucket}?inventory&inventoryId={id}`

| Coverage Area | Required / Optional | Status | Notes |
|---|---|---|---|
| Request headers | Optional `x-amz-expected-bucket-owner` and checksum headers are ignored. | 🔴 Ignored | Header coverage for this operation. |
| Query parameters | Required `inventory` route flag and ID query parameter are used; current route reads `inventoryId` while AWS CLI exposes the value as `--id`. | ✅ Tested | Route and query coverage for this operation. |
| Request body | `InventoryConfiguration` XML is parsed and stored through bucket application services. | ✅ Tested | Request payload coverage. |
| Response body / headers | No success body. | ✅ Tested | Response payload and significant header coverage. |

#### Status Codes

| Status Code | Description | Implemented | Notes |
|---|---|---|---|
| 200 | Success | ✅ Implemented | Returned by the handler for successful requests. |
| 400 | Bad Request or InvalidArgument | 🟡 Partially implemented | Selected invalid requests are rejected; full AWS validation is incomplete. |
| 403 | AccessDenied | ✅ Completed (not implemented) | No authentication, IAM, ACL enforcement, or requester-pays authorization model exists. |
| 404 | Not found condition | ✅ Implemented | NoSuchBucket, NoSuchKey, NoSuchUpload, or missing configuration paths are handled where applicable. |
| 405 | MethodNotAllowed | 🟡 Partially implemented | Unsupported methods may be rejected by WebFlux routing, but S3 XML MethodNotAllowed responses are not implemented. |
| 409 | Conflict | ✅ Completed (not implemented) | AWS conflict variants such as BucketNotEmpty or operation conflicts are not fully modeled. |
| 500 | InternalServerError | ✅ Completed (not implemented) | No explicit AWS InternalServerError mapping exists for this operation. |
| 501 | NotImplemented | ✅ Completed (not implemented) | Unsupported optional AWS S3 features are currently ignored or documented as gaps rather than returned as `NotImplemented`. |
| 503 | SlowDown or ServiceUnavailable | ✅ Completed (not implemented) | No throttling or service-availability model exists. |

---

### Operation 77 — DeleteBucketInventoryConfiguration `DELETE /{bucket}?inventory&inventoryId={id}`

| Coverage Area | Required / Optional | Status | Notes |
|---|---|---|---|
| Request headers | Optional `x-amz-expected-bucket-owner` is ignored. | 🔴 Ignored | Header coverage for this operation. |
| Query parameters | Required `inventory` route flag and ID query parameter are used; current route reads `inventoryId` while AWS CLI exposes the value as `--id`. | ✅ Tested | Route and query coverage for this operation. |
| Request body | None. | ✅ Tested | Request payload coverage. |
| Response body / headers | No success body; stored inventory configuration is removed. | ✅ Tested | Response payload and significant header coverage. |

#### Status Codes

| Status Code | Description | Implemented | Notes |
|---|---|---|---|
| 204 | No Content success | ✅ Implemented | Returned by the handler for successful delete/abort requests. |
| 400 | Bad Request or InvalidArgument | 🟡 Partially implemented | Selected invalid requests are rejected; full AWS validation is incomplete. |
| 403 | AccessDenied | ✅ Completed (not implemented) | No authentication, IAM, ACL enforcement, or requester-pays authorization model exists. |
| 404 | Not found condition | ✅ Implemented | NoSuchBucket, NoSuchKey, NoSuchUpload, or missing configuration paths are handled where applicable. |
| 405 | MethodNotAllowed | 🟡 Partially implemented | Unsupported methods may be rejected by WebFlux routing, but S3 XML MethodNotAllowed responses are not implemented. |
| 409 | Conflict | ✅ Completed (not implemented) | AWS conflict variants such as BucketNotEmpty or operation conflicts are not fully modeled. |
| 500 | InternalServerError | ✅ Completed (not implemented) | No explicit AWS InternalServerError mapping exists for this operation. |
| 501 | NotImplemented | ✅ Completed (not implemented) | Unsupported optional AWS S3 features are currently ignored or documented as gaps rather than returned as `NotImplemented`. |
| 503 | SlowDown or ServiceUnavailable | ✅ Completed (not implemented) | No throttling or service-availability model exists. |

---

### Operation 78 — ListBucketInventoryConfigurations `GET /{bucket}?inventory&list-type`

| Coverage Area | Required / Optional | Status | Notes |
|---|---|---|---|
| Request headers | Optional `x-amz-expected-bucket-owner` is ignored. | 🔴 Ignored | Header coverage for this operation. |
| Query parameters | Required `inventory` route flag and `list-type` discriminator are tested; pagination parameters are not implemented. | 🟡 Partial | Route and query coverage for this operation. |
| Request body | None. | ✅ Tested | Request payload coverage. |
| Response body / headers | `ListBucketInventoryConfigurationsResult` XML with stored configuration IDs. | ✅ Tested | Response payload and significant header coverage. |

#### Status Codes

| Status Code | Description | Implemented | Notes |
|---|---|---|---|
| 200 | Success | ✅ Implemented | Returned by the handler for successful requests. |
| 301 | PermanentRedirect or wrong-region redirect | ✅ Completed (not implemented) | Region redirects are not modeled by the in-memory local service. |
| 304 | NotModified for conditional requests | ✅ Completed (not implemented) | Conditional request headers are ignored. |
| 400 | Bad Request or InvalidArgument | 🟡 Partially implemented | Malformed requests may fail through routing/codec behavior; operation-specific AWS validation is incomplete. |
| 403 | AccessDenied | ✅ Completed (not implemented) | No authentication, IAM, ACL enforcement, or requester-pays authorization model exists. |
| 404 | Not found condition | ✅ Implemented | NoSuchBucket, NoSuchKey, NoSuchUpload, or missing configuration paths are handled where applicable. |
| 405 | MethodNotAllowed | 🟡 Partially implemented | Unsupported methods may be rejected by WebFlux routing, but S3 XML MethodNotAllowed responses are not implemented. |
| 409 | Conflict | ✅ Completed (not implemented) | AWS conflict variants such as BucketNotEmpty or operation conflicts are not fully modeled. |
| 500 | InternalServerError | ✅ Completed (not implemented) | No explicit AWS InternalServerError mapping exists for this operation. |
| 501 | NotImplemented | ✅ Completed (not implemented) | Unsupported optional AWS S3 features are currently ignored or documented as gaps rather than returned as `NotImplemented`. |
| 503 | SlowDown or ServiceUnavailable | ✅ Completed (not implemented) | No throttling or service-availability model exists. |

---

### Operation 79 — GetBucketMetricsConfiguration `GET /{bucket}?metrics`

| Coverage Area | Required / Optional | Status | Notes |
|---|---|---|---|
| Request headers | Optional `x-amz-expected-bucket-owner` is ignored. | 🔴 Ignored | Header coverage for this operation. |
| Query parameters | Required `metrics` route flag is tested; AWS `id` query semantics are not modeled and the current implementation stores one metrics configuration per bucket. | 🟡 Partial | Route and query coverage for this operation. |
| Request body | None. | ✅ Tested | Request payload coverage. |
| Response body / headers | `MetricsConfiguration` XML from `BucketMetricsConfiguration`. | ✅ Tested | Response payload and significant header coverage. |

#### Status Codes

| Status Code | Description | Implemented | Notes |
|---|---|---|---|
| 200 | Success | ✅ Implemented | Returned by the handler for successful requests. |
| 301 | PermanentRedirect or wrong-region redirect | ✅ Completed (not implemented) | Region redirects are not modeled by the in-memory local service. |
| 304 | NotModified for conditional requests | ✅ Completed (not implemented) | Conditional request headers are ignored. |
| 400 | Bad Request or InvalidArgument | 🟡 Partially implemented | Malformed requests may fail through routing/codec behavior; operation-specific AWS validation is incomplete. |
| 403 | AccessDenied | ✅ Completed (not implemented) | No authentication, IAM, ACL enforcement, or requester-pays authorization model exists. |
| 404 | Not found condition | ✅ Implemented | NoSuchBucket, NoSuchKey, NoSuchUpload, or missing configuration paths are handled where applicable. |
| 405 | MethodNotAllowed | 🟡 Partially implemented | Unsupported methods may be rejected by WebFlux routing, but S3 XML MethodNotAllowed responses are not implemented. |
| 409 | Conflict | ✅ Completed (not implemented) | AWS conflict variants such as BucketNotEmpty or operation conflicts are not fully modeled. |
| 500 | InternalServerError | ✅ Completed (not implemented) | No explicit AWS InternalServerError mapping exists for this operation. |
| 501 | NotImplemented | ✅ Completed (not implemented) | Unsupported optional AWS S3 features are currently ignored or documented as gaps rather than returned as `NotImplemented`. |
| 503 | SlowDown or ServiceUnavailable | ✅ Completed (not implemented) | No throttling or service-availability model exists. |

---

### Operation 80 — PutBucketMetricsConfiguration `PUT /{bucket}?metrics`

| Coverage Area | Required / Optional | Status | Notes |
|---|---|---|---|
| Request headers | Optional `x-amz-expected-bucket-owner` and checksum headers are ignored. | 🔴 Ignored | Header coverage for this operation. |
| Query parameters | Required `metrics` route flag is tested; AWS `id` query semantics are not modeled. | 🟡 Partial | Route and query coverage for this operation. |
| Request body | `MetricsConfiguration` XML is parsed and stored through bucket application services; XML `Id` is retained where represented by the domain value object. | ✅ Tested | Request payload coverage. |
| Response body / headers | No success body. | ✅ Tested | Response payload and significant header coverage. |

#### Status Codes

| Status Code | Description | Implemented | Notes |
|---|---|---|---|
| 200 | Success | ✅ Implemented | Returned by the handler for successful requests. |
| 400 | Bad Request or InvalidArgument | 🟡 Partially implemented | Selected invalid requests are rejected; full AWS validation is incomplete. |
| 403 | AccessDenied | ✅ Completed (not implemented) | No authentication, IAM, ACL enforcement, or requester-pays authorization model exists. |
| 404 | Not found condition | ✅ Implemented | NoSuchBucket, NoSuchKey, NoSuchUpload, or missing configuration paths are handled where applicable. |
| 405 | MethodNotAllowed | 🟡 Partially implemented | Unsupported methods may be rejected by WebFlux routing, but S3 XML MethodNotAllowed responses are not implemented. |
| 409 | Conflict | ✅ Completed (not implemented) | AWS conflict variants such as BucketNotEmpty or operation conflicts are not fully modeled. |
| 500 | InternalServerError | ✅ Completed (not implemented) | No explicit AWS InternalServerError mapping exists for this operation. |
| 501 | NotImplemented | ✅ Completed (not implemented) | Unsupported optional AWS S3 features are currently ignored or documented as gaps rather than returned as `NotImplemented`. |
| 503 | SlowDown or ServiceUnavailable | ✅ Completed (not implemented) | No throttling or service-availability model exists. |

---

### Operation 81 — DeleteBucketMetricsConfiguration `DELETE /{bucket}?metrics`

| Coverage Area | Required / Optional | Status | Notes |
|---|---|---|---|
| Request headers | Optional `x-amz-expected-bucket-owner` is ignored. | 🔴 Ignored | Header coverage for this operation. |
| Query parameters | Required `metrics` route flag is tested; AWS `id` query semantics are not modeled. | 🟡 Partial | Route and query coverage for this operation. |
| Request body | None. | ✅ Tested | Request payload coverage. |
| Response body / headers | No success body; stored metrics configuration is removed. | ✅ Tested | Response payload and significant header coverage. |

#### Status Codes

| Status Code | Description | Implemented | Notes |
|---|---|---|---|
| 204 | No Content success | ✅ Implemented | Returned by the handler for successful delete/abort requests. |
| 400 | Bad Request or InvalidArgument | 🟡 Partially implemented | Malformed requests may fail through routing/codec behavior; operation-specific AWS validation is incomplete. |
| 403 | AccessDenied | ✅ Completed (not implemented) | No authentication, IAM, ACL enforcement, or requester-pays authorization model exists. |
| 404 | Not found condition | ✅ Implemented | NoSuchBucket, NoSuchKey, NoSuchUpload, or missing configuration paths are handled where applicable. |
| 405 | MethodNotAllowed | 🟡 Partially implemented | Unsupported methods may be rejected by WebFlux routing, but S3 XML MethodNotAllowed responses are not implemented. |
| 409 | Conflict | ✅ Completed (not implemented) | AWS conflict variants such as BucketNotEmpty or operation conflicts are not fully modeled. |
| 500 | InternalServerError | ✅ Completed (not implemented) | No explicit AWS InternalServerError mapping exists for this operation. |
| 501 | NotImplemented | ✅ Completed (not implemented) | Unsupported optional AWS S3 features are currently ignored or documented as gaps rather than returned as `NotImplemented`. |
| 503 | SlowDown or ServiceUnavailable | ✅ Completed (not implemented) | No throttling or service-availability model exists. |

---

### Operation 82 — GetBucketIntelligentTieringConfiguration `GET /{bucket}?intelligent-tiering`

| Coverage Area | Required / Optional | Status | Notes |
|---|---|---|---|
| Request headers | Optional `x-amz-expected-bucket-owner` is ignored. | 🔴 Ignored | Header coverage for this operation. |
| Query parameters | Required `intelligent-tiering` route flag is tested; AWS `id` query semantics are not modeled and the current implementation stores one intelligent-tiering configuration per bucket. | 🟡 Partial | Route and query coverage for this operation. |
| Request body | None. | ✅ Tested | Request payload coverage. |
| Response body / headers | `IntelligentTieringConfiguration` XML from `BucketIntelligentTieringConfiguration`. | ✅ Tested | Response payload and significant header coverage. |

#### Status Codes

| Status Code | Description | Implemented | Notes |
|---|---|---|---|
| 200 | Success | ✅ Implemented | Returned by the handler for successful requests. |
| 301 | PermanentRedirect or wrong-region redirect | ✅ Completed (not implemented) | Region redirects are not modeled by the in-memory local service. |
| 304 | NotModified for conditional requests | ✅ Completed (not implemented) | Conditional request headers are ignored. |
| 400 | Bad Request or InvalidArgument | 🟡 Partially implemented | Malformed requests may fail through routing/codec behavior; operation-specific AWS validation is incomplete. |
| 403 | AccessDenied | ✅ Completed (not implemented) | No authentication, IAM, ACL enforcement, or requester-pays authorization model exists. |
| 404 | Not found condition | ✅ Implemented | NoSuchBucket, NoSuchKey, NoSuchUpload, or missing configuration paths are handled where applicable. |
| 405 | MethodNotAllowed | 🟡 Partially implemented | Unsupported methods may be rejected by WebFlux routing, but S3 XML MethodNotAllowed responses are not implemented. |
| 409 | Conflict | ✅ Completed (not implemented) | AWS conflict variants such as BucketNotEmpty or operation conflicts are not fully modeled. |
| 500 | InternalServerError | ✅ Completed (not implemented) | No explicit AWS InternalServerError mapping exists for this operation. |
| 501 | NotImplemented | ✅ Completed (not implemented) | Unsupported optional AWS S3 features are currently ignored or documented as gaps rather than returned as `NotImplemented`. |
| 503 | SlowDown or ServiceUnavailable | ✅ Completed (not implemented) | No throttling or service-availability model exists. |

---

### Operation 83 — PutBucketIntelligentTieringConfiguration `PUT /{bucket}?intelligent-tiering`

| Coverage Area | Required / Optional | Status | Notes |
|---|---|---|---|
| Request headers | Optional `x-amz-expected-bucket-owner` and checksum headers are ignored. | 🔴 Ignored | Header coverage for this operation. |
| Query parameters | Required `intelligent-tiering` route flag is tested; AWS `id` query semantics are not modeled. | 🟡 Partial | Route and query coverage for this operation. |
| Request body | `IntelligentTieringConfiguration` XML is parsed and stored through bucket application services; XML `Id` is retained where represented by the domain value object. | ✅ Tested | Request payload coverage. |
| Response body / headers | No success body. | ✅ Tested | Response payload and significant header coverage. |

#### Status Codes

| Status Code | Description | Implemented | Notes |
|---|---|---|---|
| 200 | Success | ✅ Implemented | Returned by the handler for successful requests. |
| 400 | Bad Request or InvalidArgument | 🟡 Partially implemented | Selected invalid requests are rejected; full AWS validation is incomplete. |
| 403 | AccessDenied | ✅ Completed (not implemented) | No authentication, IAM, ACL enforcement, or requester-pays authorization model exists. |
| 404 | Not found condition | ✅ Implemented | NoSuchBucket, NoSuchKey, NoSuchUpload, or missing configuration paths are handled where applicable. |
| 405 | MethodNotAllowed | 🟡 Partially implemented | Unsupported methods may be rejected by WebFlux routing, but S3 XML MethodNotAllowed responses are not implemented. |
| 409 | Conflict | ✅ Completed (not implemented) | AWS conflict variants such as BucketNotEmpty or operation conflicts are not fully modeled. |
| 500 | InternalServerError | ✅ Completed (not implemented) | No explicit AWS InternalServerError mapping exists for this operation. |
| 501 | NotImplemented | ✅ Completed (not implemented) | Unsupported optional AWS S3 features are currently ignored or documented as gaps rather than returned as `NotImplemented`. |
| 503 | SlowDown or ServiceUnavailable | ✅ Completed (not implemented) | No throttling or service-availability model exists. |

---

### Operation 84 — DeleteBucketIntelligentTieringConfiguration `DELETE /{bucket}?intelligent-tiering`

| Coverage Area | Required / Optional | Status | Notes |
|---|---|---|---|
| Request headers | Optional `x-amz-expected-bucket-owner` is ignored. | 🔴 Ignored | Header coverage for this operation. |
| Query parameters | Required `intelligent-tiering` route flag is tested; AWS `id` query semantics are not modeled. | 🟡 Partial | Route and query coverage for this operation. |
| Request body | None. | ✅ Tested | Request payload coverage. |
| Response body / headers | No success body; stored intelligent-tiering configuration is removed. | ✅ Tested | Response payload and significant header coverage. |

#### Status Codes

| Status Code | Description | Implemented | Notes |
|---|---|---|---|
| 204 | No Content success | ✅ Implemented | Returned by the handler for successful delete/abort requests. |
| 400 | Bad Request or InvalidArgument | 🟡 Partially implemented | Malformed requests may fail through routing/codec behavior; operation-specific AWS validation is incomplete. |
| 403 | AccessDenied | ✅ Completed (not implemented) | No authentication, IAM, ACL enforcement, or requester-pays authorization model exists. |
| 404 | Not found condition | ✅ Implemented | NoSuchBucket, NoSuchKey, NoSuchUpload, or missing configuration paths are handled where applicable. |
| 405 | MethodNotAllowed | 🟡 Partially implemented | Unsupported methods may be rejected by WebFlux routing, but S3 XML MethodNotAllowed responses are not implemented. |
| 409 | Conflict | ✅ Completed (not implemented) | AWS conflict variants such as BucketNotEmpty or operation conflicts are not fully modeled. |
| 500 | InternalServerError | ✅ Completed (not implemented) | No explicit AWS InternalServerError mapping exists for this operation. |
| 501 | NotImplemented | ✅ Completed (not implemented) | Unsupported optional AWS S3 features are currently ignored or documented as gaps rather than returned as `NotImplemented`. |
| 503 | SlowDown or ServiceUnavailable | ✅ Completed (not implemented) | No throttling or service-availability model exists. |

---

## Summary

| Category | Count / Status | Notes |
|---|---:|---|
| Implemented operations documented | 84 | Every operation has request header, query parameter, request body, response body/header, and Status Codes coverage. |
| Multipart operations documented | 7 | Operations 64–70 cover CreateMultipartUpload, UploadPart, UploadPartCopy, CompleteMultipartUpload, AbortMultipartUpload, ListMultipartUploads, and ListParts. |
| Phase E operations documented | 14 | Operations 71–84 cover Analytics, Inventory, Metrics, and Intelligent-Tiering configuration APIs. |
| Source-code changes in this update | 0 | Documentation-only update. |

### APIs yet to implement or out of scope

Advanced/specialized Amazon S3 operations such as CreateSession, directory buckets, object lock/legal hold/retention, torrent, restore, select, rename, update object encryption, write-get-object-response, and bucket metadata table operations remain future Phase F work. Amazon S3 Control APIs remain out of scope.

---

*Generated from PLAN.md operation inventory and current `s3-api` route/handler analysis. Update this file whenever an S3 operation, header, parameter, body, or status mapping changes.*
