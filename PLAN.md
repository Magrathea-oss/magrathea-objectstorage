# Plan: Magrathea ObjectStorage — AWS S3 Compatible

## Status

Current implementation is a Java 21 + Spring Boot 4 WebFlux S3-compatible object storage. The only HTTP API is the AWS S3-compatible API exposed by the pluggable `s3-api` module.

## Maven Modules

```
magrathea-objectstorage/
├── pom.xml
├── s3-api/                         # Pluggable S3 HTTP adapter (RouterFunction, XML, AWS CLI tests)
├── object-storage-domain/          # Pure S3 domain: Bucket, S3Object, repository interfaces
├── object-storage-application/     # Application services, DTOs, ContentStore port
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

### Current Implemented Operations (9/111)

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

### Phase A — CLI-Baseline Compatibility (highest priority)

Goal: support common AWS CLI object workflows beyond current CRUD.

| Operation | Reason |
|---|---|
| ListObjectsV2 | AWS CLI and SDKs commonly prefer V2 listing |
| CopyObject | Common object copy workflow |
| DeleteObjects | Batch deletion |
| GetBucketLocation | Common SDK/CLI discovery operation |
| GetBucketVersioning | Basic bucket capability discovery |
| PutBucketVersioning | Enables future version-aware behavior |
| ListObjectVersions | Required once versioning exists |

### Phase B — Object Metadata, Tagging, and ACL Compatibility

| Operation |
|---|
| GetObjectAcl |
| PutObjectAcl |
| GetObjectTagging |
| PutObjectTagging |
| DeleteObjectTagging |
| GetObjectAttributes |
| GetBucketAcl |
| PutBucketAcl |
| GetBucketTagging |
| PutBucketTagging |
| DeleteBucketTagging |

### Phase C — Multipart Upload

| Operation |
|---|
| CreateMultipartUpload |
| UploadPart |
| UploadPartCopy |
| CompleteMultipartUpload |
| AbortMultipartUpload |
| ListMultipartUploads |
| ListParts |

### Phase D — Bucket Configuration APIs

| Area | Operations |
|---|---|
| CORS | GetBucketCors, PutBucketCors, DeleteBucketCors |
| Lifecycle | GetBucketLifecycle, GetBucketLifecycleConfiguration, PutBucketLifecycle, PutBucketLifecycleConfiguration, DeleteBucketLifecycle |
| Policy | GetBucketPolicy, PutBucketPolicy, DeleteBucketPolicy, GetBucketPolicyStatus |
| Encryption | GetBucketEncryption, PutBucketEncryption, DeleteBucketEncryption |
| Logging | GetBucketLogging, PutBucketLogging |
| Website | GetBucketWebsite, PutBucketWebsite, DeleteBucketWebsite |
| Notification | GetBucketNotification, GetBucketNotificationConfiguration, PutBucketNotification, PutBucketNotificationConfiguration |
| Replication | GetBucketReplication, PutBucketReplication, DeleteBucketReplication |
| Request Payment | GetBucketRequestPayment, PutBucketRequestPayment |
| Ownership Controls | GetBucketOwnershipControls, PutBucketOwnershipControls, DeleteBucketOwnershipControls |
| Public Access Block | GetPublicAccessBlock, PutPublicAccessBlock, DeletePublicAccessBlock |
| Accelerate | GetBucketAccelerateConfiguration, PutBucketAccelerateConfiguration |

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

## Implementation Rule for New S3 Operations

For every newly implemented S3 operation:

1. Add RouterFunction route in `s3-api`.
2. Use AWS S3 terminology only in domain/application DTOs.
3. Add XML response/request records using Jackson 3 annotations when XML is required.
4. Add Cucumber feature scenario(s) in `s3-api/src/test/features/object-storage/`.
5. Add AWS CLI coverage in `test-aws-cli.sh` if AWS CLI exposes the operation.
6. Update this PLAN coverage table.
7. Update ARC42 and ADRs if the operation introduces a new architectural decision.

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
