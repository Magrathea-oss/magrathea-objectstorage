# Magrathea ObjectStorage Test Report

Generated: 2026-06-05T08:40:11+02:00

> ⚠️ **Report status: STALE / EVIDENCE-ONLY**
>
> This report reflects a point-in-time snapshot and **must not be read as implementation completeness**.
> - The `111/111` route count (referenced elsewhere) is a mapped-surface inventory only, not semantic completion.
> - AWS CLI standalone results (46 passed / 82 failed) are from a run where the server was not started; most failures are connection errors (exit code 254), not logic failures.
> - AWS CLI Cucumber: **1 scenario** implemented. WebTestClient Cucumber: **238 scenarios** implemented. These are not equivalent.
> - Storage-engine backend scenarios: **absent**. The storage-engine modules are not yet verified end-to-end.
> - **JaCoCo is the current coverage baseline.** Clover/OpenClover is optional/legacy.
>
> A semantic S3 coverage matrix is being built as part of the correction plan (see `PLAN.md` → *S3 API Semantic Completion Plan*).

## Summary

| Suite | Passed | Failed | Total | Notes |
|---|---:|---:|---:|---|
| AWS CLI S3 compatibility | 46 | 82 | 128 | Endpoint: `http://localhost:8080` |
| Maven Surefire | See section | See section | See section | Latest reports under `*/target/surefire-reports` |
| Clover coverage | See section | - | - | Latest report under `target/site/clover` |

## Current Verified Results

| Scope | Evidence | Result | Notes |
|---|---|---:|---|
| Phase 5 domain planning (`storage-engine-domain`) | Commit `b0a5f74`; `PersistencePlannerMinioStandardTest` | 152 tests passing, 0 failures | Verifies deterministic `MINIO_STANDARD` persistence planning in the domain model. |
| Phase 5 YAML catalogs and MINIO_STANDARD integration (`storage-engine-infrastructure`) | Commit `0ec84cf`; `MinioStandardIntegrationTest` | 26 tests passing, 0 failures | Verifies YAML catalog/device integration and `MINIO_STANDARD` selection; storage-engine runtime read/write wiring remains pending for Phase 6/7. |

### AWS CLI Test Status

| Feature | Status | Note |
|---|---|---|
| WebTestClient scenarios (238) | ✅ Passing | Standard Cucumber via Java WebTestClient (`@webclient` tag) |
| AWS CLI Cucumber scenarios | ⚠️ Only 1 scenario | Must reach parity with WebTestClient canonical scenarios (`@awscli` tag) |
| `test-aws-cli.sh` standalone | ⚠️ 46/82 passed | Standalone script; most failures are connection errors (server not running at test time), not logic failures |

## AWS CLI S3 Compatibility

Bucket: `magrathea-cli-test-1780641560-106784`

| Check | Status | Notes |
|---|---|---|
| ListBuckets | ❌ Failed | Expected success, exit code 254 |
| CreateBucket | ❌ Failed | Expected success, exit code 254 |
| HeadBucket existing | ❌ Failed | Expected success, exit code 254 |
| PutObject | ❌ Failed | Expected success, exit code 254 |
| PutObject header capture | ✅ Passed | Headers documented |
| HeadObject existing | ❌ Failed | Expected success, exit code 254 |
| PutObjectAcl | ❌ Failed | Expected success, exit code 254 |
| GetObjectAcl | ❌ Failed | Expected success, exit code 254 |
| PutObjectTagging | ❌ Failed | Expected success, exit code 254 |
| GetObjectTagging | ❌ Failed | Expected success, exit code 254 |
| DeleteObjectTagging | ❌ Failed | Expected success, exit code 254 |
| GetObjectAttributes | ❌ Failed | Expected success, exit code 254 |
| GetObject | ❌ Failed | Expected success, exit code 254 |
| GetObject content matches | ❌ Failed | Expected success, exit code 2 |
| ListObjects | ❌ Failed | Expected success, exit code 254 |
| ListObjectsV2 | ❌ Failed | Expected success, exit code 254 |
| GetBucketLocation | ❌ Failed | Expected success, exit code 255 |
| PutBucketAcl | ❌ Failed | Expected success, exit code 254 |
| GetBucketAcl | ❌ Failed | Expected success, exit code 254 |
| PutBucketTagging | ❌ Failed | Expected success, exit code 254 |
| GetBucketTagging | ❌ Failed | Expected success, exit code 254 |
| DeleteBucketTagging | ❌ Failed | Expected success, exit code 254 |
| GetBucketVersioning initial | ❌ Failed | Expected success, exit code 254 |
| PutBucketVersioning Enabled | ❌ Failed | Expected success, exit code 254 |
| GetBucketVersioning enabled | ❌ Failed | Expected success, exit code 254 |
| ListObjectVersions | ❌ Failed | Expected success, exit code 254 |
| CopyObject | ❌ Failed | Expected success, exit code 254 |
| HeadObject copy existing | ❌ Failed | Expected success, exit code 254 |
| DeleteObjects | ❌ Failed | Expected success, exit code 254 |
| HeadObject copy after DeleteObjects | ✅ Passed | Expected failure |
| PutObject PARANOIC_MODE | ❌ Failed | Expected success, exit code 254 |
| HeadObject PARANOIC_MODE | ❌ Failed | Expected success, exit code 254 |
| GetObjectAttributes PARANOIC_MODE | ❌ Failed | Expected success, exit code 254 |
| DeleteObject PARANOIC_MODE | ❌ Failed | Expected success, exit code 254 |
| DeleteObject | ❌ Failed | Expected success, exit code 254 |
| PutObject SSE-S3 AES256 | ❌ Failed | Expected success, exit code 254 |
| HeadObject SSE-S3 AES256 | ❌ Failed | Expected success, exit code 254 |
| DeleteObject SSE-S3 | ❌ Failed | Expected success, exit code 254 |
| PutObject STANDARD storage class | ❌ Failed | Expected success, exit code 254 |
| HeadObject STANDARD | ❌ Failed | Expected success, exit code 254 |
| GetObjectAttributes STANDARD | ❌ Failed | Expected success, exit code 254 |
| DeleteObject STANDARD | ❌ Failed | Expected success, exit code 254 |
| HeadObject after DeleteObject | ✅ Passed | Expected failure |
| PutBucketCors | ❌ Failed | Expected success, exit code 254 |
| GetBucketCors | ❌ Failed | Expected success, exit code 254 |
| DeleteBucketCors | ❌ Failed | Expected success, exit code 254 |
| GetBucketCors nonexistent | ✅ Passed | Expected failure |
| GetBucketCors after delete | ✅ Passed | Expected failure |
| PutBucketLifecycleConfiguration | ❌ Failed | Expected success, exit code 254 |
| GetBucketLifecycleConfiguration | ❌ Failed | Expected success, exit code 254 |
| DeleteBucketLifecycleConfiguration | ❌ Failed | Expected success, exit code 252 |
| GetBucketLifecycleConfiguration nonexistent | ✅ Passed | Expected failure |
| GetBucketLifecycleConfiguration after delete | ✅ Passed | Expected failure |
| PutBucketPolicy | ❌ Failed | Expected success, exit code 254 |
| GetBucketPolicy | ❌ Failed | Expected success, exit code 254 |
| DeleteBucketPolicy | ❌ Failed | Expected success, exit code 254 |
| GetBucketPolicy nonexistent | ✅ Passed | Expected failure |
| GetBucketPolicy after delete | ✅ Passed | Expected failure |
| PutBucketEncryption | ❌ Failed | Expected success, exit code 252 |
| GetBucketEncryption | ❌ Failed | Expected success, exit code 254 |
| DeleteBucketEncryption | ❌ Failed | Expected success, exit code 254 |
| GetBucketEncryption nonexistent | ✅ Passed | Expected failure |
| GetBucketEncryption after delete | ✅ Passed | Expected failure |
| PutBucketLogging | ❌ Failed | Expected success, exit code 252 |
| GetBucketLogging | ❌ Failed | Expected success, exit code 254 |
| DeleteBucketLogging | ❌ Failed | Expected success, exit code 252 |
| GetBucketLogging nonexistent | ✅ Passed | Expected failure |
| GetBucketLogging after delete | ✅ Passed | Expected failure |
| PutBucketWebsite | ❌ Failed | Expected success, exit code 254 |
| GetBucketWebsite | ❌ Failed | Expected success, exit code 254 |
| DeleteBucketWebsite | ❌ Failed | Expected success, exit code 254 |
| GetBucketWebsite nonexistent | ✅ Passed | Expected failure |
| GetBucketWebsite after delete | ✅ Passed | Expected failure |
| PutBucketNotification | ❌ Failed | Expected success, exit code 254 |
| GetBucketNotification | ❌ Failed | Expected success, exit code 254 |
| GetBucketNotification nonexistent | ✅ Passed | Expected failure |
| PutBucketReplication | ❌ Failed | Expected success, exit code 254 |
| GetBucketReplication | ❌ Failed | Expected success, exit code 254 |
| DeleteBucketReplication | ❌ Failed | Expected success, exit code 254 |
| GetBucketReplication nonexistent | ✅ Passed | Expected failure |
| GetBucketReplication after delete | ✅ Passed | Expected failure |
| PutBucketRequestPayment | ❌ Failed | Expected success, exit code 254 |
| GetBucketRequestPayment | ❌ Failed | Expected success, exit code 254 |
| GetBucketRequestPayment nonexistent | ✅ Passed | Expected failure |
| PutBucketOwnershipControls | ❌ Failed | Expected success, exit code 254 |
| GetBucketOwnershipControls | ❌ Failed | Expected success, exit code 254 |
| DeleteBucketOwnershipControls | ❌ Failed | Expected success, exit code 254 |
| GetBucketOwnershipControls nonexistent | ✅ Passed | Expected failure |
| GetBucketOwnershipControls after delete | ✅ Passed | Expected failure |
| PutPublicAccessBlock | ❌ Failed | Expected success, exit code 254 |
| GetPublicAccessBlock | ❌ Failed | Expected success, exit code 254 |
| DeletePublicAccessBlock | ❌ Failed | Expected success, exit code 254 |
| GetPublicAccessBlock nonexistent | ✅ Passed | Expected failure |
| GetPublicAccessBlock after delete | ✅ Passed | Expected failure |
| PutBucketAccelerateConfiguration | ❌ Failed | Expected success, exit code 254 |
| GetBucketAccelerateConfiguration | ❌ Failed | Expected success, exit code 254 |
| GetBucketAccelerateConfiguration nonexistent | ✅ Passed | Expected failure |
| PutBucketAnalyticsConfiguration | ❌ Failed | Expected success, exit code 254 |
| GetBucketAnalyticsConfiguration | ❌ Failed | Expected success, exit code 254 |
| ListBucketAnalyticsConfigurations | ❌ Failed | Expected success, exit code 254 |
| DeleteBucketAnalyticsConfiguration | ❌ Failed | Expected success, exit code 254 |
| GetBucketAnalyticsConfiguration nonexistent bucket | ✅ Passed | Expected failure |
| GetBucketAnalyticsConfiguration missing id | ✅ Passed | Expected failure |
| DeleteBucketAnalyticsConfiguration nonexistent bucket | ✅ Passed | Expected failure |
| DeleteBucketAnalyticsConfiguration missing id | ✅ Passed | Expected failure |
| PutBucketAnalyticsConfiguration nonexistent bucket | ✅ Passed | Expected failure |
| ListBucketAnalyticsConfigurations nonexistent bucket | ✅ Passed | Expected failure |
| PutBucketInventoryConfiguration | ❌ Failed | Expected success, exit code 252 |
| GetBucketInventoryConfiguration | ❌ Failed | Expected success, exit code 254 |
| ListBucketInventoryConfigurations | ❌ Failed | Expected success, exit code 254 |
| DeleteBucketInventoryConfiguration | ❌ Failed | Expected success, exit code 254 |
| GetBucketInventoryConfiguration nonexistent bucket | ✅ Passed | Expected failure |
| GetBucketInventoryConfiguration missing id | ✅ Passed | Expected failure |
| DeleteBucketInventoryConfiguration nonexistent bucket | ✅ Passed | Expected failure |
| DeleteBucketInventoryConfiguration missing id | ✅ Passed | Expected failure |
| PutBucketInventoryConfiguration nonexistent bucket | ✅ Passed | Expected failure |
| ListBucketInventoryConfigurations nonexistent bucket | ✅ Passed | Expected failure |
| DeleteBucket | ❌ Failed | Expected success, exit code 254 |
| HeadBucket after DeleteBucket | ✅ Passed | Expected failure |
| GetObject nonexistent | ✅ Passed | Expected failure |
| HeadObject nonexistent | ✅ Passed | Expected failure |
| GetBucketLocation nonexistent | ✅ Passed | Expected failure |
| GetBucketVersioning nonexistent | ✅ Passed | Expected failure |
| GetBucketAcl nonexistent | ✅ Passed | Expected failure |
| GetBucketTagging nonexistent | ✅ Passed | Expected failure |
| CopyObject nonexistent source | ✅ Passed | Expected failure |
| PutObject nonexistent bucket | ✅ Passed | Expected failure |
| GetObjectAcl nonexistent | ✅ Passed | Expected failure |

## Maven Surefire Results

| Module | Report | Tests | Failures | Errors | Skipped | Status |
|---|---|---:|---:|---:|---:|---|
| object-store-domain | com.example.magrathea.objectstore.domain.BucketAccelerateConfigurationTest.txt | 3 | 0 | 0 | 0 | ✅ Passed |
| object-store-domain | com.example.magrathea.objectstore.domain.BucketAnalyticsConfigurationTest.txt | 4 | 0 | 0 | 0 | ✅ Passed |
| object-store-domain | com.example.magrathea.objectstore.domain.BucketConfigurationTest.txt | 8 | 0 | 0 | 0 | ✅ Passed |
| object-store-domain | com.example.magrathea.objectstore.domain.BucketEncryptionConfigurationTest.txt | 4 | 0 | 0 | 0 | ✅ Passed |
| object-store-domain | com.example.magrathea.objectstore.domain.BucketIntelligentTieringConfigurationTest.txt | 4 | 0 | 0 | 0 | ✅ Passed |
| object-store-domain | com.example.magrathea.objectstore.domain.BucketInventoryConfigurationTest.txt | 4 | 0 | 0 | 0 | ✅ Passed |
| object-store-domain | com.example.magrathea.objectstore.domain.BucketLifecycleConfigurationTest.txt | 8 | 0 | 0 | 0 | ✅ Passed |
| object-store-domain | com.example.magrathea.objectstore.domain.BucketLoggingConfigurationTest.txt | 3 | 0 | 0 | 0 | ✅ Passed |
| object-store-domain | com.example.magrathea.objectstore.domain.BucketMetricsConfigurationTest.txt | 4 | 0 | 0 | 0 | ✅ Passed |
| object-store-domain | com.example.magrathea.objectstore.domain.BucketNotificationConfigurationTest.txt | 3 | 0 | 0 | 0 | ✅ Passed |
| object-store-domain | com.example.magrathea.objectstore.domain.BucketOwnershipControlsTest.txt | 2 | 0 | 0 | 0 | ✅ Passed |
| object-store-domain | com.example.magrathea.objectstore.domain.BucketPolicyTest.txt | 4 | 0 | 0 | 0 | ✅ Passed |
| object-store-domain | com.example.magrathea.objectstore.domain.BucketReplicationConfigurationTest.txt | 3 | 0 | 0 | 0 | ✅ Passed |
| object-store-domain | com.example.magrathea.objectstore.domain.BucketRequestPaymentConfigurationTest.txt | 3 | 0 | 0 | 0 | ✅ Passed |
| object-store-domain | com.example.magrathea.objectstore.domain.BucketTest.txt | 54 | 0 | 0 | 0 | ✅ Passed |
| object-store-domain | com.example.magrathea.objectstore.domain.BucketWebsiteConfigurationTest.txt | 4 | 0 | 0 | 0 | ✅ Passed |
| object-store-domain | com.example.magrathea.objectstore.domain.ChecksumAlgorithmTest.txt | 5 | 0 | 0 | 0 | ✅ Passed |
| object-store-domain | com.example.magrathea.objectstore.domain.ContentDescriptorTest.txt | 8 | 0 | 0 | 0 | ✅ Passed |
| object-store-domain | com.example.magrathea.objectstore.domain.CorsConfigurationTest.txt | 7 | 0 | 0 | 0 | ✅ Passed |
| object-store-domain | com.example.magrathea.objectstore.domain.EncryptionConfigurationTest.txt | 10 | 0 | 0 | 0 | ✅ Passed |
| object-store-domain | com.example.magrathea.objectstore.domain.EncryptionTypeTest.txt | 2 | 0 | 0 | 0 | ✅ Passed |
| object-store-domain | com.example.magrathea.objectstore.domain.MultipartUploadTest.txt | 9 | 0 | 0 | 0 | ✅ Passed |
| object-store-domain | com.example.magrathea.objectstore.domain.ObjectKeyTest.txt | 11 | 0 | 0 | 0 | ✅ Passed |
| object-store-domain | com.example.magrathea.objectstore.domain.ObjectStoreEventTest.txt | 45 | 0 | 0 | 0 | ✅ Passed |
| object-store-domain | com.example.magrathea.objectstore.domain.PartNumberTest.txt | 4 | 0 | 0 | 0 | ✅ Passed |
| object-store-domain | com.example.magrathea.objectstore.domain.PublicAccessBlockConfigurationTest.txt | 3 | 0 | 0 | 0 | ✅ Passed |
| object-store-domain | com.example.magrathea.objectstore.domain.RegionTest.txt | 4 | 0 | 0 | 0 | ✅ Passed |
| object-store-domain | com.example.magrathea.objectstore.domain.S3ObjectTest.txt | 49 | 0 | 0 | 0 | ✅ Passed |
| object-store-domain | com.example.magrathea.objectstore.domain.StorageClassTest.txt | 7 | 0 | 0 | 0 | ✅ Passed |
| object-store-domain | com.example.magrathea.objectstore.domain.UploadIdTest.txt | 4 | 0 | 0 | 0 | ✅ Passed |
| object-store-domain | com.example.magrathea.objectstore.domain.UploadPartTest.txt | 4 | 0 | 0 | 0 | ✅ Passed |
| object-store-reactive-application | com.example.magrathea.reactive.application.service.CucumberTest.txt | 18 | 0 | 0 | 0 | ✅ Passed |
| s3-reactive-api-adapter | com.example.magrathea.s3api.awscli.AwsCliCucumberTest.txt | 1 | 0 | 0 | 0 | ✅ Passed |
| s3-reactive-api-adapter | com.example.magrathea.s3api.cucumber.ObjectStoreCucumberTest.txt | 238 | 0 | 0 | 0 | ✅ Passed |
| **Total** |  | **544** | **0** | **0** | **0** | **✅ Passed** |

## Coverage

**JaCoCo** is the current coverage baseline. Reports are generated by `mvn verify` under `target/site/jacoco/`.

**Clover/OpenClover** is optional/legacy. Report HTML (if generated): `target/site/clover/index.html`

| Metric | Covered | Total | Coverage |
|---|---:|---:|---:|
| JaCoCo | - | - | Run `mvn verify` to generate |
| Clover (optional/legacy) | - | - | Run `mvn -Pcoverage clover:setup test clover:aggregate clover:clover` to generate |

---

## S3 Semantic Coverage Matrix

> This table classifies each S3 API family by semantic status, not route count.
> **Route mapped** = HTTP route exists; **Stateful behavior** = creates/reads/updates/deletes durable state with observable follow-up; **AWS CLI scenario** = verified via AWS CLI Cucumber or `test-aws-cli.sh`; **Storage-engine scenario** = verified against storage-engine backend; **Semantic status** = overall classification.

### Current Evidence-Based Family Classification

| Family | Operation (examples) | Route mapped | Stateful behavior | AWS CLI scenario | Storage-engine scenario | Semantic status | Notes |
|---|---|---|---|---|---|---|---|
| Object CRUD | PutObject, GetObject, HeadObject, DeleteObject, CopyObject | Yes | Partial — in-memory only | Failing (server not running at test time) | Absent | Stubbed / Partial | Read-after-write verified in-memory only; storage-engine read path returns empty; slash keys unverified; ETag semantics incomplete |
| Bucket baseline | CreateBucket, HeadBucket, DeleteBucket, ListBuckets, ListObjects, ListObjectsV2 | Yes | Partial — in-memory only | Failing (server not running at test time) | Absent | Stubbed / Partial | List prefix/delimiter/continuation tokens unverified against storage-engine; bucket delete guards unverified |
| Multipart upload | CreateMultipartUpload, UploadPart, CompleteMultipartUpload, AbortMultipartUpload, ListParts, ListMultipartUploads | Yes | Shallow/Partial — part bodies not persisted | Missing | Absent | Stubbed | Part persistence and assembly not implemented in storage-engine backend; ETag semantics incomplete |
| Bucket configuration | CORS, Lifecycle, Website, Logging, Notification, Replication, Encryption, Versioning, Tagging, etc. | Yes | Partial — config storage only | Failing (server not running at test time) | Absent | Config-only / Stubbed | No background job execution (lifecycle/replication/notification); enforcement not implemented |
| Object metadata/tagging/ACL | PutObjectTagging, GetObjectTagging, GetObjectAttributes, PutObjectAcl, GetObjectAcl | Yes | Partial — in-memory only | Failing (server not running at test time) | Absent | Stubbed / Partial | Metadata persistence in storage-engine backend unverified |
| Versioning/delete markers | ListObjectVersions, versioned GET/HEAD/DELETE | Yes | Shallow/Partial | Failing (server not running at test time) | Absent | Stubbed | Version IDs, delete marker semantics, and latest-version resolution unverified |
| Access/security controls | BucketPolicy, PublicAccessBlock, OwnershipControls | Yes | Config storage only | Failing (server not running at test time) | Absent | Config-only / Stubbed | Authorization enforcement absent |
| Analytics/inventory/metrics | Bucket analytics, inventory, metrics, intelligent-tiering | Yes | Config storage only | Failing (server not running at test time) | Absent | Config-only | No background report generation |
| Advanced/specialized | SelectObjectContent, RestoreObject, etc. | Some | None verified | Missing | Absent | Stubbed / Out of scope | Likely out of scope without explicit design |
| Admin/storage-engine APIs | StoragePolicy CRUD, StorageDevice CRUD | Route stubs | None verified | Not applicable | Absent | Not implemented | No concrete StoragePolicyCatalog implementation; lookup always fails |

### AWS CLI Cucumber vs WebTestClient Cucumber Parity

| Dimension | WebTestClient Cucumber | AWS CLI Cucumber |
|---|---|---|
| Scenarios | 238 | 1 |
| Tag | `@webclient` | `@awscli` |
| Driver | Spring WebTestClient | `aws s3api` CLI |
| Status | Passing | Stub only |
| Parity goal | Canonical suite | Must reach parity (roadmap item P3/S3-P1+) |

---

## Route-Level S3 Operation Coverage

> ⚠️ The table below documents which S3 operations have routes mapped and appear in `test-aws-cli.sh`.
> **A ✅ here means the route exists and the CLI script invokes it — it does NOT mean the operation is semantically implemented.**
> Most operations failed during the 2026-06-05 standalone run because the server was not running (exit code 254 = connection refused).

### Route and Script Coverage (formerly: "Implemented S3 Operation Coverage")

> Renamed to reflect actual meaning: **route mapped + script invoked ≠ semantically implemented.**

## Route and Script Coverage Table

| Operation | CLI command | Covered by AWS CLI script |
|---|---|---|
| ListBuckets | `aws s3api list-buckets` | ✅ |
| CreateBucket | `aws s3api create-bucket` | ✅ |
| HeadBucket | `aws s3api head-bucket` | ✅ |
| PutObject | `aws s3api put-object` | ✅ |
| HeadObject | `aws s3api head-object` | ✅ |
| GetObject | `aws s3api get-object` | ✅ |
| GetObjectAcl | `aws s3api get-object-acl` | ✅ |
| PutObjectAcl | `aws s3api put-object-acl` | ✅ |
| GetObjectTagging | `aws s3api get-object-tagging` | ✅ |
| PutObjectTagging | `aws s3api put-object-tagging` | ✅ |
| DeleteObjectTagging | `aws s3api delete-object-tagging` | ✅ |
| GetObjectAttributes | `aws s3api get-object-attributes` | ✅ |
| ListObjects | `aws s3api list-objects` | ✅ |
| ListObjectsV2 | `aws s3api list-objects-v2` | ✅ |
| GetBucketLocation | `aws s3api get-bucket-location` | ✅ |
| GetBucketAcl | `aws s3api get-bucket-acl` | ✅ |
| PutBucketAcl | `aws s3api put-bucket-acl` | ✅ |
| GetBucketTagging | `aws s3api get-bucket-tagging` | ✅ |
| PutBucketTagging | `aws s3api put-bucket-tagging` | ✅ |
| DeleteBucketTagging | `aws s3api delete-bucket-tagging` | ✅ |
| GetBucketVersioning | `aws s3api get-bucket-versioning` | ✅ |
| PutBucketVersioning | `aws s3api put-bucket-versioning` | ✅ |
| ListObjectVersions | `aws s3api list-object-versions` | ✅ |
| CopyObject | `aws s3api copy-object` | ✅ |
| PutObject PARANOIC_MODE | `aws s3api put-object --storage-class PARANOIC_MODE` | ✅ |
| HeadObject PARANOIC_MODE | `aws s3api head-object` | ✅ |
| GetObjectAttributes PARANOIC_MODE | `aws s3api get-object-attributes` | ✅ |
| DeleteObject PARANOIC_MODE | `aws s3api delete-object` | ✅ |
| PutObject SSE-S3 AES256 |  | ✅ |
| HeadObject SSE-S3 AES256 |  | ✅ |
| DeleteObject SSE-S3 |  | ✅ |
| PutObject STANDARD storage class |  | ✅ |
| HeadObject STANDARD |  | ✅ |
| GetObjectAttributes STANDARD |  | ✅ |
| DeleteObject STANDARD |  | ✅ |
| DeleteObject | `aws s3api delete-object` | ✅ |
| DeleteObjects | `aws s3api delete-objects` | ✅ |
| PutBucketCors | `aws s3api put-bucket-cors` | ✅ |
| GetBucketCors | `aws s3api get-bucket-cors` | ✅ |
| DeleteBucketCors | `aws s3api delete-bucket-cors` | ✅ |
| PutBucketLifecycleConfiguration | `aws s3api put-bucket-lifecycle-configuration` | ✅ |
| GetBucketLifecycleConfiguration | `aws s3api get-bucket-lifecycle-configuration` | ✅ |
| DeleteBucketLifecycleConfiguration | `aws s3api delete-bucket-lifecycle-configuration` | ✅ |
| PutBucketPolicy | `aws s3api put-bucket-policy` | ✅ |
| GetBucketPolicy | `aws s3api get-bucket-policy` | ✅ |
| DeleteBucketPolicy | `aws s3api delete-bucket-policy` | ✅ |
| PutBucketEncryption | `aws s3api put-bucket-encryption` | ✅ |
| GetBucketEncryption | `aws s3api get-bucket-encryption` | ✅ |
| DeleteBucketEncryption | `aws s3api delete-bucket-encryption` | ✅ |
| PutBucketLogging | `aws s3api put-bucket-logging` | ✅ |
| GetBucketLogging | `aws s3api get-bucket-logging` | ✅ |
| DeleteBucketLogging | `aws s3api delete-bucket-logging` | ✅ |
| PutBucketWebsite | `aws s3api put-bucket-website` | ✅ |
| GetBucketWebsite | `aws s3api get-bucket-website` | ✅ |
| DeleteBucketWebsite | `aws s3api delete-bucket-website` | ✅ |
| PutBucketNotification | `aws s3api put-bucket-notification-configuration` | ✅ |
| GetBucketNotification | `aws s3api get-bucket-notification-configuration` | ✅ |
| PutBucketReplication | `aws s3api put-bucket-replication` | ✅ |
| GetBucketReplication | `aws s3api get-bucket-replication` | ✅ |
| DeleteBucketReplication | `aws s3api delete-bucket-replication` | ✅ |
| PutBucketRequestPayment | `aws s3api put-bucket-request-payment` | ✅ |
| GetBucketRequestPayment | `aws s3api get-bucket-request-payment` | ✅ |
| PutBucketOwnershipControls | `aws s3api put-bucket-ownership-controls` | ✅ |
| GetBucketOwnershipControls | `aws s3api get-bucket-ownership-controls` | ✅ |
| DeleteBucketOwnershipControls | `aws s3api delete-bucket-ownership-controls` | ✅ |
| PutPublicAccessBlock | `aws s3api put-public-access-block` | ✅ |
| GetPublicAccessBlock | `aws s3api get-public-access-block` | ✅ |
| DeletePublicAccessBlock | `aws s3api delete-public-access-block` | ✅ |
| PutBucketAccelerateConfiguration | `aws s3api put-bucket-accelerate-configuration` | ✅ |
| GetBucketAccelerateConfiguration | `aws s3api get-bucket-accelerate-configuration` | ✅ |
| PutBucketAnalyticsConfiguration |  | ✅ |
| GetBucketAnalyticsConfiguration |  | ✅ |
| DeleteBucketAnalyticsConfiguration |  | ✅ |
| ListBucketAnalyticsConfigurations |  | ✅ |
| PutBucketInventoryConfiguration |  | ✅ |
| GetBucketInventoryConfiguration |  | ✅ |
| DeleteBucketInventoryConfiguration |  | ✅ |
| ListBucketInventoryConfigurations |  | ✅ |
| DeleteBucket | `aws s3api delete-bucket` | ✅ |

### Failure Tests

| Check | Status | Notes |
|---|---|---|
| GetBucketCors nonexistent | ✅ | Expected failure |
| GetBucketCors after delete | ✅ | Expected failure |
| GetBucketLifecycleConfiguration nonexistent | ✅ | Expected failure |
| GetBucketLifecycleConfiguration after delete | ✅ | Expected failure |
| GetBucketPolicy nonexistent | ✅ | Expected failure |
| GetBucketPolicy after delete | ✅ | Expected failure |
| GetBucketEncryption nonexistent | ✅ | Expected failure |
| GetBucketEncryption after delete | ✅ | Expected failure |
| GetBucketLogging nonexistent | ✅ | Expected failure |
| GetBucketLogging after delete | ✅ | Expected failure |
| GetBucketAnalyticsConfiguration nonexistent bucket | ✅ | Expected failure |
| GetBucketAnalyticsConfiguration missing id | ✅ | Expected failure |
| DeleteBucketAnalyticsConfiguration nonexistent bucket | ✅ | Expected failure |
| DeleteBucketAnalyticsConfiguration missing id | ✅ | Expected failure |
| PutBucketAnalyticsConfiguration nonexistent bucket | ✅ | Expected failure |
| ListBucketAnalyticsConfigurations nonexistent bucket | ✅ | Expected failure |
| GetBucketInventoryConfiguration nonexistent bucket | ✅ | Expected failure |
| GetBucketInventoryConfiguration missing id | ✅ | Expected failure |
| DeleteBucketInventoryConfiguration nonexistent bucket | ✅ | Expected failure |
| DeleteBucketInventoryConfiguration missing id | ✅ | Expected failure |
| PutBucketInventoryConfiguration nonexistent bucket | ✅ | Expected failure |
| ListBucketInventoryConfigurations nonexistent bucket | ✅ | Expected failure |

| Check | Status | Notes |
|---|---|---|
| GetObject nonexistent | ✅ | Expected failure |
| HeadObject nonexistent | ✅ | Expected failure |
| GetBucketLocation nonexistent | ✅ | Expected failure |
| GetBucketVersioning nonexistent | ✅ | Expected failure |
| GetBucketAcl nonexistent | ✅ | Expected failure |
| GetBucketTagging nonexistent | ✅ | Expected failure |
| CopyObject nonexistent source | ✅ | Expected failure |
| PutObject nonexistent bucket | ✅ | Expected failure |
| GetObjectAcl nonexistent | ✅ | Expected failure |

### Cucumber AWS CLI Test Status

| Category | Status | Note |
|---|---|---|
| `@webclient` scenarios (238) | ✅ Passing | Standard Cucumber via Java WebTestClient |
| `@awscli` scenarios | ⚠️ Only 1 scenario | Must reach parity with `@webclient` canonical suite (roadmap item P3/S3-P1+) |
| `test-aws-cli.sh` standalone | ⚠️ 46/82 passed | Independent script; most failures are connection errors (server not started at test time) |

## Roadmap

Operations not yet semantically implemented are tracked in `PLAN.md` under the *S3 API Semantic Completion Plan* (S3-P0 through S3-P4).
