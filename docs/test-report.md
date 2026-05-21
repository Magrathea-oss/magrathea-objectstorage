# Magrathea ObjectStorage Test Report

Generated: 2026-05-21T14:27:47+02:00

## Summary

| Suite | Passed | Failed | Total | Notes |
|---|---:|---:|---:|---|
| AWS CLI S3 compatibility | 12 | 0 | 12 | Endpoint: `http://localhost:18083` |
| Maven Surefire | See section | See section | See section | Latest reports under `*/target/surefire-reports` |
| Clover coverage | See section | - | - | Latest report under `target/site/clover` |

## AWS CLI S3 Compatibility

Bucket: `magrathea-cli-test-1779366462-36159`

| Check | Status | Notes |
|---|---|---|
| ListBuckets | ✅ Passed | Expected success |
| CreateBucket | ✅ Passed | Expected success |
| HeadBucket existing | ✅ Passed | Expected success |
| PutObject | ✅ Passed | Expected success |
| HeadObject existing | ✅ Passed | Expected success |
| GetObject | ✅ Passed | Expected success |
| GetObject content matches | ✅ Passed | Expected success |
| ListObjects | ✅ Passed | Expected success |
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
| s3-api | com.example.magrathea.s3api.cucumber.ObjectStorageCucumberTest.txt | 9 | 0 | 0 | 0 | ✅ Passed |
| **Total** |  | **50** | **0** | **0** | **0** | **✅ Passed** |

## Clover Coverage

| Metric | Covered | Total | Coverage |
|---|---:|---:|---:|
| Elements | 260 | 344 | 75.58% |
| Statements | 164 | 213 | 77.00% |
| Methods | 64 | 77 | 83.12% |
| Conditionals | 32 | 54 | 59.26% |
| NCLOC | - | 857 | - |

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
| ListObjects | `aws s3api list-objects` | ✅ |
| DeleteObject | `aws s3api delete-object` | ✅ |
| DeleteBucket | `aws s3api delete-bucket` | ✅ |

## Not Implemented Yet

Operations not listed above are intentionally outside the current implementation and are tracked in `PLAN.md`.
