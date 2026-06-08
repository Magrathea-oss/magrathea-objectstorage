# Cucumber Test Analysis

## Summary

| Category | Scenario Count |
|---|---|
| Standard | 256 |
| AWS CLI | 1 |
| AWS CLI with script | 0 |
| **Total** | **257** |

> **Note:** No feature files use Cucumber tags (`@aws-sdk`, `@awscli`, `@cli`, `@aws-cli-script`). Categorization is based on file location and scenario content.

---

## Feature Files Found: 11

### 1. Standard scenarios — 256 scenarios across 10 files

| # | File | Scenarios |
|---|---|---|
| 1 | `s3-reactive-api-adapter/src/test/features/object-store/bucket-config.feature` | 148 |
| 2 | `s3-reactive-api-adapter/src/test/features/object-store/object-crud.feature` | 27 |
| 3 | `s3-reactive-api-adapter/src/test/features/object-store/put_object.feature` | 26 |
| 4 | `s3-reactive-api-adapter/src/test/features/object-store/bucket-operations.feature` | 13 |
| 5 | `s3-reactive-api-adapter/src/test/features/object-store/metadata-operations.feature` | 13 |
| 6 | `s3-reactive-api-adapter/src/test/features/object-store/multipart-upload.feature` | 9 |
| 7 | `object-store-reactive-application/src/test/resources/features/bucket-lifecycle.feature` | 7 |
| 8 | `object-store-reactive-application/src/test/resources/features/multipart-upload.feature` | 6 |
| 9 | `object-store-reactive-application/src/test/resources/features/object-lifecycle.feature` | 5 |
| 10 | `s3-reactive-api-adapter/src/test/features/object-store/runtime-effects.feature` | 2 |

**Total standard scenarios: 256**

---

### 2. AWS CLI scenarios — 1 scenario across 1 file

| # | File | Scenarios |
|---|---|---|
| 1 | `s3-reactive-api-adapter/src/test/features/awscli/put_object.feature` | 1 |

**Total AWS CLI scenarios: 1**

| File | Scenario |
|---|---|
| `awscli/put_object.feature` | AWS CLI put-object succeeds with default headers |

---

### 3. AWS CLI with script — 0 scenarios

No feature files reference `test-aws-cli.sh` or use `@aws-cli-script` tags.

---

## Detailed Scenario Listing

### Standard — `s3-reactive-api-adapter/src/test/features/object-store/bucket-config.feature` (148)

Bucket configuration API: CORS, Lifecycle, Policy, Encryption, Logging, Website, Notification, Replication, Request Payment, Ownership Controls, Public Access Block, Accelerate, Analytics, Inventory, Metrics, Intelligent-Tiering, ABAC, Object Lock, Metadata, Metadata Table, Inventory Table, Journal Table — each with success/failure patterns (Put/Get/Delete + nonexistent/absent variants).

### Standard — `s3-reactive-api-adapter/src/test/features/object-store/object-crud.feature` (27)

Object CRUD: Get, Head, List V2, Copy, List versions, Delete multi, Delete, Rename (×2), Update encryption (×2), Get torrent, Restore (×2), plus failure scenarios (nonexistent object/bucket, idempotent delete).

### Standard — `s3-reactive-api-adapter/src/test/features/object-store/put_object.feature` (26)

PutObject anomaly tests: metadata headers, SSE/SSE-C, checksum SHA256, version-id, expected bucket owner, AWS CLI default header ignore (crc64nvme, sdk-checksum, SigV4, User-Agent, Expect), Content-MD5, no checksum, direct checksum, D1 delegation, SSE-KMS, STANDARD class, checksum echo, copy with metadata directive, lock, archive, state-machine lifecycle.

### Standard — `s3-reactive-api-adapter/src/test/features/object-store/bucket-operations.feature` (13)

Bucket operations: Create, Head, Location, Versioning (get/put), Delete, List, plus failure scenarios (duplicate, nonexistent bucket for Head/Location/Versioning/Delete).

### Standard — `s3-reactive-api-adapter/src/test/features/object-store/metadata-operations.feature` (13)

ACL and tagging: Bucket ACL (get/put), Object ACL (get/put), Object attributes, plus failure scenarios (nonexistent bucket/object for ACL, tagging get/put/delete).

### Standard — `s3-reactive-api-adapter/src/test/features/object-store/multipart-upload.feature` (9)

Multipart upload: Initiate, Upload part, List parts, Complete, Abort, List uploads, plus failure scenarios (nonexistent bucket, invalid uploadId, complete nonexistent).

### Standard — `object-store-reactive-application/src/test/resources/features/bucket-lifecycle.feature` (7)

Domain bucket lifecycle: Create, duplicate, find, find not found, delete, delete nonexistent, update CORS config.

### Standard — `object-store-reactive-application/src/test/resources/features/multipart-upload.feature` (6)

Domain multipart lifecycle: Initiate, Add parts, Complete, Abort, Find by ID, Find not found.

### Standard — `object-store-reactive-application/src/test/resources/features/object-lifecycle.feature` (5)

Domain object lifecycle: Create with ContentDescriptor, Find, Find not found, Delete, Delete nonexistent.

### Standard — `s3-reactive-api-adapter/src/test/features/object-store/runtime-effects.feature` (2)

Runtime effects: BucketOwnerEnforced cannot be reverted, PublicAccessBlock blocks public policies.

---

### AWS CLI — `s3-reactive-api-adapter/src/test/features/awscli/put_object.feature` (1)

| Scenario | Description |
|---|---|
| AWS CLI put-object succeeds with default headers | Runs actual `aws s3api put-object` via ProcessBuilder; verifies exit code 0 and object appears in list. Step definitions in `AwsCliObjectSteps.java` invoke real AWS CLI against the local endpoint. |

---

## Scripts

- `test-aws-cli.sh` exists at project root — runs AWS CLI compatibility tests and generates `docs/test-report.md`.
- No feature files directly invoke `test-aws-cli.sh` or use `@aws-cli-script` tags.
