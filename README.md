# Magrathea ObjectStore

[![Java](https://img.shields.io/badge/Java-21-blue)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0-green)](https://spring.io/)
[![License](https://img.shields.io/badge/license-MIT-blue)](LICENSE)

**Magrathea ObjectStore** is an AWS S3-compatible object store built with Spring Boot 4 WebFlux and Java 21. The only public HTTP API is the S3 REST API exposed by the pluggable `s3-reactive-api-adapter` module.

---

## System Context

<div align="center">
  <img src="docs/c4/images/SystemContext.png" alt="System Context — Magrathea ObjectStore" width="600">
</div>

*Generated from the Structurizr workspace — see `docs/c4/workspace.dsl` and [`docs/c4/README.md`](docs/c4/README.md).*

---

## Features

- **S3-compatible API only** — no custom internal REST API
- **Implemented operations** — 111/111 Amazon S3 actions in scope, including bucket/object CRUD, multipart upload, bucket/object metadata, bucket configuration, analytics/inventory/metrics/intelligent-tiering, and Phase F advanced/specialized operations from ADR 0012
- **Pluggable S3 API** — auto-configured when `s3-reactive-api-adapter` is on the classpath; disabled with `s3.api.enabled=false`
- **Spring Boot 4 WebFlux** — functional RouterFunction endpoints
- **Jackson 3 XML** — `tools.jackson.dataformat:jackson-dataformat-xml` with custom WebFlux encoder
- **Pure domain** — no Spring, no JPA, no reactive types in `object-store-domain`
- **In-memory infrastructure** — reactive in-memory bucket, object, and multipart repositories
- **Testing** — JUnit, Cucumber, AWS CLI compatibility, Clover coverage profile

---

## Architecture

| Module | Responsibility | Notes |
|---|---|---|
| `s3-reactive-api-adapter` | Pluggable AWS S3 HTTP adapter | Auto-configuration, RouterFunction, XML responses, Cucumber tests |
| `object-store-domain` | S3 domain model | Zero framework dependencies |
| `object-store-reactive-repository-application` | Reactive CQS repository interfaces | Bucket, object, and multipart command/query ports |
| `object-store-reactive-application` | Reactive application services and DTOs | Native Mono/Flux service APIs |
| `object-store-reactive-infrastructure` | Reactive in-memory repository implementations | No HTTP API, no S3 router |
| `object-store-reactive-repository-storage-engine-infrastructure` | Anti-Corruption Layer + adapter: Object Store → Storage Engine | Implements repository interfaces using Storage Engine backend |
| `storage-engine-domain` | Storage Engine domain model (puro) | Policy, workflow, device, trace, manifest — zero framework dependencies |
| `storage-engine-application` | Storage Engine reactive orchestration | Ports, Chunker, ReactiveStorageOrchestrator |
| `storage-engine-infrastructure` | Storage Engine filesystem cluster backend | FileSystemStorageCluster, content address index, manifest repository, chaos decorator |
| `bootstrap-application` | Spring Boot entry point | Includes `s3-reactive-api-adapter` to activate S3 endpoints |

### Backend selection

Two implementations of the Object Store repository interfaces coexist:

| Profile | Backend | Module |
|---|---|---|
| `single-node` (default) | In-memory repositories | `object-store-reactive-infrastructure` |
| `storage-engine` | Storage Engine filesystem cluster | `object-store-reactive-repository-storage-engine-infrastructure` |

The Storage Engine backend is selectable via Spring profile or Maven profile at deployment time.
The ACL translation layer lives in `object-store-reactive-repository-storage-engine-infrastructure`,
which translates Object Store concepts into Storage Engine commands and delegates persistence
to the Storage Engine bounded context.

### Storage Engine Architecture

The Storage Engine bounded context is a separate domain model for object persistence,
independent of the S3 API domain. It defines:

- **StoragePolicy / EffectiveStoragePolicy** — base policy vs request-resolved policy
- **VirtualDevice** — BucketDevice (non-dedup) and DedupDevice (content-addressed)
- **DedupNamespace** — GlobalDedupNamespace and BucketDedupNamespace
- **WorkflowCompatibilityKey / DeviceConfigurationHash** — deterministic device identity
- **StepPlan / StepExecutionTrace** — fixed 6-step pipeline with EXECUTED/SKIPPED/BYPASSED
- **ObjectManifest** — persisted chunk layout, separate from StoredObject aggregate
- **CompleteUpload** — common phase for PutObject and MultipartUpload
- **Chaos engineering** — alter step after checksum, before verify, with FaultInjectingStorageCluster

The pipeline order is fixed: DEDUP → COMPRESS → CRYPT → ERASURE_CODING → REPLICATION → STORE.
EC differentiates DedupDevice; replication does not.

### S3 API activation

```properties
# default: enabled when s3-reactive-api-adapter is on the classpath
s3.api.enabled=true

# disable S3 routes even with s3-reactive-api-adapter present
s3.api.enabled=false
```

`S3ApiConfig` and `JacksonXmlCodecConfig` are loaded through:

```text
s3-reactive-api-adapter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

---

## Quick Start

```bash
mvn clean test
mvn -pl bootstrap-application -am package -DskipTests
java -jar bootstrap-application/target/bootstrap-application-1.0.0-SNAPSHOT.jar
```

In another shell:

```bash
aws --endpoint-url http://localhost:8080 s3api list-buckets
aws --endpoint-url http://localhost:8080 s3api create-bucket --bucket test-bucket
printf 'Hello\n' > /tmp/hello.txt
aws --endpoint-url http://localhost:8080 s3api put-object --bucket test-bucket --key hello.txt --body /tmp/hello.txt
aws --endpoint-url http://localhost:8080 s3api get-object --bucket test-bucket --key hello.txt /tmp/out.txt
```

---

## Testing

| Level | Type | Command |
|---|---|---|
| 1 | All unit + integration tests | `mvn test` |
| 2 | Domain JUnit only | `mvn test -pl object-store-domain` |
| 3 | S3 API Cucumber only | `mvn test -pl s3-reactive-api-adapter -am -Dsurefire.failIfNoSpecifiedTests=false` |
| 4 | Clover coverage | `mvn -Pcoverage clover:setup test clover:aggregate clover:clover` |
| 5 | AWS CLI compatibility | `bash test-aws-cli.sh` |
| 6 | AWS CLI via Maven profile | `mvn verify -Paws-cli-tests` (auto-starts/stops server) |

Consolidated Markdown report: [`docs/test-report.md`](docs/test-report.md)

### Automated Coverage on Commit

A **pre-commit git hook** generates Clover coverage before every commit:

```bash
# Normal commit — coverage auto-generates
 git commit -m "msg"

# Skip coverage (fast commit)
 git commit --no-verify -m "msg"
```

Or use the helper script:

```bash
bash scripts/commit-with-coverage.sh -m "commit message"
```

The hook runs `mvn -Pcoverage clover:setup test clover:aggregate clover:clover` and stages
`target/site/clover/clover.xml` + `docs/test-report.md`.

AWS CLI tests require:

- AWS CLI installed
- Port 8080 free (the profile starts/stops the server automatically)
- Optional endpoint override: `ENDPOINT_URL=http://host:port bash test-aws-cli.sh`

#### Workflow

```bash
# Manual: start server in one shell, run tests in another
java -jar bootstrap-application/target/bootstrap-application-1.0.0-SNAPSHOT.jar
bash test-aws-cli.sh

# Automatic via Maven (starts server, runs tests, stops server):
mvn verify -Paws-cli-tests
```

The Maven profile (`aws-cli-tests`):
1. **pre-integration-test** — starts the JAR on port 8080
2. **integration-test** — runs `test-aws-cli.sh`
3. **post-integration-test** — stops the server

Use `mvn verify -Paws-cli-tests` for a fully automated AWS CLI compatibility run.

---

## S3 API Roadmap

The implementation plan tracks all Amazon S3 actions from:

<https://docs.aws.amazon.com/AmazonS3/latest/API/API_Operations.md>

Current coverage: **111 / 111 Amazon S3 actions**.

Phases A, B, C, D, E, and F are complete. Phase F advanced/specialized operations are recorded in [ADR 0012](docs/adr/0012-phase-f-advanced-s3-operations.md) and verified by Cucumber.

See [`PLAN.md`](PLAN.md) for the full phased inclusion plan.

---

## Documentation

| Artifact | Location |
|---|---|
| Implementation plan | [`PLAN.md`](PLAN.md) |
| ARC42 architecture docs | [`docs/arc42/`](docs/arc42/) |
| ADRs | [`docs/adr/`](docs/adr/) |
| C4 diagrams | [`docs/c4/`](docs/c4/) |
| C4 workflow | [`docs/c4/README.md`](docs/c4/README.md) |

---

## License

[MIT](LICENSE)
