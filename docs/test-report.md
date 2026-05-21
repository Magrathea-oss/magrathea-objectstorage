# Magrathea ObjectStorage Test Report

Generated: 2026-05-21T15:37:15+02:00

## Summary

| Suite | Passed | Failed | Total | Notes |
|---|---:|---:|---:|---|
| AWS CLI S3 compatibility | 33 | 0 | 33 | Endpoint: `http://localhost:18090` |
| Maven Surefire | See section | See section | See section | Latest reports under `*/target/surefire-reports` |
| Clover coverage | See section | - | - | Latest report under `target/site/clover` |

## AWS CLI S3 Compatibility

Bucket: `magrathea-cli-test-1779370622-46050`

| Check | Status | Notes |
|---|---|---|
| ListBuckets | ✅ Passed | Expected success |
| CreateBucket | ✅ Passed | Expected success |
| HeadBucket existing | ✅ Passed | Expected success |
| PutObject | ✅ Passed | Expected success |
| HeadObject existing | ✅ Passed | Expected success |
| PutObjectAcl | ✅ Passed | Expected success |
| GetObjectAcl | ✅ Passed | Expected success |
| PutObjectTagging | ✅ Passed | Expected success |
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
| PutBucketTagging | ✅ Passed | Expected success |
| GetBucketTagging | ✅ Passed | Expected success |
| DeleteBucketTagging | ✅ Passed | Expected success |
| GetBucketVersioning initial | ✅ Passed | Expected success |
| PutBucketVersioning Enabled | ✅ Passed | Expected success |
| GetBucketVersioning enabled | ✅ Passed | Expected success |
| ListObjectVersions | ✅ Passed | Expected success |
| CopyObject | ✅ Passed | Expected success |
| HeadObject copy existing | ✅ Passed | Expected success |
| DeleteObjects | ✅ Passed | Expected success |
| HeadObject copy after DeleteObjects | ✅ Passed | Expected failure |
| DeleteObject | ✅ Passed | Expected success |
| HeadObject after DeleteObject | ✅ Passed | Expected failure |
| DeleteBucket | ✅ Passed | Expected success |
| HeadBucket after DeleteBucket | ✅ Passed | Expected failure |

## Maven Surefire Results

| Module | Report | Tests | Failures | Errors | Skipped | Status |
|---|---|---:|---:|---:|---:|---|
| object-storage-domain | com.example.magrathea.objectstorage.domain.BucketTest.txt | 11 | 0 | 0 | 0 | ✅ Passed |
| object-storage-domain | com.example.magrathea.objectstorage.domain.ObjectKeyTest.txt | 6 | 0 | 0 | 0 | ✅ Passed |
| object-storage-domain | com.example.magrathea.objectstorage.domain.ObjectStorageEventTest.txt | 6 | 0 | 0 | 0 | ✅ Passed |
| object-storage-domain | com.example.magrathea.objectstorage.domain.RegionTest.txt | 4 | 0 | 0 | 0 | ✅ Passed |
| object-storage-domain | com.example.magrathea.objectstorage.domain.S3ObjectTest.txt | 8 | 0 | 0 | 0 | ✅ Passed |
| object-storage-domain | com.example.magrathea.objectstorage.domain.StorageClassTest.txt | 6 | 0 | 0 | 0 | ✅ Passed |
| s3-api | com.example.magrathea.s3api.cucumber.ObjectStorageCucumberTest.txt | 21 | 0 | 0 | 0 | ✅ Passed |
| **Total** |  | **62** | **0** | **0** | **0** | **✅ Passed** |

## Clover Coverage

| Metric | Covered | Total | Coverage |
|---|---:|---:|---:|
| Elements | 503 | 663 | 75.87% |
| Statements | 315 | 394 | 79.95% |
| Methods | 110 | 125 | 88.00% |
| Conditionals | 78 | 144 | 54.17% |
| NCLOC | - | 1474 | - |

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
| DeleteObject | `aws s3api delete-object` | ✅ |
| DeleteObjects | `aws s3api delete-objects` | ✅ |
| DeleteBucket | `aws s3api delete-bucket` | ✅ |

## Not Implemented Yet

Operations not listed above are intentionally outside the current implementation and are tracked in `PLAN.md`.
