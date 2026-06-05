# Magrathea ObjectStore

## Status

Current implementation is a Java 21 + Spring Boot 4 WebFlux S3-compatible object store. The only public HTTP API is the AWS S3-compatible API exposed by the pluggable `s3-reactive-api-adapter` module.

## Maven Modules

```
magrathea-objectstorage/
├── pom.xml
├── s3-reactive-api-adapter/                    # Pluggable S3 HTTP adapter (RouterFunction, XML, Cucumber tests)
├── object-store-domain/                        # Pure S3 domain: aggregates, value objects, domain events
├── object-store-reactive-repository-application/ # Reactive CQS repository interfaces
├── object-store-reactive-application/          # Reactive application services and DTOs
├── object-store-reactive-infrastructure/       # Reactive in-memory repository implementations
├── storage-engine-domain/                      # Storage Engine domain: policy, workflow, device, trace, manifest
├── storage-engine-application/                 # Storage Engine reactive orchestration and ports
├── storage-engine-infrastructure/              # Storage Engine filesystem cluster backend (FS nodes, content-address index, chaos)
├── object-store-reactive-repository-storage-engine-infrastructure/ # ACL + adapter: Object Store → Storage Engine
├── bootstrap-application/                      # Spring Boot entry point
├── docs/                                       # ARC42, ADR, C4
└── test-aws-cli.sh                             # AWS CLI compatibility tests for implemented S3 operations
```

### Two-backend Repository Strategy

The Object Store repository interfaces (`object-store-reactive-repository-application`) have two concrete implementations:

| Backend | Module | Profile | Description |
|---|---|---|---|
| InMemory | `object-store-reactive-infrastructure` | `single-node` (default) | Reactive in-memory repositories for development and single-node deployments |
| Storage Engine | `object-store-reactive-repository-storage-engine-infrastructure` | `storage-engine` | Filesystem cluster backend using the Storage Engine bounded context |

The ACL/adapter module (`object-store-reactive-repository-storage-engine-infrastructure`) translates Object Store repository commands into Storage Engine domain commands. It is the Anti-Corruption Layer between the two bounded contexts.

Removed modules/components:
- `shared-domain` removed.
- `InternalApiRouter` removed because it was not standard S3.
- Legacy blocking application/infrastructure module entries are obsolete; current runtime paths use reactive modules.

## S3 API Handler Organization

`s3-reactive-api-adapter` route mapping is intentionally split by context:

| Class | Responsibility |
|---|---|
| `S3ProxyRouter` | Route composition only |
| `S3BucketOperationsHandler` | Bucket lifecycle, bucket-level listings, location/versioning, directory-bucket listing |
| `S3BucketMetadataHandler` | Bucket ACL and tagging metadata |
| `S3BucketConfigHandler` | Bucket configuration, object-lock configuration, ABAC, and Phase F bucket metadata/table configurations |
| `S3ObjectOperationsHandler` | Object CRUD, copy, multi-delete, torrent, restore, rename, select, and Object Lambda response operations |
| `S3ObjectMetadataHandler` | Object ACL, tagging, attributes, legal hold, retention, and encryption metadata |
| `S3MultipartHandler` | Multipart upload lifecycle |
| `S3SessionHandler` | Phase F session creation |
| `S3WebSupport` | Shared request predicates and S3 XML error helpers |

`ContentStore` has been removed. Object content is saved through reactive object repository commands. Domain interfaces expose no framework types; reactive application implementations carry `Flux<DataBuffer>` at the application boundary. `InMemoryReactiveS3ObjectRepository` persists metadata plus content in the reactive infrastructure module.

## Pluggable S3 API

`s3-reactive-api-adapter` is loaded through Spring Boot 4 auto-configuration:
- `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- `S3ApiConfig` guarded by reactive bucket/object service classes
- `S3ApiConfig` guarded by `s3.api.enabled=true` (`true` by default)

Activation modes:

| Mode | Behavior |
|---|---|
| `s3-reactive-api-adapter` dependency present + `s3.api.enabled=true` | S3 routes are active |
| `s3-reactive-api-adapter` dependency present + `s3.api.enabled=false` | S3 routes disabled |
| `s3-reactive-api-adapter` dependency absent | No S3 web API loaded |

## Testing Strategy

| Level | Type | Command | Notes |
|---|---|---|---|
| 1 | Pure JUnit | `mvn test -pl object-store-domain` | Domain only, no Spring |
| 2 | Cucumber BDD | `mvn test -pl s3-reactive-api-adapter -am -Dsurefire.failIfNoSpecifiedTests=false` | RouterFunction integration |
| 3 | AWS CLI compatibility | `bash test-aws-cli.sh` | Requires app running on `localhost:8080` |
| 4 | Clover coverage | `mvn -Pcoverage clover:setup test clover:aggregate clover:clover` | Generates Clover reports |
| 5 | AWS CLI Maven profile | `mvn -N verify -Paws-cli-tests` | Requires app running and AWS CLI installed |

Consolidated report: `docs/test-report.md` includes AWS CLI outcomes, Surefire/JUnit/Cucumber outcomes, and Clover coverage percentages.

Latest verification: `mvn test -pl s3-reactive-api-adapter -am -Dsurefire.failIfNoSpecifiedTests=false` => 227 tests, 0 failures, 0 errors. Domain: 213 tests. Application: 18 tests. Total: **458 tests**, 0 failures.

## Coverage Tooling

Clover/OpenClover is configured in the parent POM under profile `coverage`.
Report command: `mvn -Pcoverage clover:setup test clover:aggregate clover:clover`
Expected reports: `target/site/clover/`

## Current API Coverage Analysis

See [`docs/api-coverage.md`](docs/api-coverage.md) for detailed request/response coverage. The implemented operation count is now **111/111**.

---

## Phase 1 — Handler Correction Plan

### New Architecture — Layer Responsibilities

| Layer | Responsibilities |
|---|---|
| **Handler** (s3-reactive-api-adapter) | Extract HTTP headers/path variables into primitives/DTOs; delegate to application service; convert service response to HTTP response. **NO** validation, **NO** bucket checks, **NO** ETag computation, **NO** handler-local state, **NO** CORS/website logic. |
| **Application Service** (object-store-reactive-application) | Orchestrate domain calls; call repository. **NO** command checks (those go in repositories). |
| **Domain** (object-store-domain) | Pure aggregates and value objects: S3Object, Bucket, ChecksumValue, EncryptionConfiguration, ObjectKey, StorageClass, ContentDescriptor. No HTTP types. |
| **Repository** (object-store-reactive-infrastructure) | Compute ETag on store; generate version-id on store; persist and retrieve S3Object. **Preconditions** (bucket existence, command validation) go here. **Everything in repositories is POSTPONED.** |

### Handler Minimal Pattern

```java
public Mono<ServerResponse> putObject(ServerRequest request) {
    return objectService.saveObjectWithContent(
            extractObjectKey(request), extractStorageClass(request),
            extractChecksum(request), extractEncryption(request),
            extractContentType(request), extractContentLength(request),
            extractUserMetadata(request), request.bodyToFlux(DataBuffer.class))
        .flatMap(result -> S3ResponseBuilder.ok(result.aggregate()))
        .onErrorResume(BucketNotFoundException.class,
            e -> S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found"))
        .onErrorResume(Throwable.class,
            e -> S3WebSupport.xmlError(HttpStatus.INTERNAL_SERVER_ERROR, "InternalError", e.getMessage()));
}
```

### Already Done (Reverted to Minimal Pattern)

- `S3ObjectOperationsHandler.java` — reverted to minimal pattern
- `S3ObjectMetadataHandler.java` — reverted (no ACL store sharing via constructor)
- `S3ResponseBuilder.java` — reverted (no computeEtagFromChecksum, no applyHeaders)
- `S3ApiConfig.java` — reverted (no BucketService in S3ObjectOperationsHandler)
- `S3ProxyRouter.java` — reverted (no xmlErrorEmptyKey route)
- `S3WebSupport.java` — reverted (no withCorsValidation, no xmlErrorEmptyKey)
- `pom.xml` — reverted (awscli-cucumber-tests profile restored)

### Handlers Still Needing Cleanup

Each handler needs: remove bucket checks, remove handler-local state, remove validation, remove CORS/website logic. Delegate to service.

| Handler | Current Violations | Cleanup Actions | Agent |
|---|---|---|---|
| **S3MultipartHandler** | `bucketService.findByName()` in initiateMultipartUpload/listMultipartUploads; `DigestUtils.md5DigestAsHex()` for ETag | Remove bucket checks; ETag → repository (postponed). | java-infra-coder |
| **S3BucketOperationsHandler** | Bucket name validation in createBucket; `validateRuntimeRequest`/`applyRuntimeHeaders` in listObjects; `websiteRouting` | Remove bucket name validation (postponed); remove CORS/website (postponed). | java-infra-coder |
| **S3BucketMetadataHandler** | `ConcurrentHashMap` for ACL/tag; `visibleAcl`/`validatePublicAclMutation` | Remove handler-local state (postponed → repository); remove CORS (postponed). | java-infra-coder |
| **S3ObjectMetadataHandler** | `bucketService.findByName()` in every method; `ConcurrentHashMap` for ACL/tag | Remove bucket checks (postponed); remove handler-local state (postponed); remove CORS (postponed). | java-infra-coder |
| **S3BucketConfigHandler** | Complex registry pattern; `ConcurrentHashMap` for ABAC, ObjectLock, Metadata, InventoryTable, JournalTable | **Separate phase** — too complex. Needs thorough analysis later. | java-infra-coder (later) |

### Postponed Items (DA FARE — nei repository)

| Item | Target | Notes |
|---|---|---|
| Validation (Spring Reactive Validator) | Repository preconditions | A data da destinarsi |
| Bucket existence checks | Repository preconditions | A data da destinarsi |
| Bucket name validation | Repository preconditions | A data da destinarsi |
| ETag computation | Repository | Generato dal repository su store |
| Handler-local ACL/grants/tagging | Repository | ConcurrentHashMap → repository |
| CORS validation | Repository/Service | A data da destinarsi |
| Website routing | Repository/Service | A data da destinarsi |
| S3Object state machine | Domain | Phase 1o-1q |

### Completed Sub-phases

| Sub-phase | Description | Status |
|---|---|---|
| 1a | S3Header enum + S3RequestExtractor foundation | ✅ DONE |
| 1b | ObjectKey as natural identifier | ✅ DONE |
| 1c–1n | Handler cleanup (reverted to minimal pattern) | ✅ DONE (via revert) |

### Remaining Sub-phases

| Sub-phase | Description | Agent | Priority |
|---|---|---|---|
| 1o–1q | S3Object state machine, ContentDescriptor, EncryptionConfiguration | java-domain-coder | Next |
| 1r | Domain tests | java-tester | After domain |
| 1s | Cucumber tests for updated handlers | java-tester | After handlers |
| 1t | Verify: `mvn test` → 0 failures | java-planner | After all code |
| 1u | Update docs (PLAN.md, ARC42, ADRs) | documenter | After verify |
| 1v | Create docs/user-manual.md | documenter | After verify |

### Verification

```bash
mvn test --also-make
bash test-aws-cli.sh
mvn -N verify -Paws-cli-tests
```

Expected: all tests pass, AWS CLI tests pass.
