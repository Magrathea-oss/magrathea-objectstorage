# Plan: Magrathea ObjectStorage — AWS S3 Compatible

## Status

Current implementation is a Java 21 + Spring Boot 4 WebFlux S3-compatible object storage. The only HTTP API is the AWS S3-compatible API exposed by the pluggable `s3-api` module.

## Maven Modules

```
magrathea-objectstorage/
├── pom.xml
├── s3-api/                         # Pluggable S3 HTTP adapter (RouterFunction, XML, AWS CLI tests)
├── object-storage-domain/          # Pure S3 domain: Bucket, S3Object, repository interfaces
├── object-storage-application/     # Application services, DTOs, S3ObjectWrite implementation with Flux<DataBuffer>
├── object-storage-infrastructure/  # Repository implementations only (BucketRepositoryImpl, InMemoryObjectRepository)
├── persistence-context-domain/     # EMPTY — reserved for future use
├── persistence-context-application/# EMPTY — reserved for future use
├── persistence-context-infrastructure/ # EMPTY — reserved for future use
├── bootstrap-application/          # Spring Boot entry point
├── docs/                           # ARC42, ADR, C4
└── test-aws-cli.sh                 # AWS CLI compatibility tests for implemented S3 operations
```

Removed modules/components:
- `shared-domain` removed.
- `InternalApiRouter` removed because it was not standard S3.
- `S3ObjectRepositoryImpl` renamed to `InMemoryObjectRepository` and no longer references persistence-context.

## S3 API Handler Organization

`s3-api` route mapping is intentionally split by context:

| Class | Responsibility |
|---|---|
| `S3ProxyRouter` | Route composition only |
| `S3BucketOperationsHandler` | Bucket lifecycle, bucket configuration, bucket-level listings |
| `S3ObjectOperationsHandler` | Object CRUD, copy, and multi-delete |
| `S3WebSupport` | Shared request predicates and S3 XML error helpers |

`ContentStore` has been removed. Object content is saved through `S3ObjectRepository.save(S3ObjectWrite)`. The domain-level `S3ObjectWrite` and `S3ObjectContent` interfaces expose no framework types; application implementations `DefaultS3ObjectWrite` and `DefaultS3ObjectContent` carry `Flux<DataBuffer>`. `InMemoryObjectRepository` casts the interfaces to the supported implementations internally and persists metadata plus content.

## Pluggable S3 API

`s3-api` is loaded through Spring Boot 4 auto-configuration:

- `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- `S3ApiConfig` guarded by `@ConditionalOnClass({ BucketService.class, ObjectService.class })`
- `S3ApiConfig` guarded by `s3.api.enabled=true` (`true` by default)

Activation modes:

| Mode | Behavior |
|---|---|
| `s3-api` dependency present + `s3.api.enabled=true` | S3 routes are active |
| `s3-api` dependency present + `s3.api.enabled=false` | S3 routes disabled |
| `s3-api` dependency absent | No S3 web API loaded |

## Testing Strategy

| Level | Type | Command | Notes |
|---|---|---|---|
| 1 | Pure JUnit | `mvn test -pl object-storage-domain` | Domain only, no Spring |
| 2 | Cucumber BDD | `mvn test -pl s3-api` | RouterFunction integration |
| 3 | AWS CLI compatibility | `bash test-aws-cli.sh` | Requires app running on `localhost:8080` |
| 4 | Clover coverage | `mvn -Pcoverage clover:setup test clover:aggregate clover:clover` | Generates Clover reports |
| 5 | AWS CLI Maven profile | `mvn -N verify -Paws-cli-tests` | Requires app running and AWS CLI installed |

Consolidated report: `docs/test-report.md` includes AWS CLI outcomes, Surefire/JUnit/Cucumber outcomes, and Clover coverage percentages.

## Coverage Tooling

Clover/OpenClover is configured in the parent POM under profile `coverage`.

Report command:

```bash
mvn -Pcoverage clover:setup test clover:aggregate clover:clover
```

Expected reports:

```text
target/site/clover/
```

AWS CLI compatibility tests are not run by the default `mvn test` because they require an external process (the boot application) and AWS CLI. They are available via `test-aws-cli.sh` and Maven profile `aws-cli-tests`.

## S3 API Operations — Coverage and Inclusion Plan

Source: https://docs.aws.amazon.com/AmazonS3/latest/API/API_Operations.md

Scope: **Amazon S3 actions only**. Amazon S3 Control actions are intentionally out of scope for the object-storage S3 REST API module.

### Current Implemented Operations (70/111)

| Operation | Endpoint | Test coverage |
|---|---|---|
| ListBuckets | `GET /` | Cucumber + AWS CLI |
| CreateBucket | `PUT /{bucket}` | Cucumber + AWS CLI |
| HeadBucket | `HEAD /{bucket}` | Cucumber + AWS CLI |
| DeleteBucket | `DELETE /{bucket}` | Cucumber + AWS CLI |
| ListObjects | `GET /{bucket}` | Cucumber + AWS CLI |
| PutObject | `PUT /{bucket}/{key}` | Cucumber + AWS CLI |
| GetObject | `GET /{bucket}/{key}` | Cucumber + AWS CLI |
| HeadObject | `HEAD /{bucket}/{key}` | Cucumber + AWS CLI |
| DeleteObject | `DELETE /{bucket}/{key}` | Cucumber + AWS CLI |
| ListObjectsV2 | `GET /{bucket}?list-type=2` | Cucumber + AWS CLI |
| CopyObject | `PUT /{bucket}/{key}` with `x-amz-copy-source` | Cucumber + AWS CLI |
| DeleteObjects | `POST /{bucket}?delete` | Cucumber + AWS CLI |
| GetBucketLocation | `GET /{bucket}?location` | Cucumber + AWS CLI |
| GetBucketVersioning | `GET /{bucket}?versioning` | Cucumber + AWS CLI |
| PutBucketVersioning | `PUT /{bucket}?versioning` | Cucumber + AWS CLI |
| ListObjectVersions | `GET /{bucket}?versions` | Cucumber + AWS CLI |
| GetObjectAcl | `GET /{bucket}/{key}?acl` | Cucumber + AWS CLI |
| PutObjectAcl | `PUT /{bucket}/{key}?acl` | Cucumber + AWS CLI |
| GetObjectTagging | `GET /{bucket}/{key}?tagging` | Cucumber + AWS CLI |
| PutObjectTagging | `PUT /{bucket}/{key}?tagging` | Cucumber + AWS CLI |
| DeleteObjectTagging | `DELETE /{bucket}/{key}?tagging` | Cucumber + AWS CLI |
| GetObjectAttributes | `GET /{bucket}/{key}?attributes` | Cucumber + AWS CLI |
| GetBucketAcl | `GET /{bucket}?acl` | Cucumber + AWS CLI |
| PutBucketAcl | `PUT /{bucket}?acl` | Cucumber + AWS CLI |
| GetBucketTagging | `GET /{bucket}?tagging` | Cucumber + AWS CLI |
| PutBucketTagging | `PUT /{bucket}?tagging` | Cucumber + AWS CLI |
| DeleteBucketTagging | `DELETE /{bucket}?tagging` | Cucumber + AWS CLI |
| GetBucketCors | `GET /{bucket}?cors` | Cucumber + AWS CLI |
| PutBucketCors | `PUT /{bucket}?cors` | Cucumber + AWS CLI |
| DeleteBucketCors | `DELETE /{bucket}?cors` | Cucumber + AWS CLI |
| GetBucketLifecycleConfiguration | `GET /{bucket}?lifecycle` | Cucumber + AWS CLI |
| PutBucketLifecycleConfiguration | `PUT /{bucket}?lifecycle` | Cucumber + AWS CLI |
| DeleteBucketLifecycleConfiguration | `DELETE /{bucket}?lifecycle` | Cucumber + AWS CLI |
| GetBucketPolicy | `GET /{bucket}?policy` | Cucumber + AWS CLI |
| PutBucketPolicy | `PUT /{bucket}?policy` | Cucumber + AWS CLI |
| DeleteBucketPolicy | `DELETE /{bucket}?policy` | Cucumber + AWS CLI |
| GetBucketEncryption | `GET /{bucket}?encryption` | Cucumber + AWS CLI |
| PutBucketEncryption | `PUT /{bucket}?encryption` | Cucumber + AWS CLI |
| DeleteBucketEncryption | `DELETE /{bucket}?encryption` | Cucumber + AWS CLI |
| GetBucketLogging | `GET /{bucket}?logging` | Cucumber + AWS CLI |
| PutBucketLogging | `PUT /{bucket}?logging` | Cucumber + AWS CLI |
| DeleteBucketLogging | `DELETE /{bucket}?logging` | Cucumber + AWS CLI |
| GetBucketWebsite | `GET /{bucket}?website` | Cucumber + AWS CLI |
| PutBucketWebsite | `PUT /{bucket}?website` | Cucumber + AWS CLI |
| DeleteBucketWebsite | `DELETE /{bucket}?website` | Cucumber + AWS CLI |
| GetBucketNotification | `GET /{bucket}?notification` | Cucumber + AWS CLI |
| PutBucketNotification | `PUT /{bucket}?notification` | Cucumber + AWS CLI |
| DeleteBucketNotification | `DELETE /{bucket}?notification` | Cucumber + AWS CLI |
| GetBucketReplication | `GET /{bucket}?replication` | Cucumber + AWS CLI |
| PutBucketReplication | `PUT /{bucket}?replication` | Cucumber + AWS CLI |
| DeleteBucketReplication | `DELETE /{bucket}?replication` | Cucumber + AWS CLI |
| GetBucketRequestPayment | `GET /{bucket}?requestPayment` | Cucumber + AWS CLI |
| PutBucketRequestPayment | `PUT /{bucket}?requestPayment` | Cucumber + AWS CLI |
| DeleteBucketRequestPayment | `DELETE /{bucket}?requestPayment` | Cucumber + AWS CLI |
| GetBucketOwnershipControls | `GET /{bucket}?ownershipControls` | Cucumber + AWS CLI |
| PutBucketOwnershipControls | `PUT /{bucket}?ownershipControls` | Cucumber + AWS CLI |
| DeleteBucketOwnershipControls | `DELETE /{bucket}?ownershipControls` | Cucumber + AWS CLI |
| GetPublicAccessBlock | `GET /{bucket}?publicAccessBlock` | Cucumber + AWS CLI |
| PutPublicAccessBlock | `PUT /{bucket}?publicAccessBlock` | Cucumber + AWS CLI |
| DeletePublicAccessBlock | `DELETE /{bucket}?publicAccessBlock` | Cucumber + AWS CLI |
| GetBucketAccelerateConfiguration | `GET /{bucket}?accelerate` | Cucumber + AWS CLI |
| PutBucketAccelerateConfiguration | `PUT /{bucket}?accelerate` | Cucumber + AWS CLI |
| DeleteBucketAccelerateConfiguration | `DELETE /{bucket}?accelerate` | Cucumber + AWS CLI |
| CreateMultipartUpload | `POST /{bucket}/{key}?uploads` | Cucumber |
| UploadPart | `PUT /{bucket}/{key}?uploadId=...&partNumber=...` | Cucumber |
| UploadPartCopy | `PUT /{bucket}/{key}?uploadId=...&partNumber=...` + `x-amz-copy-source` | RouterFunction |
| CompleteMultipartUpload | `POST /{bucket}/{key}?uploadId=...` | Cucumber |
| AbortMultipartUpload | `DELETE /{bucket}/{key}?uploadId=...` | Cucumber |
| ListMultipartUploads | `GET /{bucket}?uploads` | Cucumber |
| ListParts | `GET /{bucket}/{key}?uploadId=...` | Cucumber |

### Phase A — CLI-Baseline Compatibility (completed)

Goal: support common AWS CLI object workflows beyond current CRUD.

| Operation | Status |
|---|---|
| ListObjectsV2 | Implemented |
| CopyObject | Implemented |
| DeleteObjects | Implemented |
| GetBucketLocation | Implemented |
| GetBucketVersioning | Implemented |
| PutBucketVersioning | Implemented |
| ListObjectVersions | Implemented |

### Phase B — Object Metadata, Tagging, and ACL Compatibility (completed)

| Operation | Status |
|---|---|
| GetObjectAcl | Implemented |
| PutObjectAcl | Implemented |
| GetObjectTagging | Implemented |
| PutObjectTagging | Implemented |
| DeleteObjectTagging | Implemented |
| GetObjectAttributes | Implemented |
| GetBucketAcl | Implemented |
| PutBucketAcl | Implemented |
| GetBucketTagging | Implemented |
| PutBucketTagging | Implemented |
| DeleteBucketTagging | Implemented |

### Phase C — Multipart Upload (implemented)

| Operation | Endpoint | Test coverage |
|---|---|---|
| CreateMultipartUpload | POST /{bucket}/{key}?uploads | Cucumber |
| UploadPart | PUT /{bucket}/{key}?uploadId=...&partNumber=... | Cucumber |
| UploadPartCopy | PUT /{bucket}/{key}?uploadId=...&partNumber=... + x-amz-copy-source | Implemented, no test |
| CompleteMultipartUpload | POST /{bucket}/{key}?uploadId=... | Cucumber |
| AbortMultipartUpload | DELETE /{bucket}/{key}?uploadId=... | Cucumber |
| ListMultipartUploads | GET /{bucket}?uploads | Cucumber |
| ListParts | GET /{bucket}/{key}?uploadId=... | Cucumber |

### Phase D — Bucket Configuration APIs (completed)

| Area | Operations |
|---|---|
| CORS | GetBucketCors ✅, PutBucketCors ✅, DeleteBucketCors ✅ |
| Lifecycle | GetBucketLifecycle ✅, PutBucketLifecycle ✅, DeleteBucketLifecycle ✅ |
| Policy | GetBucketPolicy ✅, PutBucketPolicy ✅, DeleteBucketPolicy ✅, GetBucketPolicyStatus ⬜ |
| Encryption | GetBucketEncryption ✅, PutBucketEncryption ✅, DeleteBucketEncryption ✅ |
| Logging | GetBucketLogging ✅, PutBucketLogging ✅, DeleteBucketLogging ✅ |
| Website | GetBucketWebsite ✅, PutBucketWebsite ✅, DeleteBucketWebsite ✅ |
| Notification | GetBucketNotification ✅, PutBucketNotification ✅, DeleteBucketNotification ✅ |
| Replication | GetBucketReplication ✅, PutBucketReplication ✅, DeleteBucketReplication ✅ |
| Request Payment | GetBucketRequestPayment ✅, PutBucketRequestPayment ✅, DeleteBucketRequestPayment ✅ |
| Ownership Controls | GetBucketOwnershipControls ✅, PutBucketOwnershipControls ✅, DeleteBucketOwnershipControls ✅ |
| Public Access Block | GetPublicAccessBlock ✅, PutPublicAccessBlock ✅, DeletePublicAccessBlock ✅ |
| Accelerate | GetBucketAccelerateConfiguration ✅, PutBucketAccelerateConfiguration ✅, DeleteBucketAccelerateConfiguration ✅ |

### Phase E — Analytics, Inventory, Metrics, Intelligent-Tiering

| Area | Operations |
|---|---|
| Analytics | GetBucketAnalyticsConfiguration, PutBucketAnalyticsConfiguration, DeleteBucketAnalyticsConfiguration, ListBucketAnalyticsConfigurations |
| Inventory | GetBucketInventoryConfiguration, PutBucketInventoryConfiguration, DeleteBucketInventoryConfiguration, ListBucketInventoryConfigurations |
| Metrics | GetBucketMetricsConfiguration, PutBucketMetricsConfiguration, DeleteBucketMetricsConfiguration, ListBucketMetricsConfigurations |
| Intelligent-Tiering | GetBucketIntelligentTieringConfiguration, PutBucketIntelligentTieringConfiguration, DeleteBucketIntelligentTieringConfiguration, ListBucketIntelligentTieringConfigurations |

### Phase F — Advanced / Specialized Operations

| Operation |
|---|
| CreateSession |
| ListDirectoryBuckets |
| GetBucketAbac |
| PutBucketAbac |
| GetObjectLegalHold |
| PutObjectLegalHold |
| GetObjectLockConfiguration |
| PutObjectLockConfiguration |
| GetObjectRetention |
| PutObjectRetention |
| GetObjectTorrent |
| RestoreObject |
| SelectObjectContent |
| RenameObject |
| UpdateObjectEncryption |
| WriteGetObjectResponse |
| CreateBucketMetadataConfiguration |
| DeleteBucketMetadataConfiguration |
| GetBucketMetadataConfiguration |
| CreateBucketMetadataTableConfiguration |
| DeleteBucketMetadataTableConfiguration |
| GetBucketMetadataTableConfiguration |
| UpdateBucketMetadataInventoryTableConfiguration |
| UpdateBucketMetadataJournalTableConfiguration |

## Current API Coverage Analysis

See [`docs/api-coverage.md`](docs/api-coverage.md) for a detailed breakdown of every implemented operation,
including which HTTP headers and query parameters are:

- ✅ **Tested** — read and actively used in business logic
- ⬜ **Not implemented (hashtable)** — stored in ConcurrentHashMap only, not in domain/application
- 🔴 **Not implemented (ignored)** — completely ignored

## Implementation Rule for New S3 Operations

For every newly implemented S3 operation:

1. Add RouterFunction route in `s3-api`.
2. Use AWS S3 terminology only in domain/application DTOs.
3. Add XML response/request records using Jackson 3 annotations when XML is required.
4. Add Cucumber feature scenario(s) in `s3-api/src/test/features/object-storage/`.
5. Add AWS CLI coverage in `test-aws-cli.sh` if AWS CLI exposes the operation.
6. Update this PLAN coverage table.
7. Update ARC42 and ADRs if the operation introduces a new architectural decision.

### Domain / Application / Test Requirement

Every new S3 operation MUST include:

**Domain layer (`object-storage-domain`):**
- Add or update domain value objects / aggregates if the operation introduces new data concepts (e.g., ACL, tagging, storage class)
- Add pure JUnit tests in `object-storage-domain/src/test/` for every new domain type

**Application layer (`object-storage-application`):**
- Add or update application service methods in `BucketService` / `ObjectService`
- Add or update DTOs in `application/dto/`
- Data MUST flow through domain → application → handler, NOT be stored directly in handler `ConcurrentHashMap`

**Tests:**
- At least 1 success scenario + at least 1 failure scenario per operation in Cucumber
- Failure scenarios must cover every error case documented in the AWS S3 API spec (e.g., NoSuchBucket, NoSuchKey, BucketAlreadyExists, InvalidArgument)
- AWS CLI failure tests for every error case that AWS CLI exposes

### Coverage Verification

```bash
# Verify success + failure coverage
mvn test -pl s3-api
bash test-aws-cli.sh

# Verify domain unit tests
mvn test -pl object-storage-domain
```

## Verification

```bash
mvn test
mvn -Pcoverage clover:setup test clover:aggregate clover:clover

# In one shell
java -jar bootstrap-application/target/bootstrap-application-1.0.0-SNAPSHOT.jar

# In another shell
bash test-aws-cli.sh
# or
mvn -N verify -Paws-cli-tests
```
