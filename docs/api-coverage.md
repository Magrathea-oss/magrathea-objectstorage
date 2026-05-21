# S3 API Coverage Analysis

Status legend:
- ✅ **Tested** — header/param is read and used in the response/storage
- 🟡 **Not tested** — header/param is stored in hashtable but not used in business logic
- 🔴 **Not implemented** — header/param is completely ignored (not read at all)
- ⬜ **Not implemented** — stored in hashtable only, no domain/application processing

---

## 1. ListBuckets `GET /`

| Header / Param | Required | Status | Notes |
|---|---|---|---|
| No request headers | — | ✅ Tested | Returns XML `ListAllMyBucketsResult` |

---

## 2. CreateBucket `PUT /{bucket}`

| Header / Param | Required | Status | Notes |
|---|---|---|---|
| `x-amz-acl` / `x-amz-grant-*` | 🟡 Optional | 🔴 Not implemented | Hardcoded `private` |
| `x-amz-bucket-object-lock-enabled` | 🟡 Optional | 🔴 Not implemented | — |
| `x-amz-expected-bucket-owner` | 🟡 Optional | 🔴 Not implemented | — |
| Body `LocationConstraint` / `Region` | 🟡 Optional | 🔴 Not implemented | Hardcoded `us-east-1` |
| Body `StorageClass` | 🟡 Optional | 🔴 Not implemented | Hardcoded `STANDARD` |

---

## 3. HeadBucket `HEAD /{bucket}`

| Header / Param | Required | Status | Notes |
|---|---|---|---|
| No request headers | — | ✅ Tested | Returns 200 if exists, 404 if not |

---

## 4. DeleteBucket `DELETE /{bucket}`

| Header / Param | Required | Status | Notes |
|---|---|---|---|
| `x-amz-expected-bucket-owner` | 🟡 Optional | 🔴 Not implemented | — |

---

## 5. ListObjects `GET /{bucket}`

| Query Param | Required | Status | Notes |
|---|---|---|---|
| `prefix` | 🟡 Optional | 🔴 Not implemented | Always returns all objects |
| `marker` | 🟡 Optional | 🔴 Not implemented | Always returns from start |
| `max-keys` | 🟡 Optional | 🔴 Not implemented | Hardcoded `1000` |
| `delimiter` | 🟡 Optional | 🔴 Not implemented | No grouping |

---

## 6. PutObject `PUT /{bucket}/{key}`

| Header / Param | Required | Status | Notes |
|---|---|---|---|
| `Content-Type` | 🟡 Optional | ✅ Tested | Read and stored |
| `Content-Length` | 🟡 Optional | ✅ Tested | Read and stored |
| `x-amz-storage-class` | 🟡 Optional | ✅ Tested | Stored, returned by GetObjectAttributes |
| `x-amz-acl` / `x-amz-grant-*` | 🟡 Optional | 🔴 Not implemented | — |
| `Content-Disposition` | 🟡 Optional | 🔴 Not implemented | — |
| `Content-Encoding` | 🟡 Optional | 🔴 Not implemented | — |
| `x-amz-tagging` | 🟡 Optional | 🔴 Not implemented | Use separate tagging API |
| `x-amz-metadata-*` | 🟡 Optional | 🔴 Not implemented | — |
| `x-amz-expected-bucket-owner` | 🟡 Optional | 🔴 Not implemented | — |
| `x-amz-object-lock-*` | 🟡 Optional | 🔴 Not implemented | — |
| `x-amz-copy-source` | 🟡 Optional (for copy) | ✅ Tested | Separate CopyObject handler |

---

## 7. GetObject `GET /{bucket}/{key}`

| Header / Param | Required | Status | Notes |
|---|---|---|---|
| `Range` | 🟡 Optional | 🔴 Not implemented | Always returns full content |
| `version-id` (query) | 🟡 Optional | 🔴 Not implemented | — |
| `if-match` / `if-none-match` / `if-modified-since` | 🟡 Optional | 🔴 Not implemented | — |
| `x-amz-expected-bucket-owner` | 🟡 Optional | 🔴 Not implemented | — |

---

## 8. HeadObject `HEAD /{bucket}/{key}`

| Header / Param | Required | Status | Notes |
|---|---|---|---|
| `version-id` (query) | 🟡 Optional | 🔴 Not implemented | — |
| `if-match` / `if-none-match` | 🟡 Optional | 🔴 Not implemented | — |
| `x-amz-expected-bucket-owner` | 🟡 Optional | 🔴 Not implemented | — |

---

## 9. DeleteObject `DELETE /{bucket}/{key}`

| Header / Param | Required | Status | Notes |
|---|---|---|---|
| `x-amz-bypass-governance-retention` | 🟡 Optional | 🔴 Not implemented | — |
| `x-amz-expected-bucket-owner` | 🟡 Optional | 🔴 Not implemented | — |
| `version-id` (query) | 🟡 Optional | 🔴 Not implemented | Always deletes current |

---

## 10. ListObjectsV2 `GET /{bucket}?list-type=2`

| Query Param | Required | Status | Notes |
|---|---|---|---|
| `prefix` | 🟡 Optional | 🔴 Not implemented | Always returns all objects |
| `delimiter` | 🟡 Optional | 🔴 Not implemented | No grouping |
| `max-keys` | 🟡 Optional | 🔴 Not implemented | Hardcoded `1000` |
| `continuation-token` | 🟡 Optional | 🔴 Not implemented | Always from start |
| `fetch-owner` | 🟡 Optional | 🔴 Not implemented | — |
| `start-after` | 🟡 Optional | 🔴 Not implemented | — |

---

## 11. CopyObject `PUT /{bucket}/{key}` with `x-amz-copy-source`

| Header / Param | Required | Status | Notes |
|---|---|---|---|
| `x-amz-copy-source` | ✅ Required | ✅ Tested | Source bucket/key parsed |
| `x-amz-metadata-directive` | 🟡 Optional | 🔴 Not implemented | Always copies all metadata |
| `x-amz-storage-class` | 🟡 Optional | 🔴 Not implemented | Uses source's storage class |
| `x-amz-acl` / `x-amz-grant-*` | 🟡 Optional | 🔴 Not implemented | — |
| `x-amz-tagging-directive` | 🟡 Optional | 🔴 Not implemented | — |
| `Content-Disposition` | 🟡 Optional | 🔴 Not implemented | — |
| `Content-Encoding` | 🟡 Optional | 🔴 Not implemented | — |
| `x-amz-expected-bucket-owner` | 🟡 Optional | 🔴 Not implemented | — |

---

## 12. DeleteObjects `POST /{bucket}?delete`

| Header / Param | Required | Status | Notes |
|---|---|---|---|
| Body XML `<Delete>` | ✅ Required | ✅ Tested | Keys parsed from XML |
| `x-amz-bypass-governance-retention` | 🟡 Optional | 🔴 Not implemented | — |
| `x-amz-expected-bucket-owner` | 🟡 Optional | 🔴 Not implemented | — |

---

## 13. GetBucketLocation `GET /{bucket}?location`

| Header / Param | Required | Status | Notes |
|---|---|---|---|
| No request headers | — | ✅ Tested | Returns region from bucket |

---

## 14. GetBucketVersioning `GET /{bucket}?versioning`

| Header / Param | Required | Status | Notes |
|---|---|---|---|
| No request headers | — | ✅ Tested | Returns Enabled/Suspended |

---

## 15. PutBucketVersioning `PUT /{bucket}?versioning`

| Header / Param | Required | Status | Notes |
|---|---|---|---|
| Body XML `<VersioningConfiguration>` | ✅ Required | ✅ Tested | Status parsed |
| `x-amz-mfa` | 🟡 Optional | 🔴 Not implemented | — |
| `x-amz-expected-bucket-owner` | 🟡 Optional | 🔴 Not implemented | — |

---

## 16. ListObjectVersions `GET /{bucket}?versions`

| Query Param | Required | Status | Notes |
|---|---|---|---|
| `prefix` | 🟡 Optional | 🔴 Not implemented | Always returns all |
| `max-keys` | 🟡 Optional | 🔴 Not implemented | Hardcoded `1000` |
| `delimiter` | 🟡 Optional | 🔴 Not implemented | No grouping |
| `key-marker` | 🟡 Optional | 🔴 Not implemented | — |
| `version-id-marker` | 🟡 Optional | 🔴 Not implemented | — |

---

## 17. GetObjectAcl `GET /{bucket}/{key}?acl`

| Header / Param | Required | Status | Notes |
|---|---|---|---|
| `version-id` (query) | 🟡 Optional | 🔴 Not implemented | — |
| `x-amz-expected-bucket-owner` | 🟡 Optional | 🔴 Not implemented | — |
| ACL data | — | ⬜ Not implemented | Stored in hashtable only |

---

## 18. PutObjectAcl `PUT /{bucket}/{key}?acl`

| Header / Param | Required | Status | Notes |
|---|---|---|---|
| `x-amz-acl` | 🟡 Optional | ⬜ Not implemented | Stored in hashtable only, not in domain |
| `x-amz-grant-*` | 🟡 Optional | 🔴 Not implemented | — |
| Body XML `<AccessControlPolicy>` | 🟡 Optional | 🔴 Not implemented | Only canned ACL via header |
| `x-amz-expected-bucket-owner` | 🟡 Optional | 🔴 Not implemented | — |

---

## 19. GetObjectTagging `GET /{bucket}/{key}?tagging`

| Header / Param | Required | Status | Notes |
|---|---|---|---|
| `version-id` (query) | 🟡 Optional | 🔴 Not implemented | — |
| `x-amz-expected-bucket-owner` | 🟡 Optional | 🔴 Not implemented | — |
| Tag data | — | ⬜ Not implemented | Stored in hashtable only |

---

## 20. PutObjectTagging `PUT /{bucket}/{key}?tagging`

| Header / Param | Required | Status | Notes |
|---|---|---|---|
| Body XML `<Tagging>` | ✅ Required | ⬜ Not implemented | Parsed and stored in hashtable only |
| `x-amz-expected-bucket-owner` | 🟡 Optional | 🔴 Not implemented | — |

---

## 21. DeleteObjectTagging `DELETE /{bucket}/{key}?tagging`

| Header / Param | Required | Status | Notes |
|---|---|---|---|
| `x-amz-expected-bucket-owner` | 🟡 Optional | 🔴 Not implemented | Removes from hashtable |

---

## 22. GetObjectAttributes `GET /{bucket}/{key}?attributes`

| Header / Param | Required | Status | Notes |
|---|---|---|---|
| `x-amz-object-attributes` | ✅ Required | ✅ Tested | Only ETag, ObjectSize, StorageClass |
| `version-id` (query) | 🟡 Optional | 🔴 Not implemented | — |
| `max-records` (query) | 🟡 Optional | 🔴 Not implemented | — |

---

## 23. GetBucketAcl `GET /{bucket}?acl`

| Header / Param | Required | Status | Notes |
|---|---|---|---|
| No request headers | — | ⬜ Not implemented | Stored in hashtable only |

---

## 24. PutBucketAcl `PUT /{bucket}?acl`

| Header / Param | Required | Status | Notes |
|---|---|---|---|
| `x-amz-acl` | 🟡 Optional | ⬜ Not implemented | Stored in hashtable only |
| `x-amz-grant-*` | 🟡 Optional | 🔴 Not implemented | — |
| Body XML `<AccessControlPolicy>` | 🟡 Optional | 🔴 Not implemented | — |

---

## 25. GetBucketTagging `GET /{bucket}?tagging`

| Header / Param | Required | Status | Notes |
|---|---|---|---|
| No request headers | — | ⬜ Not implemented | Stored in hashtable only |

---

## 26. PutBucketTagging `PUT /{bucket}?tagging`

| Header / Param | Required | Status | Notes |
|---|---|---|---|
| Body XML `<Tagging>` | ✅ Required | ⬜ Not implemented | Parsed and stored in hashtable only |

---

## 27. DeleteBucketTagging `DELETE /{bucket}?tagging`

| Header / Param | Required | Status | Notes |
|---|---|---|---|
| No request headers | — | ⬜ Not implemented | Removes from hashtable |

---

## 28. GetBucketCors `GET /{bucket}?cors` ✅ NEW

| Header / Param | Required | Status | Notes |
|---|---|---|---|
| No request headers | — | ✅ Tested | Returns CORS configuration XML |

---

## 29. PutBucketCors `PUT /{bucket}?cors` ✅ NEW

| Header / Param | Required | Status | Notes |
|---|---|---|---|
| Body XML `<CORSConfiguration>` | ✅ Required | ✅ Tested | Parsed, stored via domain/application |
| `x-amz-expected-bucket-owner` | 🟡 Optional | 🔴 Not implemented | — |

---

## 30. DeleteBucketCors `DELETE /{bucket}?cors` ✅ NEW

| Header / Param | Required | Status | Notes |
|---|---|---|---|
| No request headers | — | ✅ Tested | Removes CORS configuration |

---

## Summary

| Category | Count |
|---|---|
| ✅ **Tested** (read and used) | 18 |
| ⬜ **Not implemented** (hashtable only) | 10 |
| 🔴 **Not implemented** (ignored) | 54 |
| 🟡 **Not tested** | 0 |

### APIs implemented in this session

| API | Domain | Application | Handler | Cucumber tests | AWS CLI |
|---|---|---|---|---|---|
| GetBucketCors | `BucketConfiguration` value object | `BucketService.getCorsConfiguration()` | `S3BucketConfigHandler.getBucketCors()` | ✅ success + failure | ✅ |
| PutBucketCors | `BucketConfiguration` + `CorsRule` records | `BucketService.putCorsConfiguration()` | `S3BucketConfigHandler.putBucketCors()` | ✅ success + failure | ✅ |
| DeleteBucketCors | — | `BucketService.deleteCorsConfiguration()` | `S3BucketConfigHandler.deleteBucketCors()` | ✅ success + failure | ✅ |

### APIs yet to implement (Phases C–F)

**Phase C — Multipart Upload** (7 operations):
- CreateMultipartUpload
- UploadPart
- UploadPartCopy
- CompleteMultipartUpload
- AbortMultipartUpload
- ListMultipartUploads
- ListParts

**Phase D — Bucket Configuration** (22 operations):
- CORS (3), Lifecycle (5), Policy (4), Encryption (3), Logging (2), Website (3), Notification (4), Replication (3), RequestPayment (2), OwnershipControls (3), PublicAccessBlock (3), Accelerate (2)

**Phase E — Analytics & Inventory** (16 operations)

**Phase F — Advanced** (22 operations)

---

*Generated from source code analysis — update when new headers/params are implemented.*
