# 6. S3 API protocol endpoint mapping

Date: 2026-05-21

## Status

Accepted

## Context

AWS S3 provides 111 REST API operations. The implementation is split into phases covering core CRUD, CLI-baseline compatibility, object metadata/tagging/ACL, bucket configuration APIs, multipart upload, analytics, and advanced operations.

## Decision

### Phase A — CLI-Baseline Compatibility (completed)

| Operation | Endpoint | Status |
|-----------|----------|--------|
| ListObjectsV2 | GET /{bucket}?list-type=2 | ✅ Implemented |
| CopyObject | PUT /{bucket}/{key} x-amz-copy-source | ✅ Implemented |
| DeleteObjects | POST /{bucket}?delete | ✅ Implemented |
| GetBucketLocation | GET /{bucket}?location | ✅ Implemented |
| GetBucketVersioning | GET /{bucket}?versioning | ✅ Implemented |
| PutBucketVersioning | PUT /{bucket}?versioning | ✅ Implemented |
| ListObjectVersions | GET /{bucket}?versions | ✅ Implemented |

### Phase B — Object Metadata, Tagging, and ACL (completed)

| Operation | Endpoint | Status |
|-----------|----------|--------|
| GetObjectAcl | GET /{bucket}/{key}?acl | ✅ Implemented |
| PutObjectAcl | PUT /{bucket}/{key}?acl | ✅ Implemented |
| GetObjectTagging | GET /{bucket}/{key}?tagging | ✅ Implemented |
| PutObjectTagging | PUT /{bucket}/{key}?tagging | ✅ Implemented |
| DeleteObjectTagging | DELETE /{bucket}/{key}?tagging | ✅ Implemented |
| GetObjectAttributes | GET /{bucket}/{key}?attributes | ✅ Implemented |
| GetBucketAcl | GET /{bucket}?acl | ✅ Implemented |
| PutBucketAcl | PUT /{bucket}?acl | ✅ Implemented |
| GetBucketTagging | GET /{bucket}?tagging | ✅ Implemented |
| PutBucketTagging | PUT /{bucket}?tagging | ✅ Implemented |
| DeleteBucketTagging | DELETE /{bucket}?tagging | ✅ Implemented |

### Phase C — Multipart Upload (not started)

| Operation | Endpoint |
|-----------|----------|
| CreateMultipartUpload | POST /{bucket}/{key}?uploads |
| UploadPart | PUT /{bucket}/{key}?uploadId=...&partNumber=... |
| UploadPartCopy | PUT /{bucket}/{key}?uploadId=...&partNumber=... + x-amz-copy-source |
| CompleteMultipartUpload | POST /{bucket}/{key}?uploadId=... |
| AbortMultipartUpload | DELETE /{bucket}/{key}?uploadId=... |
| ListMultipartUploads | GET /{bucket}?uploads |
| ListParts | GET /{bucket}/{key}?uploadId=... |

### Phase D — Bucket Configuration APIs (CORS completed, rest pending)

| Area | Status |
|------|--------|
| CORS | ✅ GetBucketCors, PutBucketCors, DeleteBucketCors |
| Lifecycle | ⬜ Not implemented |
| Policy | ⬜ Not implemented |
| Encryption | ⬜ Not implemented |
| Logging | ⬜ Not implemented |
| Website | ⬜ Not implemented |
| Notification | ⬜ Not implemented |
| Replication | ⬜ Not implemented |
| Ownership Controls | ⬜ Not implemented |
| Public Access Block | ⬜ Not implemented |
| Accelerate | ⬜ Not implemented |

### Phase E — Analytics, Inventory, Metrics, Intelligent-Tiering (not started)

### Phase F — Advanced / Specialized Operations (not started)

### Implementation Rule

Per ogni nuova operazione:
1. RouterFunction route in s3-api
2. AWS S3 terminology in domain/application DTOs
3. Jackson 3 XML annotations per XML response/request records
4. Cucumber feature scenarios (success + failure)
5. AWS CLI coverage in test-aws-cli.sh
6. Update PLAN.md coverage table
7. Update ARC42 and ADRs se introduce nuove decisioni architetturali

### Domain / Application / Test Requirement

- Domain: value objects / aggregates per nuovi concetti di dati
- Application: service methods in BucketService/ObjectService, DTO records
- Tests: almeno 1 success + 1 failure scenario per operazione
- AWS CLI failure tests per ogni errore documentato

## Consequences

**Positive**: phased approach delivers working S3 API quickly, priorities based on AWS CLI common usage
**Negative**: phase 2/3 operations not available until implemented
