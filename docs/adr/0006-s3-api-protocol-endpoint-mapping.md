# 6. S3 API protocol endpoint mapping

Date: 2026-05-21

## Status

Proposed

## Context

AWS S3 provides 40+ REST API operations. The initial implementation covers core operations. Future phases will add multipart upload, batch operations, tagging, versioning, CORS, lifecycle management, and ACLs.

## Decision

### Phase 1 (implemented)
| Operation | Endpoint | Status |
|-----------|----------|--------|
| ListBuckets | GET / | ✅ Implemented |
| CreateBucket | PUT /{bucket} | ✅ Implemented |
| HeadBucket | HEAD /{bucket} | ✅ Implemented |
| ListObjects | GET /{bucket} | ✅ Implemented |
| PutObject | PUT /{bucket}/{key} | ✅ Implemented |
| GetObject | GET /{bucket}/{key} | ✅ Implemented |
| HeadObject | HEAD /{bucket}/{key} | ✅ Implemented |
| DeleteObject | DELETE /{bucket}/{key} | ✅ Implemented |
| DeleteBucket | DELETE /{bucket} | ✅ Implemented |

### Phase 2 (planned)
| Operation | Endpoint | Priority |
|-----------|----------|----------|
| ListObjectsV2 | GET /{bucket}?list-type=2 | High |
| CopyObject | PUT /{bucket}/{key} x-amz-copy-source | High |
| DeleteObjects | POST /{bucket}?delete | High |
| GetBucketVersioning | GET /{bucket}?versioning | Medium |
| PutBucketVersioning | PUT /{bucket}?versioning | Medium |
| GetBucketTagging | GET /{bucket}?tagging | Medium |
| PutBucketTagging | PUT /{bucket}?tagging | Medium |
| DeleteBucketTagging | DELETE /{bucket}?tagging | Medium |

### Phase 3 (future)
Multipart upload, CORS, lifecycle, ACL, encryption, replication, object lock, public access block.

## Consequences

**Positive**: phased approach delivers working S3 API quickly, priorities based on AWS CLI common usage
**Negative**: phase 2/3 operations not available until implemented
