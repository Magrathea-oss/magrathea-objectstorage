# Magrathea ObjectStore Test Report

Generated: 2026-05-24T15:14:59+02:00

## Summary

| Suite | Passed | Failed | Total | Notes |
|---|---:|---:|---:|---|
| AWS CLI S3 compatibility | 83 | 37 | 120 | Endpoint: `http://localhost:8080` |
| Maven Surefire | See section | See section | See section | Latest reports under `*/target/surefire-reports` |
| Clover coverage | See section | - | - | Latest report under `target/site/clover` |

## AWS CLI S3 Compatibility

Bucket: `magrathea-cli-test-1779628454-199892`

| Check | Status | Notes |
|---|---|---|
| ListBuckets | ✅ Passed | Expected success |
| CreateBucket | ✅ Passed | Expected success |
| HeadBucket existing | ✅ Passed | Expected success |
| PutObject | ✅ Passed | Expected success |
| HeadObject existing | ✅ Passed | Expected success |
| PutObjectAcl | ✅ Passed | Expected success |
| GetObjectAcl | ✅ Passed | Expected success |
| PutObjectTagging | ❌ Failed | Expected success, exit code 254 |
| GetObjectTagging | ✅ Passed | Expected success |
| DeleteObjectTagging | ✅ Passed | Expected success |
| GetObjectAttributes | ✅ Passed | Expected success |
| GetObject | ✅ Passed | Expected success |
| GetObject content matches | ✅ Passed | Expected success |
| ListObjects | ✅ Passed | Expected success |
| ListObjectsV2 | ✅ Passed | Expected success |
| GetBucketLocation | ✅ Passed | Expected success |
| PutBucketAcl | ✅ Passed | Expected success |
| GetBucketAcl | ✅ Passed | Expected success |
| PutBucketTagging | ❌ Failed | Expected success, exit code 254 |
| GetBucketTagging | ✅ Passed | Expected success |
| DeleteBucketTagging | ✅ Passed | Expected success |
| GetBucketVersioning initial | ✅ Passed | Expected success |
| PutBucketVersioning Enabled | ❌ Failed | Expected success, exit code 254 |
| GetBucketVersioning enabled | ✅ Passed | Expected success |
| ListObjectVersions | ✅ Passed | Expected success |
| CopyObject | ✅ Passed | Expected success |
| HeadObject copy existing | ✅ Passed | Expected success |
| DeleteObjects | ❌ Failed | Expected success, exit code 254 |
| HeadObject copy after DeleteObjects | ❌ Failed | Expected failure, command succeeded |
| PutObject PARANOIC_MODE | ✅ Passed | Expected success |
| HeadObject PARANOIC_MODE | ✅ Passed | Expected success |
| GetObjectAttributes PARANOIC_MODE | ✅ Passed | Expected success |
| DeleteObject PARANOIC_MODE | ✅ Passed | Expected success |
| DeleteObject | ✅ Passed | Expected success |
| HeadObject after DeleteObject | ✅ Passed | Expected failure |
| PutBucketCors | ❌ Failed | Expected success, exit code 254 |
| GetBucketCors | ❌ Failed | Expected success, exit code 254 |
| DeleteBucketCors | ✅ Passed | Expected success |
| GetBucketCors nonexistent | ✅ Passed | Expected failure |
| GetBucketCors after delete | ✅ Passed | Expected failure |
| PutBucketLifecycleConfiguration | ❌ Failed | Expected success, exit code 254 |
| GetBucketLifecycleConfiguration | ❌ Failed | Expected success, exit code 254 |
| DeleteBucketLifecycleConfiguration | ❌ Failed | Expected success, exit code 252 |
| GetBucketLifecycleConfiguration nonexistent | ✅ Passed | Expected failure |
| GetBucketLifecycleConfiguration after delete | ✅ Passed | Expected failure |
| PutBucketPolicy | ✅ Passed | Expected success |
| GetBucketPolicy | ✅ Passed | Expected success |
| DeleteBucketPolicy | ✅ Passed | Expected success |
| GetBucketPolicy nonexistent | ✅ Passed | Expected failure |
| GetBucketPolicy after delete | ✅ Passed | Expected failure |
| PutBucketEncryption | ❌ Failed | Expected success, exit code 252 |
| GetBucketEncryption | ❌ Failed | Expected success, exit code 254 |
| DeleteBucketEncryption | ✅ Passed | Expected success |
| GetBucketEncryption nonexistent | ✅ Passed | Expected failure |
| GetBucketEncryption after delete | ✅ Passed | Expected failure |
| PutBucketLogging | ❌ Failed | Expected success, exit code 252 |
| GetBucketLogging | ❌ Failed | Expected success, exit code 254 |
| DeleteBucketLogging | ❌ Failed | Expected success, exit code 252 |
| GetBucketLogging nonexistent | ✅ Passed | Expected failure |
| GetBucketLogging after delete | ✅ Passed | Expected failure |
| PutBucketWebsite | ❌ Failed | Expected success, exit code 254 |
| GetBucketWebsite | ❌ Failed | Expected success, exit code 254 |
| DeleteBucketWebsite | ✅ Passed | Expected success |
| GetBucketWebsite nonexistent | ✅ Passed | Expected failure |
| GetBucketWebsite after delete | ✅ Passed | Expected failure |
| PutBucketNotification | ❌ Failed | Expected success, exit code 254 |
| GetBucketNotification | ❌ Failed | Expected success, exit code 254 |
| GetBucketNotification nonexistent | ✅ Passed | Expected failure |
| PutBucketReplication | ❌ Failed | Expected success, exit code 254 |
| GetBucketReplication | ❌ Failed | Expected success, exit code 254 |
| DeleteBucketReplication | ✅ Passed | Expected success |
| GetBucketReplication nonexistent | ✅ Passed | Expected failure |
| GetBucketReplication after delete | ✅ Passed | Expected failure |
| PutBucketRequestPayment | ❌ Failed | Expected success, exit code 254 |
| GetBucketRequestPayment | ❌ Failed | Expected success, exit code 254 |
| GetBucketRequestPayment nonexistent | ✅ Passed | Expected failure |
| PutBucketOwnershipControls | ❌ Failed | Expected success, exit code 254 |
| GetBucketOwnershipControls | ❌ Failed | Expected success, exit code 254 |
| DeleteBucketOwnershipControls | ✅ Passed | Expected success |
| GetBucketOwnershipControls nonexistent | ✅ Passed | Expected failure |
| GetBucketOwnershipControls after delete | ✅ Passed | Expected failure |
| PutPublicAccessBlock | ❌ Failed | Expected success, exit code 254 |
| GetPublicAccessBlock | ❌ Failed | Expected success, exit code 254 |
| DeletePublicAccessBlock | ✅ Passed | Expected success |
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
| DeleteBucket | ✅ Passed | Expected success |
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
| object-store-domain | com.example.magrathea.objectstore.domain.BucketConfigurationTest.txt | 3 | 0 | 0 | 0 | ✅ Passed |
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
| object-store-domain | com.example.magrathea.objectstore.domain.BucketTest.txt | 11 | 0 | 0 | 0 | ✅ Passed |
| object-store-domain | com.example.magrathea.objectstore.domain.BucketWebsiteConfigurationTest.txt | 4 | 0 | 0 | 0 | ✅ Passed |
| object-store-domain | com.example.magrathea.objectstore.domain.CorsConfigurationTest.txt | 7 | 0 | 0 | 0 | ✅ Passed |
| object-store-domain | com.example.magrathea.objectstore.domain.MultipartUploadTest.txt | 9 | 0 | 0 | 0 | ✅ Passed |
| object-store-domain | com.example.magrathea.objectstore.domain.ObjectKeyTest.txt | 6 | 0 | 0 | 0 | ✅ Passed |
| object-store-domain | com.example.magrathea.objectstore.domain.ObjectStoreEventTest.txt | 6 | 0 | 0 | 0 | ✅ Passed |
| object-store-domain | com.example.magrathea.objectstore.domain.PartNumberTest.txt | 4 | 0 | 0 | 0 | ✅ Passed |
| object-store-domain | com.example.magrathea.objectstore.domain.PublicAccessBlockConfigurationTest.txt | 3 | 0 | 0 | 0 | ✅ Passed |
| object-store-domain | com.example.magrathea.objectstore.domain.RegionTest.txt | 4 | 0 | 0 | 0 | ✅ Passed |
| object-store-domain | com.example.magrathea.objectstore.domain.S3ObjectTest.txt | 8 | 0 | 0 | 0 | ✅ Passed |
| object-store-domain | com.example.magrathea.objectstore.domain.StorageClassTest.txt | 7 | 0 | 0 | 0 | ✅ Passed |
| object-store-domain | com.example.magrathea.objectstore.domain.UploadIdTest.txt | 4 | 0 | 0 | 0 | ✅ Passed |
| object-store-domain | com.example.magrathea.objectstore.domain.UploadPartTest.txt | 4 | 0 | 0 | 0 | ✅ Passed |
| s3-reactive-api-adapter | com.example.magrathea.s3api.cucumber.ObjectStoreCucumberTest.txt | 216 | 0 | 0 | 0 | ✅ Passed |
| **Total** |  | **345** | **0** | **0** | **0** | **✅ Passed** |

## Clover Coverage

| Metric | Covered | Total | Coverage |
|---|---:|---:|---:|
| Elements | 1524 | 1857 | 82.07% |
| Statements | 968 | 1159 | 83.52% |
| Methods | 305 | 348 | 87.64% |
| Conditionals | 251 | 350 | 71.71% |
| NCLOC | - | 4297 | - |

Report HTML: `target/site/clover/index.html`

## Implemented S3 Operation Coverage

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

## Not Implemented Yet

Operations not listed above are intentionally outside the current implementation and are tracked in `PLAN.md`.
