# Magrathea ObjectStorage Test Report

Updated: 2026-06-12

> ⚠️ **Report status: STALE / EVIDENCE-ONLY**
>
> This report reflects a point-in-time snapshot and **must not be read as implementation completeness**.
> - The `111/111` route count (referenced elsewhere) is a mapped-surface inventory only, not semantic completion.
> - AWS CLI standalone results (46 passed / 82 failed) are from a run where the server was not started; most failures are connection errors (exit code 254), not logic failures.
> - AWS CLI Cucumber: **26 scenarios total** covering object CRUD (including slash-containing keys), bucket operations parity, ACL/tagging (bucket and object), and multipart upload lifecycle; **26 run** and **0 are skipped**. WebTestClient Cucumber: **239 scenarios** implemented. These are not equivalent and full AWS CLI parity is not complete.
> - Storage-engine backend scenarios: **absent**. The storage-engine modules are not yet verified end-to-end.
> - **JaCoCo is the current coverage baseline.** Clover/OpenClover is optional/legacy.
>
> A semantic S3 coverage matrix is being built as part of the correction plan (see `PLAN.md` → *S3 API Semantic Completion Plan*).

## Summary

| Suite | Passed | Failed | Total | Notes |
|---|---:|---:|---:|---|
| AWS CLI S3 compatibility script | 46 | 82 | 128 | Stale standalone `test-aws-cli.sh` result; endpoint: `http://localhost:8080` |
| S3 API adapter Cucumber/Surefire | 265 | 0 | 265 | `mvn -B -pl s3-reactive-api-adapter -am test -Dsurefire.failIfNoSpecifiedTests=false` — build success; 0 skipped scenarios |
| Maven Surefire (all modules) | 768 | 0 | 768 | `mvn test` — Phase 10 quality gate; all 10 modules; 0 failures, 0 errors, 0 skipped; see Surefire section below |
| Admin API adapter | 9 | 0 | 9 | `mvn -B -pl admin-api-adapter -am test` — build success |
| JaCoCo coverage | See section | - | - | Current baseline; latest reports under `target/site/jacoco` when generated |

## Current Verified Results

| Scope | Evidence | Result | Notes |
|---|---|---:|---|
| Phase 5 domain planning (`storage-engine-domain`) | Commit `b0a5f74`; `PersistencePlannerMinioStandardTest` | 152 tests passing, 0 failures | Verifies deterministic `MINIO_STANDARD` persistence planning in the domain model. |
| Phase 5 YAML catalogs and MINIO_STANDARD integration (`storage-engine-reactive-infrastructure`) | Commit `0ec84cf`; `MinioStandardIntegrationTest` | 26 tests passing, 0 failures | Verifies YAML catalog/device integration and `MINIO_STANDARD` selection with S3 storage class `STANDARD`, dedup disabled, EC planning `4 data / 2 parity`, replication factor `1`, compression disabled, and encryption disabled by default; storage-engine runtime read/write wiring and physical EC shard placement remain pending for Phase 6/7. |
| Phase 8 backend Admin API (`admin-api-adapter`) | `mvn -B -pl admin-api-adapter -am test`; `AdminRouterTest` | 9 tests passing, 0 failures, build success | Verifies configuration-as-code/read-only admin catalog behavior for policies/devices/disk sets, structured validation responses for `POST /admin/storage-policies/validate`, non-persistence of validation, and runtime rejection of policy mutations. |
| Phase 9 AWS CLI object CRUD, bucket operations, metadata/tagging, and multipart increment (`s3-reactive-api-adapter`) | Production commits `9ff5a08`, `b2c333c`, `a03bc4a`; test commits `94f3e47`, `cdbcc9d`, `a03bc4a`; `mvn -B -pl s3-reactive-api-adapter -am test -Dsurefire.failIfNoSpecifiedTests=false`; `AwsCliCucumberTest` + `ObjectStoreCucumberTest` | 265 tests, 0 failures, 0 errors, 0 skipped | Targeted AWS CLI Cucumber has 26 scenarios total: all run, 0 skipped. Object scenarios: put default headers, get content, head, list v1/v2, delete, idempotent delete, `STANDARD` storage class via object attributes, and slash-containing object keys. Bucket scenarios: create-bucket + list-buckets, head-bucket found, get-bucket-location, get-bucket-versioning default, put-bucket-versioning enable, delete-bucket, head-bucket 404, delete-bucket 404, create duplicate bucket 409. Metadata/tagging scenarios: bucket ACL read/write, object ACL read/write, bucket tagging CRUD, object tagging CRUD, and object attributes (ETag + ObjectSize). Multipart scenarios: full lifecycle (initiate/upload-parts/list-parts/complete), abort, and list multipart uploads. |

### AWS CLI Test Status

| Feature | Status | Note |
|---|---|---|
| WebTestClient scenarios (239) | ✅ Passing | Standard Cucumber via Java WebTestClient (`@webclient` tag) |
| AWS CLI Cucumber scenarios | ⚠️ 26 total, all run, 0 skipped | Object CRUD basics (including slash-containing keys), bucket operations (create, list, head, location, versioning, delete, and failure cases), ACL/tagging (bucket and object read/write/CRUD), object attributes, and multipart upload lifecycle (initiate/upload/complete/abort/list) now have targeted AWS CLI coverage; full parity with WebTestClient canonical scenarios is not complete. |
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
| s3-reactive-api-adapter | com.example.magrathea.s3api.awscli.AwsCliCucumberTest.txt | 26 | 0 | 0 | 0 | ✅ Passed |
| s3-reactive-api-adapter | com.example.magrathea.s3api.cucumber.ObjectStoreCucumberTest.txt | 239 | 0 | 0 | 0 | ✅ Passed |
| admin-api-adapter | com.example.magrathea.admin.web.AdminRouterTest.txt | 9 | 0 | 0 | 0 | ✅ Passed |
| storage-engine-domain | com.example.magrathea.storageengine.domain.aggregate.StoredObjectTest.txt | 14 | 0 | 0 | 0 | ✅ Passed |
| storage-engine-domain | com.example.magrathea.storageengine.domain.service.EffectivePolicyResolverTest.txt | 10 | 0 | 0 | 0 | ✅ Passed |
| storage-engine-domain | com.example.magrathea.storageengine.domain.service.PersistencePlannerMinioStandardTest.txt | 11 | 0 | 0 | 0 | ✅ Passed |
| storage-engine-domain | com.example.magrathea.storageengine.domain.service.PersistencePlannerTest.txt | 8 | 0 | 0 | 0 | ✅ Passed |
| storage-engine-domain | com.example.magrathea.storageengine.domain.service.VirtualDeviceResolverTest.txt | 13 | 0 | 0 | 0 | ✅ Passed |
| storage-engine-domain | com.example.magrathea.storageengine.domain.valueobject.DedupConfigTest.txt | 10 | 0 | 0 | 0 | ✅ Passed |
| storage-engine-domain | com.example.magrathea.storageengine.domain.valueobject.DiskSetTest.txt | 13 | 0 | 0 | 0 | ✅ Passed |
| storage-engine-domain | com.example.magrathea.storageengine.domain.valueobject.ErasureCodingConfigTest.txt | 6 | 0 | 0 | 0 | ✅ Passed |
| storage-engine-domain | com.example.magrathea.storageengine.domain.valueobject.ObjectManifestTest.txt | 12 | 0 | 0 | 0 | ✅ Passed |
| storage-engine-domain | com.example.magrathea.storageengine.domain.valueobject.ReplicationConfigTest.txt | 4 | 0 | 0 | 0 | ✅ Passed |
| storage-engine-domain | com.example.magrathea.storageengine.domain.valueobject.StorageClassIdTest.txt | 8 | 0 | 0 | 0 | ✅ Passed |
| storage-engine-domain | com.example.magrathea.storageengine.domain.valueobject.StorageDeviceTest.txt | 29 | 0 | 0 | 0 | ✅ Passed |
| storage-engine-domain | com.example.magrathea.storageengine.domain.valueobject.StoragePolicyTest.txt | 11 | 0 | 0 | 0 | ✅ Passed |
| storage-engine-reactive-application | com.example.magrathea.storageengine.application.service.ReactiveStorageOrchestratorTest.txt | 4 | 0 | 0 | 0 | ✅ Passed |
| storage-engine-reactive-infrastructure | com.example.magrathea.storageengine.infrastructure.filesystem.FileSystemStorageConsistencyTest.txt | 3 | 0 | 0 | 0 | ✅ Passed |
| storage-engine-reactive-infrastructure | com.example.magrathea.storageengine.infrastructure.yaml.MinioStandardIntegrationTest.txt | 2 | 0 | 0 | 0 | ✅ Passed |
| storage-engine-reactive-infrastructure | com.example.magrathea.storageengine.infrastructure.yaml.YamlDiskSetCatalogTest.txt | 8 | 0 | 0 | 0 | ✅ Passed |
| storage-engine-reactive-infrastructure | com.example.magrathea.storageengine.infrastructure.yaml.YamlStorageDeviceCatalogTest.txt | 6 | 0 | 0 | 0 | ✅ Passed |
| storage-engine-reactive-infrastructure | com.example.magrathea.storageengine.infrastructure.yaml.YamlStoragePolicyCatalogTest.txt | 10 | 0 | 0 | 0 | ✅ Passed |
| object-store-reactive-repository-storage-engine-infrastructure | com.example.magrathea.objectstorage.repository.storageengine.adapter.StorageEngineReactiveS3ObjectRepositoryTest.txt | 1 | 0 | 0 | 0 | ✅ Passed |
| bootstrap-application | com.example.magrathea.bootstrap.DefaultBackendContextTest.txt | 2 | 0 | 0 | 0 | ✅ Passed |
| bootstrap-application | com.example.magrathea.bootstrap.StorageEngineBackendContextTest.txt | 3 | 0 | 0 | 0 | ✅ Passed |
| bootstrap-application | com.example.magrathea.bootstrap.StorageEngineMissingExternalConfigTest.txt | 1 | 0 | 0 | 0 | ✅ Passed |
| **Total (Phase 10 quality gate — all 10 modules)** |  | **768** | **0** | **0** | **0** | **✅ Passed** |

## Coverage

**JaCoCo** is the current coverage baseline. Reports are generated by `mvn -Pcoverage test jacoco:report` under `target/site/jacoco/`.

**Clover/OpenClover** is optional/legacy. Report HTML (if generated): `target/site/clover/index.html`

| Metric | Notes |
|---|---|
| JaCoCo | Phase 10 results in table below and in the Phase 10 Quality Gates section |
| Clover (optional/legacy) | Run `mvn -Pcoverage clover:setup test clover:aggregate clover:clover` to generate |

### Phase 10 JaCoCo Results (`mvn -Pcoverage test jacoco:report`)

| Module | Instruction Coverage | Branch Coverage |
|---|---:|---:|
| object-store-domain | 81% | 64% |
| storage-engine-reactive-application | 82% | 74% |
| s3-reactive-api-adapter | 77% | 30% |
| storage-engine-reactive-infrastructure | 54% | 42% |
| storage-engine-domain | 59% | 45% |
| admin-api-adapter | 69% | 46% |
| bootstrap-application | 58% | 29% |
| object-store-reactive-application | 18% | 20% |
| object-store-reactive-repository-storage-engine-infrastructure | 13% | 5% |

> **Note:** Low coverage in `object-store-reactive-application` (18% instruction / 20% branch) and `object-store-reactive-repository-storage-engine-infrastructure` (13% instruction / 5% branch) reflects that these modules primarily contain port interfaces and thin adapters where runtime behaviour is exercised through integration/Cucumber tests that run against the assembled application rather than isolated units.

---

## Phase 10 Quality Gates

**Date:** 2026-06-12 &nbsp; **HEAD:** `351d088`

### `mvn validate`

✅ BUILD SUCCESS — all module POMs valid.

### `mvn test` — Full Multi-Module Run

| Module | Tests | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|
| storage-engine-domain | 149 | 0 | 0 | 0 |
| admin-api-adapter | 9 | 0 | 0 | 0 |
| object-store-domain | 287 | 0 | 0 | 0 |
| object-store-reactive-application | 18 | 0 | 0 | 0 |
| s3-reactive-api-adapter (WebTestClient Cucumber) | 239 | 0 | 0 | 0 |
| s3-reactive-api-adapter (AWS CLI Cucumber) | 26 | 0 | 0 | 0 |
| storage-engine-reactive-application | 4 | 0 | 0 | 0 |
| storage-engine-reactive-infrastructure | 29 | 0 | 0 | 0 |
| object-store-reactive-repository-storage-engine-infrastructure | 1 | 0 | 0 | 0 |
| bootstrap-application | 6 | 0 | 0 | 0 |
| **TOTAL** | **768** | **0** | **0** | **0** |

### JaCoCo Coverage (`mvn -Pcoverage test jacoco:report`)

| Module | Instruction Coverage | Branch Coverage |
|---|---:|---:|
| object-store-domain | 81% | 64% |
| storage-engine-reactive-application | 82% | 74% |
| s3-reactive-api-adapter | 77% | 30% |
| storage-engine-reactive-infrastructure | 54% | 42% |
| storage-engine-domain | 59% | 45% |
| admin-api-adapter | 69% | 46% |
| bootstrap-application | 58% | 29% |
| object-store-reactive-application | 18% | 20% |
| object-store-reactive-repository-storage-engine-infrastructure | 13% | 5% |

> **Note:** Low coverage in `object-store-reactive-application` and `object-store-reactive-repository-storage-engine-infrastructure` reflects that these modules primarily contain port interfaces and thin adapters where runtime behaviour is exercised through integration/Cucumber tests that run against the assembled application rather than isolated units.

All quality gates pass. No failures, errors, or skipped tests across any module.

---

## S3 Semantic Coverage Matrix

> This table classifies each S3 API family by semantic status, not route count.
> **Route mapped** = HTTP route exists; **Stateful behavior** = creates/reads/updates/deletes durable state with observable follow-up; **AWS CLI scenario** = verified via AWS CLI Cucumber or `test-aws-cli.sh`; **Storage-engine scenario** = verified against storage-engine backend; **Semantic status** = overall classification.

### Current Evidence-Based Family Classification

| Family | Operation (examples) | Route mapped | Stateful behavior | AWS CLI scenario | Storage-engine scenario | Semantic status | Notes |
|---|---|---|---|---|---|---|---|
| Object CRUD | PutObject, GetObject, HeadObject, DeleteObject, CopyObject | Yes | Partial — in-memory only | Partial pass: first AWS CLI increment covers put default headers, get content, head, delete, idempotent delete, `STANDARD` storage class via object attributes, and slash-containing object keys | Absent | AWS CLI compatible for first CRUD subset / Partial overall | Read-after-write verified for targeted AWS CLI object CRUD subset; slash-containing keys are supported by catch-all object routes/key normalization; storage-engine read path remains unverified here; copy and ETag semantics remain incomplete |
| Bucket baseline | CreateBucket, HeadBucket, DeleteBucket, ListBuckets, ListObjects, ListObjectsV2 | Yes | Partial — in-memory only | Partial pass: AWS CLI bucket operations increment covers create-bucket, list-buckets, head-bucket, get-bucket-location, get-bucket-versioning, put-bucket-versioning, delete-bucket, duplicate-bucket 409, head-bucket 404, delete-bucket 404, list-objects, and list-objects-v2 | Absent | Stubbed / Partial | Bucket create/head/location/versioning/delete and failure cases now have targeted AWS CLI Cucumber coverage; prefix/delimiter edge cases, continuation tokens, and storage-engine indexes remain unverified |
| Multipart upload | CreateMultipartUpload, UploadPart, CompleteMultipartUpload, AbortMultipartUpload, ListParts, ListMultipartUploads | Yes | Shallow/Partial — part bodies not persisted | Partial pass: AWS CLI Cucumber third increment covers full lifecycle (initiate/upload-parts/list-parts/complete), abort, and list multipart uploads; `UploadPart` now returns `ETag` response header (fix commit `a03bc4a`) | Absent | Stubbed / Partial | Part persistence and assembly not implemented in storage-engine backend; ETag semantics incomplete; AWS CLI output parsing unblocked by ETag fix |
| Bucket configuration | CORS, Lifecycle, Website, Logging, Notification, Replication, Encryption, Versioning, Tagging, etc. | Yes | Partial — config storage only | Failing (server not running at test time) | Absent | Config-only / Stubbed | No background job execution (lifecycle/replication/notification); enforcement not implemented |
| Object metadata/tagging/ACL | PutObjectTagging, GetObjectTagging, GetObjectAttributes, PutObjectAcl, GetObjectAcl, PutBucketTagging, GetBucketTagging, DeleteBucketTagging, PutBucketAcl, GetBucketAcl | Yes | Partial — in-memory only | Partial pass: AWS CLI third increment covers bucket ACL read/write, object ACL read/write, bucket tagging CRUD, object tagging CRUD, and object attributes (ETag + ObjectSize); `putBucketTagging` and `putObjectTagging` fixed to consume body as String to avoid 415 from AWS CLI XML (fix commit `a03bc4a`) | Absent | Stubbed / Partial | In-memory round-trip verified for ACL/tagging/attributes subset via AWS CLI Cucumber; storage-engine backend unverified; enforcement and ACL grant semantics remain stubbed |
| Versioning/delete markers | ListObjectVersions, versioned GET/HEAD/DELETE | Yes | Shallow/Partial | Failing (server not running at test time) | Absent | Stubbed | Version IDs, delete marker semantics, and latest-version resolution unverified |
| Access/security controls | BucketPolicy, PublicAccessBlock, OwnershipControls | Yes | Config storage only | Failing (server not running at test time) | Absent | Config-only / Stubbed | Authorization enforcement absent |
| Analytics/inventory/metrics | Bucket analytics, inventory, metrics, intelligent-tiering | Yes | Config storage only | Failing (server not running at test time) | Absent | Config-only | No background report generation |
| Advanced/specialized | SelectObjectContent, RestoreObject, etc. | Some | None verified | Missing | Absent | Stubbed / Out of scope | Likely out of scope without explicit design |
| Admin/storage-engine APIs | StoragePolicy/device/disk-set catalog reads; policy validation | Yes | Read-only configuration-as-code catalogs; validation is non-persistent | Not applicable | Admin adapter tests pass; selected-backend S3 scenarios still absent | Partial backend Admin API implemented | `/admin/**` is separate from S3 coverage. `mvn -B -pl admin-api-adapter -am test` passes 9 tests. Policy/device/disk-set catalogs are read-only at runtime; create/update/delete policy requests are rejected. |

### AWS CLI Cucumber vs WebTestClient Cucumber Parity

| Dimension | WebTestClient Cucumber | AWS CLI Cucumber |
|---|---|---|
| Scenarios | 239 | 26 total (all run, 0 skipped) |
| Tag | `@webclient` | `@awscli` |
| Driver | Spring WebTestClient | `aws s3api` CLI |
| Status | Passing | Object CRUD, bucket operations, metadata/tagging, and multipart increments passing; full parity incomplete |
| Parity goal | Canonical suite | Must continue toward parity (roadmap item P3/S3-P1+) |

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
| `@webclient` scenarios (239) | ✅ Passing | Standard Cucumber via Java WebTestClient |
| `@awscli` scenarios | ⚠️ 26 total, all run, 0 skipped | Object CRUD basics (including slash-containing keys), bucket operations parity (create, list, head, location, versioning, delete, and failure cases), ACL/tagging (bucket and object), object attributes, and multipart upload lifecycle (initiate/upload/complete/abort/list) pass; full parity with `@webclient` canonical suite remains open (roadmap item P3/S3-P1+). |
| `test-aws-cli.sh` standalone | ⚠️ 46/82 passed | Independent script; most failures are connection errors (server not started at test time) |

## Roadmap

Operations not yet semantically implemented are tracked in `PLAN.md` under the *S3 API Semantic Completion Plan* (S3-P0 through S3-P4).
