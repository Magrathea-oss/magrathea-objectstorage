# Magrathea ObjectStore — User Manual

## 1. Configuration

### Starting the Application

Magrathea ObjectStore is a Spring Boot 4 WebFlux application. Build and run:

```bash
# Build all modules
mvn clean install -DskipTests

# Start the bootstrap application
cd bootstrap-application
mvn spring-boot:run
```

The application starts on `http://localhost:8080` by default. The S3 API is enabled by default (`s3.api.enabled=true`).

### Configuration Properties

| Property | Default | Description |
|---|---|---|
| `s3.api.enabled` | `true` | Enable/disable the S3-compatible REST API |
| `server.port` | `8080` | HTTP server port |
| `spring.profiles.active` | `single-node` | Profile: `single-node` (in-memory) or `storage-engine` (filesystem cluster) |

### Profiles

| Profile | Backend | Module |
|---|---|---|
| `single-node` (default) | In-memory `ConcurrentHashMap` | `object-store-reactive-infrastructure` |
| `storage-engine` | Filesystem cluster | `object-store-reactive-repository-storage-engine-infrastructure` |

### AWS CLI Compatibility Testing

```bash
# Start the app in one terminal
cd bootstrap-application && mvn spring-boot:run

# Run the AWS CLI test script
bash test-aws-cli.sh

# Or use the Maven profile (starts app automatically)
mvn -N verify -Paws-cli-tests
```

---

## 2. S3-Compatible API

Magrathea ObjectStore exposes **111/111 in-scope S3 operations** through a pluggable Spring Boot auto-configuration module (`s3-reactive-api-adapter`).

### Implemented Operations

| Category | Operations | Handler |
|---|---|---|
| **Service** | ListBuckets (`GET /`) | `S3BucketOperationsHandler.listBuckets*()` |
| **Bucket Lifecycle** | CreateBucket (`PUT /{bucket}`), DeleteBucket (`DELETE /{bucket}`), HeadBucket (`HEAD /{bucket}`), BucketLocation (`GET /{bucket}?location`), BucketVersioning (`GET/PUT /{bucket}?versioning`), DirectoryBucket listing (`GET /?directory-buckets`) | `S3BucketOperationsHandler` |
| **Bucket Listing** | ListObjects (`GET /{bucket}`), ListObjectsV2 (`GET /{bucket}?list-type=2`), ListVersions (`GET /{bucket}?versions`) | `S3BucketOperationsHandler` |
| **Bucket ACL** | GetBucketAcl (`GET /{bucket}?acl`), PutBucketAcl (`PUT /{bucket}?acl`) | `S3BucketMetadataHandler` |
| **Bucket Tagging** | GetBucketTagging (`GET /{bucket}?tagging`), PutBucketTagging (`PUT /{bucket}?tagging`), DeleteBucketTagging (`DELETE /{bucket}?tagging`) | `S3BucketMetadataHandler` |
| **Bucket Configuration** | CORS, Lifecycle, Policy, Encryption, Logging, Website, Notification, Replication, RequestPayment, OwnershipControls, PublicAccessBlock, Accelerate, Analytics, Inventory, Metrics, IntelligentTiering, ABAC, ObjectLock, Metadata, MetadataTable, InventoryTable, JournalTable (`GET/PUT/DELETE /{bucket}?{config}`) | `S3BucketConfigHandler` |
| **Object CRUD** | PutObject (`PUT /{bucket}/{key}`), GetObject (`GET /{bucket}/{key}`), HeadObject (`HEAD /{bucket}/{key}`), DeleteObject (`DELETE /{bucket}/{key}`), DeleteObjects (`POST /{bucket}/{key}?delete`) | `S3ObjectOperationsHandler` |
| **Object Copy** | CopyObject (`PUT /{bucket}/{key}` with `x-amz-copy-source`) | `S3ObjectOperationsHandler` |
| **Object Metadata** | GetObjectAcl (`GET /{bucket}/{key}?acl`), PutObjectAcl (`PUT /{bucket}/{key}?acl`), GetObjectTagging (`GET /{bucket}/{key}?tagging`), PutObjectTagging (`PUT /{bucket}/{key}?tagging`), DeleteObjectTagging (`DELETE /{bucket}/{key}?tagging`), GetObjectAttributes (`GET /{bucket}/{key}?attributes`), LegalHold (`GET/PUT /{bucket}/{key}?legal-hold`), Retention (`GET/PUT /{bucket}/{key}?retention`) | `S3ObjectMetadataHandler` |
| **Multipart Upload** | CreateMultipartUpload (`POST /{bucket}/{key}?uploads`), UploadPart (`PUT /{bucket}/{key}?uploadId=...&partNumber=...`), UploadPartCopy (`PUT /{bucket}/{key}?uploadId=...&partNumber=...` with copy-source), CompleteMultipartUpload (`POST /{bucket}/{key}?uploadId=...`), AbortMultipartUpload (`DELETE /{bucket}/{key}?uploadId=...`), ListMultipartUploads (`GET /{bucket}?uploads`), ListParts (`GET /{bucket}/{key}?uploadId=...`) | `S3MultipartHandler` |
| **Phase F Advanced** | RenameObject (`POST /{bucket}/{key}?rename`), Torrent (`GET /{bucket}/{key}?torrent`), Restore (`POST /{bucket}/{key}?restore`), Select (`POST /{bucket}/{key}?select`), Object Lambda Response (`PUT /{bucket}/{key}?x-amz-write-get-object-response`), CreateSession (`GET /_/session`) | Various handlers |

---

## 3. Handler Pattern

Every handler (except `S3BucketConfigHandler`) follows the **extract → delegate → response** minimal pattern:

### Extract
Use `S3RequestExtractor` to parse HTTP headers, path variables, and query parameters into typed primitives and DTOs.

```java
var key = S3RequestExtractor.extractObjectKey(request);
var storageClass = S3RequestExtractor.extractStorageClass(request);
var checksum = S3RequestExtractor.extractChecksum(request);
```

### Delegate
Call a single reactive service method. No validation, no bucket checks, no ETag computation in the handler.

```java
return objectService.saveObjectWithContent(key, storageClass, checksum, ...)
```

### Response
Convert the service result into an HTTP response using `S3ResponseBuilder` or direct XML serialization.

```java
    .flatMap(result -> S3ResponseBuilder.ok(result.aggregate()))
    .onErrorResume(BucketNotFoundException.class,
        e -> S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found"))
    .onErrorResume(Throwable.class,
        e -> S3WebSupport.xmlError(HttpStatus.INTERNAL_SERVER_ERROR, "InternalError", e.getMessage()));
```

### Handler Status

| Handler | Pattern | Notes |
|---|---|---|
| `S3ObjectOperationsHandler` | ✅ Minimal | Delegates to `ReactiveObjectService` |
| `S3ObjectMetadataHandler` | ✅ Minimal | Delegates to `ReactiveObjectService` |
| `S3MultipartHandler` | ✅ Minimal | Delegates to `ReactiveMultipartUploadService` |
| `S3BucketOperationsHandler` | ✅ Minimal | Delegates to `ReactiveBucketService` |
| `S3BucketMetadataHandler` | ✅ Minimal | Delegates to `ReactiveBucketService` |
| `S3SessionHandler` | ✅ Minimal | Delegates to `ReactiveBucketService.createSession()` |
| `S3BucketConfigHandler` | ⏳ Excluded | Complex registry + `ConcurrentHashMap` — postponed |

---

## 4. Postponed Items

The following features are **not yet implemented** in the current codebase. They are tracked as postponed items to be implemented in the repository layer.

| Feature | Target | Notes |
|---|---|---|
| Validation (Spring Reactive Validator) | Repository preconditions | Input validation moved out of handlers |
| Bucket existence checks | Repository preconditions | `findByName()` in handlers replaced by repository preconditions |
| Bucket name validation | Repository preconditions | DNS-compliant bucket naming rules |
| ETag computation | Repository | Generated by repository on store operations |
| Handler-local ACL/grants/tagging | Repository | `ConcurrentHashMap` → repository persistence |
| CORS validation | Repository/Service | Origin header validation |
| Website routing | Repository/Service | Website configuration redirect |
| `S3BucketConfigHandler` cleanup | Separate phase | Registry pattern + `ConcurrentHashMap` for ABAC/ObjectLock/Metadata |

These items are documented in `PLAN.md` under "Postponed Items" and in `docs/adr/0015-handler-minimal-pattern-and-postponed-repository-responsibilities.md`.

---

## 5. Running Tests

### Test Levels

| Level | Type | Command | Notes |
|---|---|---|---|
| 1 | Pure JUnit | `mvn test -pl object-store-domain` | Domain only, no Spring |
| 2 | Cucumber BDD | `mvn test -pl s3-reactive-api-adapter -am -Dsurefire.failIfNoSpecifiedTests=false` | RouterFunction integration |
| 3 | AWS CLI compatibility | `bash test-aws-cli.sh` | Requires app running on `localhost:8080` |
| 4 | Clover coverage | `mvn -Pcoverage clover:setup test clover:aggregate clover:clover` | Generates Clover reports |
| 5 | AWS CLI Maven profile | `mvn -N verify -Paws-cli-tests` | Requires app running and AWS CLI installed |

### Quick Verify

```bash
# Run all tests (domain + application + S3 integration)
mvn test --also-make
```

Expected result: all tests pass, 0 failures.

### Full Verification

```bash
mvn test --also-make
bash test-aws-cli.sh
mvn -N verify -Paws-cli-tests
```

### Test Report

Consolidated test results are available in `docs/test-report.md`.

---

## Source Code Reference

| Module | Source Path |
|---|---|
| HTTP Adapter | `s3-reactive-api-adapter/src/main/java/` |
| Domain | `object-store-domain/src/main/java/` |
| Application Services | `object-store-reactive-application/src/main/java/` |
| Repository Interfaces | `object-store-reactive-repository-application/src/main/java/` |
| In-memory Infrastructure | `object-store-reactive-infrastructure/src/main/java/` |
| Storage Engine | `storage-engine-*/src/main/java/` |
| Bootstrap | `bootstrap-application/src/main/java/` |
| Docs | `docs/` |

## Architecture Documentation

- ARC42: `docs/arc42/arc42-template.adoc`
- ADRs: `docs/adr/`
- C4 Diagrams: `docs/c4/`
