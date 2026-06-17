# Magrathea ObjectStore — Correction Plan

> **Planning status:** This document is a correction and implementation plan. It is **not** an implementation result, and unchecked items must not be described elsewhere as already delivered.

## Post-Audit Production Readiness Plan

The post-audit baseline is that Magrathea ObjectStore remains a prototype with an advanced architecture, not a production-ready object store. The declared Phase 2 filesystem reliability scope is now implemented and validated, but production-readiness work must still close restart-safety, streaming, observability, distributed-readiness, and broader S3 semantic gaps below before any production claim is made.

### Phase 1 — Reliable Upload and Restart Safety ⚠️ Partially implemented and validated

| Field | Plan / Result |
|---|---|
| Focus | Make the `PutObject` → storage-engine → `GetObject` path durable, restart-safe, and bounded in memory. |
| Required outputs | Durable S3 object metadata; durable S3 bucket/key/version to storage-engine `manifestId` mapping; removal of runtime-only `storeByKey` / `manifestByKey` as the source of truth; restart-safe PUT → process restart → GET behavior; HTTP S3 + storage-engine profile E2E coverage; upload path that does not reduce all object bytes to one array and does not retain unbounded traces. |
| Current status | `@implemented-not-e2e-validated` for REQ-UPLOAD-001, REQ-UPLOAD-002, REQ-UPLOAD-003, and REQ-UPLOAD-004. `@implemented-and-validated` for REQ-UPLOAD-005 and REQ-UPLOAD-006: both WebTestClient (5+3 examples via `Phase1UploadStorageEngineCucumberTest`) and AWS CLI (5+3 examples via `Phase1UploadStorageEngineAwsCliCucumberTest`) Cucumber runners pass. REQ-UPLOAD-001 and REQ-UPLOAD-002 require two independent Spring application contexts for restart semantics; no Cucumber runner covers them. REQ-UPLOAD-003 requires `fixtures/upload/large-object.bin` (256 MiB, not yet created). REQ-UPLOAD-004 requires `large-object.bin` and fault-injection step definitions; no Cucumber runner covers it. |
| Validation evidence | `StorageEngineRestartSafetyTest` (1 test, bootstrap module) — programmatically starts two independent Spring contexts sharing the same storage root and verifies PUT in the first context survives full context close/restart, with GET returning the same bytes from filesystem persistence in the second context (REQ-UPLOAD-001). `StorageEngineMultiChunkUploadTest` (1 test) — uploads a 128 KB payload with 32 KB chunk size, asserts ≥ 2 chunk files are written to the filesystem, and verifies byte-for-byte read-back without whole-object aggregation (REQ-UPLOAD-003). `StorageEngineHttpReadAfterWriteTest` (2 tests) — proves immediate read-after-write is served from filesystem persistence with manifests/object references/chunks on disk (REQ-UPLOAD-005), and proves a fresh `StorageEngineReactiveS3ObjectRepository` instance reloads the committed object manifest from the same storage root and reads back the original content without shared in-memory state (REQ-UPLOAD-002). `StorageEngineUploadAtomicityTest` (1 test, bootstrap) — fault-injected interrupted PUT produces no committed manifest and no readable object (REQ-UPLOAD-004). `StorageEngineIntegrityDetectionTest` (1 test, bootstrap) — chunk corruption is detected before returning bytes, GET returns non-200 (REQ-UPLOAD-006). `Phase1UploadStorageEngineCucumberTest` WebTestClient Cucumber runner: 30 tests run, 0 failures, 22 skipped (`@awscli`-tagged rows excluded): REQ-UPLOAD-005 5 webclient examples (content-type, user-metadata, storage-class, checksum-sha256, sse-s3-metadata) and REQ-UPLOAD-006 3 webclient examples (default-put, checksum-sha256, content-md5), all pass. Full suite: `mvn -B test` PASS — BUILD SUCCESS, 0 failures, 0 errors. `Phase1UploadStorageEngineAwsCliCucumberTest` AWS CLI Cucumber runner (package `phase2awscli`): 30 tests run, 0 failures, 22 skipped (non-@awscli filtered); 8 @awscli examples executed — REQ-UPLOAD-005 (5: content-type, user-metadata, standard-storage-class, checksum-sha256, sse-s3-metadata) and REQ-UPLOAD-006 (3: default-put, checksum-sha256, content-md5), all pass. |
| Acceptance gates | Bootstrap JUnit gates met for all six requirements. Cucumber e2e gates: REQ-UPLOAD-005 and REQ-UPLOAD-006 fully met — WebTestClient and AWS CLI runners both pass for all declared examples. REQ-UPLOAD-001 and REQ-UPLOAD-002 Cucumber gates remain open (restart semantics require multi-context runner not yet built). REQ-UPLOAD-003 and REQ-UPLOAD-004 Cucumber gates remain open (require `fixtures/upload/large-object.bin` and fault-injection steps not yet built). |

Tasks:
- [x] Persist the object-store key, bucket, metadata, version/null-version state, ETag/checksum data, and selected storage-engine manifest reference.
- [x] Replace in-memory key-to-manifest lookup with a durable repository and recovery path.
- [x] Add an HTTP S3 E2E scenario that runs with the storage-engine backend, restarts/recreates the application context, and validates read-after-write.
- [x] Refactor upload to stream through the storage engine with bounded buffers instead of collecting all content and trace records before storing.
- [x] Add Phase 1 Cucumber WebTestClient runner glue so `phase-1-upload-storage-engine.feature` scenarios become executable Cucumber tests. `Phase1UploadStorageEngineCucumberTest` covers REQ-UPLOAD-005 (5 webclient examples) and REQ-UPLOAD-006 (3 webclient examples); restart (REQ-UPLOAD-001, 002) and large-object (REQ-UPLOAD-003) scenarios remain validated through bootstrap integration tests only.
- [x] Add `@awscli` runner for REQ-UPLOAD-005: `Phase1UploadStorageEngineAwsCliCucumberTest` (package `phase2awscli`) covers 5 @awscli examples — all pass.
- [ ] Create `fixtures/upload/large-object.bin` (deterministic 256 MiB binary) to unblock REQ-UPLOAD-003 and REQ-UPLOAD-004 Cucumber examples.
- [ ] Add multi-context restart Cucumber runner for REQ-UPLOAD-001 and REQ-UPLOAD-002, or formally accept bootstrap integration tests as the sole validation mode and document the rationale in the feature file.
- [x] Implement REQ-UPLOAD-004 failed-upload atomicity: `StorageEngineUploadAtomicityTest` (bootstrap) proves fault-injected interrupted PUT leaves no committed manifest or readable object.
- [ ] Add Cucumber e2e runner for REQ-UPLOAD-004: requires `fixtures/upload/large-object.bin` (256 MiB, deterministic) and WebTestClient fault-injection step definitions; `@awscli` example also pending.
- [x] Implement REQ-UPLOAD-006 integrity/corruption detection: `StorageEngineIntegrityDetectionTest` (bootstrap) plus `Phase1UploadStorageEngineCucumberTest` REQ-UPLOAD-006 WebTestClient (3 examples) prove chunk corruption is detected on read.
- [x] Add `@awscli` runner for REQ-UPLOAD-006: `Phase1UploadStorageEngineAwsCliCucumberTest` covers 3 @awscli examples — all pass.

### Phase 2 — Filesystem Reliability ✅ Implemented and validated for declared Phase 2 scope

| Field | Plan / Current status |
|---|---|
| Focus | Make filesystem-backed chunks, manifests, and metadata safe against partial writes, corruption, and crashes. |
| Current status | `@implemented-and-validated` for the declared Phase 2 filesystem reliability scope. Production/runtime code implements atomic chunk and manifest publication, checksum verification, recovery scanning/quarantine, S3 XML integrity-error mapping, disabled-by-default write fault injection for interrupted-write validation, and storage-engine `PutObject` storage-class defaulting. |
| Implemented outputs | Atomic chunk writes using temporary files, fsync, and rename plus SHA-256 sidecar checksums; atomic manifest writes using temporary files, fsync, and rename plus checksum trailers; read-time checksum verification for chunks and manifests; `FileSystemRecoveryScanner` with reporting, quarantine, and idempotence; storage-engine integrity exceptions mapped to S3 XML error responses; disabled-by-default write fault injection for interrupted chunk/manifest write tests; storage-engine `PutObject` storage-class default fixed so `null`/blank maps to `STANDARD`. |
| Validation evidence | `mvn -B -pl s3-reactive-api-adapter -am test -Dsurefire.failIfNoSpecifiedTests=false` PASS; `mvn -B test` PASS; full Surefire XML aggregate: 827 tests, 0 failures, 0 errors, 11 skipped. Phase 2 WebTestClient requirements: 11 examples total, 7 passed, 4 skipped/excluded (`@awscli`); REQ-FS-001 through REQ-FS-006 WebTestClient-required scope passed. Dedicated storage-engine AWS CLI Phase 2 validation: 4 scenarios/examples, 4 passed, 0 failed, 0 skipped. |
| Validated Phase 2 scenarios | WebTestClient: REQ-FS-001 interrupted chunk write; REQ-FS-002 interrupted manifest write; REQ-FS-003 corrupted chunk detection; REQ-FS-004 corrupted manifest detection; REQ-FS-005 recovery scanner; REQ-FS-006 concurrent PUT of different keys and same key. AWS CLI: REQ-FS-003, REQ-FS-004, REQ-FS-006 different keys, and REQ-FS-006 same key. |
| Remaining notes | No declared Phase 2 filesystem reliability validation gap remains. This does **not** claim distributed readiness, broader S3 semantic completion, physical erasure-coding placement, or later-phase production readiness. |
| Acceptance gates | Met for the declared Phase 2 scope. Interrupted-write publication safety, checksum mismatch detection, recovery scanner behavior, selected concurrency scenarios, and applicable AWS CLI storage-engine validation are passing. |

Tasks:
- [x] Write chunks/manifests to temporary files and publish them atomically for the implemented chunk/manifest paths.
- [x] Store and verify checksums at chunk and manifest levels.
- [x] Implement recovery scanning for orphaned/incomplete/corrupt filesystem state with quarantine and deterministic/idempotent reporting.
- [x] Add semantic WebTestClient validation for corruption detection, recovery scanning, and concurrent PUT scenarios.
- [x] Define and document the fsync-backed atomic publication policy for data files, metadata files, and directories within the declared Phase 2 scope.
- [x] Add executable interrupted-write/fault-injection validation for partial chunk and manifest writes (REQ-FS-001 and REQ-FS-002).
- [x] Add AWS CLI validation mode for Phase 2 filesystem reliability examples where applicable.

### Phase 3 — Reactive Pipeline Refactor ⚠️ Implemented, not e2e validated

| Field | Plan / Current status |
|---|---|
| Focus | Replace the monolithic orchestrator flow with a staged reactive read/write pipeline. |
| Current status | `@implemented-not-e2e-validated`. Phase 3 core application behavior is implemented and unit/application validated. A WebTestClient Cucumber runner (`Phase3ReactivePipelineCucumberTest`) now exists but all `@phase-3 @webclient` scenarios carry `@not-implemented` in the feature file, so they are excluded from execution by the runner's `not @not-implemented` tag filter. The runner infrastructure is ready; scenarios will be included once production pipeline-event observability is exposed externally and the `@not-implemented` tag is removed. |
| Implemented outputs | `StorageStage`, `StorageContext`, `StorageEvent`, `StorageEventPublisher`, cleanup handle, and `StoragePipelineExecutor`; `ReactiveStorageOrchestrator` refactored into staged write and read pipelines; bounded-demand chunk processing without whole-object aggregation; deterministic failure propagation, cancellation cleanup, and stage event publication. No Phase 4 metrics/tracing adapters are implemented as part of this phase. |
| Validation evidence | Requirement feature: `s3-reactive-api-adapter/src/test/features/requirements/phase-3-reactive-pipeline.feature`. WebTestClient runner: `Phase3ReactivePipelineCucumberTest` (filter: `@phase-3 and @webclient and not @not-implemented`): 12 tests, 0 failures, 0 errors, 12 skipped — all excluded because feature-file scenarios carry `@not-implemented`. `mvn -B -pl storage-engine-reactive-application -am test` PASS: 159 total, 159 passed. `mvn -B test` PASS: BUILD SUCCESS, 0 failures, 0 errors. |
| Validated Phase 3 scenarios | REQ-PIPELINE-001 stage order/events; REQ-PIPELINE-002 bounded demand/backpressure/no whole-object aggregation; REQ-PIPELINE-003 read manifest chunk order; REQ-PIPELINE-004 failure propagation/cleanup/later stages stopped; REQ-PIPELINE-005 cancellation event and cleanup; REQ-PIPELINE-006 instrumentation metadata/correlation/no payload leakage. None are validated through the WebTestClient runner yet (all carry `@not-implemented` in the feature file). |
| Remaining validation gap | All Phase 3 `@webclient` scenarios carry `@not-implemented` in the feature file. Remove the tag and add step implementations once pipeline-event observability (StorageStage transitions, event emission, backpressure counters) is exposed through an external HTTP endpoint or metrics surface. |
| Acceptance gates | Unit/application pipeline tests pass. WebTestClient runner infrastructure is in place. External/e2e validation gate remains open until `@not-implemented` is removed from feature scenarios. |

Tasks:
- [x] Introduce explicit stages for validation, policy resolution, chunking, dedup lookup, chunk persistence, manifest persistence, object-index persistence, read planning, chunk reading, and response streaming.
- [x] Carry request/object metadata and per-stage decisions in `StorageContext` rather than ad hoc local state.
- [x] Emit typed `StorageEvent` records for stage start, success, failure, retry, cleanup, and timing.
- [x] Ensure stage contracts preserve reactive backpressure and avoid blocking I/O on event-loop threads.
- [x] Add Phase 3 WebTestClient runner infrastructure (`Phase3ReactivePipelineCucumberTest`); runner excludes all scenarios because they carry `@not-implemented`. Remove tag and add step implementations once pipeline-event observability is externally observable.
- [ ] Add Phase 3 WebTestClient step implementations and remove `@not-implemented` from feature scenarios once internal pipeline-event behavior is exposed through the agreed external surface.

### Phase 4 — Observability ⚠️ Implemented and bootstrap-validated; no Cucumber e2e runner exists

| Field | Plan / Result |
|---|---|
| Focus | Make storage behavior visible without coupling core logic to one metrics/tracing backend. |
| Required outputs | Storage listener/event publisher; metrics for bytes, chunks, manifests, failures, recoveries, dedup hits/misses, and latency; tracing/OpenTelemetry integration; stage timing and correlation IDs; operational logging for recovery and corruption events. |
| Current status | `@implemented-not-e2e-validated` for all Phase 4 requirements. Production observability adapters (Micrometer metrics, OpenTelemetry tracing, composite event publisher, redaction policy, recovery logging) are implemented and validated through `Phase4ObservabilityIntegrationTest` and `Phase4ObservabilityFaultInjectionIntegrationTest` (bootstrap JUnit, 5 tests total). No Cucumber e2e runner exists for Phase 4; the Cucumber scenarios in `phase-4-observability.feature` are never executed as Cucumber tests. The feature file top tag correctly reflects `@implemented-not-e2e-validated`. REQ-OBS-003 dedup/recovery metrics remain unit-only validated (`@implemented-not-e2e-validated`). No Phase 4 AWS CLI runner exists. |
| Acceptance gates | Bootstrap JUnit integration gates met: events emitted for success/failure pipelines, metrics inspectable in integration tests, traces identify request/manifest/stage timings without sensitive metadata leakage. Cucumber e2e gates remain open: no Phase 4 WebTestClient Cucumber runner executes the `phase-4-observability.feature` scenarios; no Phase 4 AWS CLI runner exists. |
| Validation evidence | Requirement feature: `s3-reactive-api-adapter/src/test/features/requirements/phase-4-observability.feature`. Integration tests: `bootstrap-application` `Phase4ObservabilityIntegrationTest` (4 tests), `Phase4ObservabilityFaultInjectionIntegrationTest` (1 test). Infrastructure: `StorageObservabilityAdaptersTest` (2 tests), `FileSystemRecoveryScannerObservabilityTest` (1 test). `mvn -B test` PASS: 834 total, 0 failures, 0 errors. |
| Validated Phase 4 scenarios | REQ-OBS-001 stage events/timing/correlation (unit+integration); REQ-OBS-002 failure-event classification/cleanup/redaction (unit+integration); REQ-OBS-003 Micrometer dedup/recovery metrics (unit only — `@implemented-not-e2e-validated`); REQ-OBS-004 OpenTelemetry tracing/stage-spans/redaction (integration); REQ-OBS-005 recovery/corruption operational logging (integration); REQ-OBS-006 redaction policy/no payload leakage (unit+integration). |
| Implemented outputs | Composite `StorageEventPublisher`; Micrometer metrics adapter (bytes, chunks, failures, latency, dedup hit/miss, recovery); OpenTelemetry adapter with in-process exporter; `FileSystemRecoveryScanner` operational logging; redaction policy banning object body and user-metadata from logs/traces/metrics. |

Completed scope:
- Composite listener/event-publisher port wrapping `StorageEvent`.
- Micrometer metrics and OpenTelemetry tracing adapters wired in infrastructure and bootstrap.
- Per-stage durations, failure classifications, and recovery findings recorded.
- Redaction policy documented and validated: object payloads and user metadata excluded from all telemetry surfaces.

### Phase 5 — S3 Semantic Compatibility ✅ Implemented and WebTestClient-validated for declared Phase 5 scope

| Field | Plan / Result |
|---|---|
| Focus | Move beyond mapped endpoints and protocol smoke tests toward semantically meaningful S3 behavior. |
| Required outputs | Real multipart persistence/assembly and multipart ETag semantics; `Range` and conditional requests; correct ETag/checksum behavior; object versioning and delete markers; `CopyObject`; ACL/tagging persistence and enforcement classification; lifecycle, object lock, encryption, retention, restore, and replication semantics clearly implemented or explicitly unsupported/config-only. |
| Current status | `@implemented-and-validated` for 24 Phase 5 WebTestClient scenarios and 12 Phase 5 AWS CLI scenarios. `@implemented-not-e2e-validated` for REQ-S3-002-C multipart restart/durability because validation uses WebTestClient plus direct filesystem same-directory repository inspection, not a full process/Spring restart. REQ-S3-007 scenarios are validated unsupported/config-only classification, not real enforcement. |
| Validation evidence | Requirement feature: `s3-reactive-api-adapter/src/test/features/requirements/phase-5-s3-semantic-compatibility.feature`. WebTestClient runner: `s3-reactive-api-adapter/src/test/java/com/example/magrathea/s3api/cucumber/requirements/Phase5S3SemanticCompatibilityRequirementsCucumberTest.java` PASS: 25 scenarios/tests, 0 failures, 0 errors, 0 skipped. Step glue: `s3-reactive-api-adapter/src/test/java/com/example/magrathea/s3api/cucumber/requirements/Phase5S3SemanticCompatibilitySteps.java`. AWS CLI runner: `Phase5S3SemanticCompatibilityAwsCliCucumberTest` (package `phase5awscli`) PASS: 25 tests, 0 failures, 0 errors, 13 skipped (non-@awscli-required filtered out); 12 `@awscli-required` examples executed — multipart ETag (REQ-S3-002-A, -B), byte-range GETs (REQ-S3-003-A through -D), and conditional requests (REQ-S3-004-A through -F). Full suite: `mvn -B test --no-transfer-progress` PASS / BUILD SUCCESS, 0 failures, 0 errors. |
| Validated Phase 5 scenarios | REQ-S3-001 PutObject/HeadObject ETag format and consistency; REQ-S3-002 UploadPart and CompleteMultipartUpload ETag semantics, with restart durability not e2e validated; REQ-S3-003 byte-range GET and unsatisfiable range; REQ-S3-004 conditional request headers; REQ-S3-005 CopyObject ETag; REQ-S3-006 object tagging lifecycle and inline `x-amz-tagging`; REQ-S3-007 explicit unsupported/config-only classification for versioning, object lock, and lifecycle. |
| Implemented outputs | RFC-compatible quoted lowercase MD5 ETag computation for PutObject/HeadObject; ETag persistence through the S3 object domain model (`etag`) and repository translators; object tags persisted through the S3 object domain model (`objectTags`) and object tagging endpoints; Range GET handling with 206 Partial Content, `Content-Range`, and 416 `InvalidRange`; conditional GET/HEAD behavior for `If-Match`, `If-None-Match`, `If-Modified-Since`, and `If-Unmodified-Since`; CopyObject destination ETag propagation; real multipart part and final ETags; filesystem-backed multipart upload repository; explicit unsupported/config-only S3 responses. |
| Remaining validation gaps | No full application restart e2e validation exists for REQ-S3-002-C multipart uploaded-part durability (requires a full Spring context restart, not same-process repository inspection); object versioning/delete markers/object-lock/lifecycle/replication enforcement remains intentionally unsupported or config-only. Phase 5 AWS CLI runner (`Phase5S3SemanticCompatibilityAwsCliCucumberTest`) covers 12 scenarios; 13 skipped (non-@awscli-required); scenarios are skipped gracefully when `aws` CLI is not installed. |

Completed scope:
- Correct single-part and multipart ETag behavior for the declared WebTestClient validation scope.
- Range and conditional request semantics for S3 GET/HEAD paths.
- CopyObject ETag propagation and object tagging persistence.
- Honest unsupported/config-only behavior for features not yet enforced.

### Phase 6 — Distributed Readiness ⚠️ Modeled and unit-validated, not e2e/distributed-production-ready

| Field | Plan / Result |
|---|---|
| Focus | Design and verify behavior needed before claiming distributed storage readiness. |
| Required outputs | Real replication; quorum rules; node membership; health model; anti-entropy/self-healing; rebalancing; placement across failure domains; operational recovery workflows. |
| Current status | `@implemented-not-e2e-validated` for the declared Phase 6 modeled domain scope. Deterministic placement, health-aware membership decisions, quorum decisions, anti-entropy/healing plans, rebalance plans, and readiness classification are implemented and unit validated. Real networked membership, replication execution, healing/rebalance job runners, and full multi-node end-to-end validation remain absent. |
| Validation evidence | Requirement feature: `s3-reactive-api-adapter/src/test/features/requirements/phase-6-distributed-readiness.feature`. Domain tests: `DistributedPlacementPlannerTest`, `QuorumPolicyTest`, `AntiEntropyPlannerTest`, `RebalancePlannerTest`, `DistributedReadinessReporterTest`. `mvn -B -pl storage-engine-domain test --no-transfer-progress` PASS: 164 tests, 0 failures/errors/skipped. `mvn -B test --no-transfer-progress` PASS / BUILD SUCCESS: 883 test cases observed, 0 failures/errors, 11 skipped. |
| Validated Phase 6 scenarios | REQ-DIST-001 modeled placement across failure domains and insufficient-domain failure; REQ-DIST-002 write/read quorum and integrity-aware read failure; REQ-DIST-003 health-aware membership placement and exclusions; REQ-DIST-004 anti-entropy findings and planned healing tasks; REQ-DIST-005 safe rebalance planning and failed-copy retry classification; REQ-DIST-006 readiness classification that avoids distributed-production claims. |
| Implemented outputs | Pure domain distributed-readiness model under `storage-engine-domain/.../distributed`: topology/node health, placement decisions, quorum policy, replica observations, anti-entropy findings/healing tasks, rebalance plans/task results, and readiness reports. |
| Remaining validation gaps | No real networked membership service; no real replication execution across processes/nodes; no durable multi-node manifest publication; no healing or rebalance job runner; no WebTestClient/AWS CLI/full e2e multi-node validation. Distributed production readiness is not claimed. |

Completed modeled scope:
- Deterministic failure-domain placement and health-aware candidate selection.
- Explicit quorum and integrity decisions.
- Observable anti-entropy/healing/rebalance plans without job execution claims.
- Honest readiness status reporting for single-node/distributed-simulation modes.
- No public storage-engine object API endpoints were added; S3 object behavior remains exposed through S3-compatible APIs only.

## Gherkin Requirements and ARC42 Appendix

Gherkin feature files are executable requirements for the object store. They must describe business/system behavior and storage invariants, not only anemic request/response/status-code checks.

Storage-engine external-boundary note: S3 client behavior must be specified and validated only through S3-compatible APIs, using shared WebTestClient/AWS CLI features where applicable. Storage-engine requirements are internal/operational abilities unless they describe admin-only configuration or state with no S3 API equivalent; those admin-only concepts may be exposed through the Admin API/UI for storage policies, storage devices, disk sets/topology, backend status, and validation/reporting. Do not plan public storage-engine endpoints for object upload/read/delete/list semantics.

First pass: reconcile false-positive API/feature completion claims into explicit requirement statuses such as `@implemented-and-validated`, `@implemented-not-e2e-validated`, `@partial`, `@config-only`, `@placeholder`, `@absent`, or `@not-implemented`. The ARC42 appendix must show declared-vs-validated status instead of treating route existence, docs text, or status-code smoke tests as completion evidence.

### Authoring Rules

- Feature files are executable requirements, not transport smoke tests.
- Scenarios must express meaningful behavior: preconditions, state transitions, outcomes, invariants, durability guarantees, failure behavior, and recovery semantics.
- Status-code-only scenarios are allowed only as low-level protocol smoke tests and must be tagged accordingly, for example `@protocol-smoke`.
- Production-readiness scenarios should use tags such as `@requirement`, `@storage-engine`, `@restart-safety`, `@large-object`, `@recovery`, `@observability`, and `@s3-compatibility`.
- Use `Business Need` for externally promised S3 server behavior through S3 APIs; use `Ability` for storage-engine internals that support those S3 needs; use `Business Need` or `Ability` for admin-only capabilities depending on whether they are user-visible admin product requirements.
- Admin-only storage-engine features should use tags such as `@admin-api`, `@storage-policy`, `@storage-device`, `@disk-set`, and `@backend-status`, not `@s3-api`.
- Each requirement scenario should map to a requirement ID and ARC42 section where possible, for example `REQ-STORAGE-RESTART-001` and `arc42:8` or `arc42:10`.
- AWS CLI and WebTestClient variants should validate the same requirement and acceptance rules; they must not duplicate anemic endpoint calls just to increase scenario counts.
- Scenario text should make the backend and validation mode explicit when behavior differs by driver, backend, failure mode, or production-readiness phase.

### Reporting Task — Generated Requirements Appendix

| Field | Plan |
|---|---|
| Output | Generate a Gherkin requirements report from feature files and include it as an ARC42 appendix. |
| Proposed generated source target | Prefer `docs/arc42/generated/gherkin-requirements.adoc` or `docs/requirements/generated/gherkin-requirements.adoc`. The source report is generated documentation; do not commit generated static web assets unless a current repository convention explicitly requires it. |
| Grouping | The appendix groups scenarios by feature, tags, requirement ID, capability area, test backend/driver, ARC42 section, and validation status. |
| Build integration | Remaining: the Docker documentation/web build should include or regenerate the appendix for the web app/docs, consistent with the existing decision that web documentation regeneration is Dockerfile-driven rather than dependent on host-generated static assets. No generated static web assets were changed by the appendix-generator task. |
| Quality gate | Implemented locally: `python3 scripts/generate-gherkin-requirements-appendix.py --check` verifies freshness. Remaining: wire this check into CI or the Docker documentation build. |

Tasks:
- [x] Define a lightweight metadata convention for requirement IDs, capability area, ARC42 section references, backend/driver, and validation status in feature files by using existing Gherkin scenario tags and generated report classification rules.
- [x] Implement a dependency-light Python generator that scans Gherkin features and writes the ARC42 appendix to `docs/arc42/generated/gherkin-requirements.adoc`.
- [x] Link the generated appendix from the ARC42 source docs.
- [ ] Ensure Docker-driven documentation generation includes the report without committing built static web assets.
- [x] Add a freshness check command: `python3 scripts/generate-gherkin-requirements-appendix.py --check`.
- [ ] Wire the freshness check into CI or Docker quality gates.

### Gherkin Appendix Acceptance Criteria

- ARC42 contains an appendix listing all executable requirements derived from Gherkin feature files.
- Each requirement scenario has an ID, classification/capability area, validation mode, and, where possible, an ARC42 section mapping.
- The report marks anemic `@protocol-smoke` scenarios separately from production-readiness requirements.
- AWS CLI and WebTestClient variants are shown as validation modes for the same requirement where they exercise the same behavior.
- CI/quality gates verify generated appendix freshness or the Docker documentation build regenerates it deterministically.

## Project-Wide Correction Plan — Full Audit Findings

This section implements [ADR 0017](docs/adr/0017-course-correction-broaden-project-wide-correction-plan-beyond-storage-engine-notes.md). It broadens the correction plan beyond the user's storage-engine configuration notes and promotes project-wide audit findings into first-class work items. The items below are **planned corrections only**; they are not evidence that implementation has happened.

### A. Build/scaffolding/package hygiene

| Field | Plan |
|---|---|
| Owner agent(s) | `java-scaffolder` primary; `java-tester` for build gates; `documenter` for report updates |
| Affected modules/files | Root `pom.xml`; all module `pom.xml` files; `admin-api-adapter/pom.xml`; `bootstrap-application/src/main/resources/static/**`; generated `com/**/*.class`; generated or misplaced `META-INF/**`; frontend/docs generation configuration; Dockerfiles and runtime scripts; `magrathea-ui/**`; `docs/test-report.md` |
| Concrete findings | Generated/static artifacts appeared tracked under the source tree, including `bootstrap-application/src/main/resources/static`, `com/**/*.class`, and `META-INF/**`. Maven plugin/version configuration is inconsistent and coverage profile naming is duplicated. Host-side builds must not depend on committed generated frontend/docs resources. Docker/runtime packaging previously had risks: a `curl` healthcheck was expected in a JRE image and npm installation appeared duplicated across paths. `admin-api-adapter` had a POM schema URL issue. |
| Expected correction outputs | Clean source tree boundaries; generated artifacts moved to build output or explicitly ignored; unified Maven plugin management and one canonical coverage profile; frontend/docs build outputs regenerated during Docker image creation instead of copied from host-generated bootstrap static resources; Docker healthcheck/runtime dependencies reconciled; duplicated npm install path removed or justified; corrected `admin-api-adapter` POM metadata. |
| Acceptance gates | `mvn validate` succeeds from a clean checkout; `git status` remains clean after build/test/doc generation except intended outputs; no `.class` or generated `META-INF` artifacts are committed under source paths; one documented coverage profile is authoritative; Docker image healthcheck dependencies are present or the healthcheck is changed; admin API module POM validates. |

Current status / result — safe first pass completed: root `pom.xml` now centralizes `spring-boot-maven-plugin` version management through `${spring-boot.version}` and documents the root `-Pcoverage` profile as the canonical coverage profile. Source/build hygiene ignores were tightened in `.gitignore` and `.dockerignore` for class files, Maven/node build outputs, frontend dist output, and misplaced root generated output without hiding intended source resources. `Dockerfile` now installs `wget`, matching the docker-compose healthcheck dependency. `scripts/check-source-hygiene.sh` was added and the ignored generated root output directories `com/` and `META-INF/` were removed.

Validation evidence for this first pass: `mvn -B validate --no-transfer-progress` PASS / BUILD SUCCESS; `bash scripts/check-source-hygiene.sh` PASS. Docker compose config and full Docker image validation were not run because `docker` is unavailable in this environment.

Remaining gaps: full duplicate module-level `coverage` profile consolidation in `bootstrap-application/pom.xml` is still pending because `bootstrap-application/**` was outside the java-scaffolder-owned correction scope. Full Docker build validation is also pending. Therefore section A is not complete yet.

### B. Module/layering architecture

| Field | Plan |
|---|---|
| Owner agent(s) | `java-scaffolder` primary for module boundaries; `java-infra-coder` for Spring wiring; `java-domain-coder` for dependency direction; `documenter` for ARC42/ADR alignment |
| Affected modules/files | `object-store-reactive-application`; `object-store-reactive-infrastructure`; `bootstrap-application`; `object-store-reactive-repository-storage-engine-infrastructure`; `storage-engine-*`; package roots under `com/example/magrathea/objectstore/**` and `com/example/magrathea/objectstorage/**`; root and module POMs; Spring configuration and auto-configuration files |
| Concrete findings | `object-store-reactive-application` currently depends on `object-store-reactive-infrastructure` and imports infrastructure exceptions, inverting the intended dependency direction. `bootstrap-application` depends directly on nearly all modules and masks dependency boundaries. The storage-engine backend exists but is not runtime-wired as an active selectable backend. Package naming is inconsistent (`objectstore` vs `objectstorage`), which can affect component scanning and configuration. |
| Expected correction outputs | Application modules depend only on ports/domain abstractions; infrastructure-specific exceptions are translated at boundaries; bootstrap dependencies are reduced or made explicit through auto-configuration; storage-engine backend has a real profile/property selection path; package naming and scanning rules are made consistent or explicitly bridged. |
| Acceptance gates | Dependency analysis shows no application-to-infrastructure dependency inversion; Spring context tests prove in-memory and storage-engine backend selection separately; duplicate repository beans fail fast or are prevented; package scanning covers intended beans without broad accidental scanning. |

Current status / result — bounded first pass completed: root `pom.xml` now runs `scripts/check-module-layering.sh` during root-only `validate`. The guard prevents `object-store-reactive-application` from depending on object-store or storage-engine infrastructure modules, prevents `storage-engine-reactive-application` from depending on storage-engine infrastructure, and prevents repository-application modules from depending on infrastructure modules. It also preserves the explicit package naming bridge while `objectstore` and `objectstorage` coexist by requiring `MagratheaApplication` scan roots for both `com.example.magrathea.objectstore` and `com.example.magrathea.objectstorage`. Backend selection remains explicit: in-memory repositories are guarded under `@Profile({"single-node", "default"})`, storage-engine repositories under `@Profile("storage-engine")`, and backend conflict messages remain present. Existing context tests cover selection behavior: `DefaultBackendContextTest` asserts in-memory repository beans are active and storage-engine beans absent; `StorageEngineBackendContextTest` asserts storage-engine repository beans are active and in-memory beans absent; `ObjectStoreBackendStatusConfig` fails fast for conflicting backend property/profile combinations. Validation evidence: `mvn -B validate --no-transfer-progress` PASS, `bash scripts/check-module-layering.sh` PASS, and `mvn -B test --no-transfer-progress` PASS / BUILD SUCCESS after the ARC42 docs conversion fix that changed generated appendix output from `NOTE:` admonition syntax to a plain paragraph.

Remaining gaps: this is a bounded first pass, not complete architecture cleanup. No large package rename was performed, so the `objectstore` versus `objectstorage` naming inconsistency remains intentionally bridged by explicit scan roots and guards. `bootstrap-application` still intentionally depends on multiple modules for application assembly, and no major bootstrap dependency reduction was performed. The architecture guard is shell/POM based; no Java/ArchUnit test was added because test-file ownership belongs to `java-tester`.

### C. Domain quality

| Field | Plan |
|---|---|
| Owner agent(s) | `java-domain-coder` primary; `java-tester` for domain test gates; `documenter` for documenting domain invariants |
| Affected modules/files | `storage-engine-domain/src/main/java/**`; `storage-engine-domain/src/test/java/**`; domain classes including `StoredObject`, `Bucket`, `MultipartUpload`, `ActiveS3Object`, `StoragePolicy`, `DedupConfig`, collection-bearing records, and `EncryptionConfiguration` variants |
| Concrete findings | Earlier audit evidence said `storage-engine-domain` had no tests; that statement is now outdated because substantial domain tests exist. `StoredObject` remains mutable through controlled lifecycle methods and diverges from the immutable/record style used elsewhere. Domain records previously leaked collection mutability. Duplicate or ambiguous names such as multiple encryption configuration/context variants reduce clarity. Lifecycle invariants remain incomplete in areas such as `Bucket` deletion state and broader aggregate lifecycle modeling. Optional state is represented in a null-heavy style. |
| Expected correction outputs | Domain invariants captured in constructors/factories/behavior methods; immutable aggregates/value objects where appropriate; defensive copies or immutable collections in records; naming cleanup for duplicate/ambiguous concepts; explicit optional-state model; storage-engine domain unit test suite. |
| Acceptance gates | `mvn test -pl storage-engine-domain -am` passes with meaningful tests; mutation and null-state regressions are covered; invalid lifecycle transitions fail deterministically; public domain APIs avoid leaking mutable internal collections. |

Current status / result — bounded first pass completed: defensive collection copies and immutable exposure were added in object-store domain aggregates/value objects including `Bucket`, `MultipartUpload`, `S3Object`, and `EncryptionContext`, and in storage-engine/domain planning and trace types including `ObjectMetadataDescriptor`, `PersistencePlan`, `ChunkPersistenceTrace`, `StepOutcome`, `CompleteUploadCommand`, and `UploadCompletionTrace`. `StoredObject` invariants were strengthened so `CREATING` objects must not have a `manifestId`, `STORED`/`DELETED` objects must have a `manifestId`, and `lastModified` cannot be before `createdAt`. Validation evidence: `mvn -B -pl storage-engine-domain,object-store-domain test --no-transfer-progress` PASS with 172 `storage-engine-domain` tests and 292 `object-store-domain` tests; `mvn -B test --no-transfer-progress` PASS. Remaining gaps: `StoredObject` is still intentionally mutable through controlled lifecycle methods; duplicate/ambiguous encryption class names were not renamed; `Bucket` deletion lifecycle still appears event-only without an explicit deleted terminal state; and no broad lifecycle or immutable-aggregate redesign was attempted.

### D. Application/infrastructure/runtime correctness

| Field | Plan |
|---|---|
| Owner agent(s) | `java-infra-coder` primary for adapters/runtime; `java-domain-coder` for orchestrator semantics; `java-tester` for integration and Cucumber gates |
| Affected modules/files | `object-store-reactive-repository-storage-engine-infrastructure`; `storage-engine-reactive-application`; `storage-engine-reactive-infrastructure`; `s3-reactive-api-adapter`; `admin-api-adapter`; `bootstrap-application`; repositories, orchestrators, manifest/chunk stores, routers, and error mappers |
| Concrete findings | `StorageEngineReactiveS3ObjectRepository#getContent` returns empty. Filesystem manifest and stored-object repositories cannot deserialize complete state. Chunk IDs are inconsistent across orchestrator, manifest, dedup, and filesystem persistence. A dedup hit does not skip duplicate persistence. Full object bodies are accumulated into memory. Production code has blocking-call and blocking filesystem I/O scheduling risks. S3 keys with slash-containing paths were corrected in Phase 9 by catch-all object routes and key normalization. Multipart upload does not persist and assemble part bodies. ETag semantics are likely wrong. `AdminRouter` error semantics and policy map are disconnected from `StoragePolicyCatalog`. |
| Expected correction outputs | End-to-end read-after-write through selected backend; complete manifest/stored-object serialization; consistent chunk identity model; dedup duplicate-hit short-circuit; streaming upload/read path without whole-object buffering; blocking I/O isolated on suitable schedulers; route handling for slash-containing S3 keys preserved by Phase 9 catch-all normalization; multipart part persistence and assembly; documented and tested ETag behavior; admin errors backed by the real policy catalog. |
| Acceptance gates | Storage-engine backend integration tests pass for put/get/delete/list and read-after-write; large/chunk-boundary tests do not require full-body aggregation; route tests cover keys with slashes; multipart Cucumber/unit tests persist and assemble content; ETag expectations are asserted; admin API tests validate catalog-backed behavior and error semantics. |

Current status / result — bounded first pass completed: `StorageEngineReactiveS3ObjectRepository#getContent` was verified to perform real storage-engine read-through from persisted manifest references via `orchestrator.read(...)`, with strengthened regression coverage that writes through the storage-engine repository, reads content back through a new repository instance, and verifies metadata/content plus slash-containing key preservation. `FileSystemManifestRepository` manifest serialization now round-trips declared upload checksums and multipart part checksum results. Blocking filesystem reads/writes in `FileSystemContentAddressIndex`, `FileSystemStorageNode`, `FileSystemManifestRepository`, and `FileSystemStoredObjectRepository` now use the `BlockingFileSystemOperation` helper on `Schedulers.boundedElastic()`. Validation evidence: `mvn -B -pl object-store-reactive-repository-storage-engine-infrastructure,storage-engine-reactive-infrastructure,s3-reactive-api-adapter,bootstrap-application -am test --no-transfer-progress` PASS / BUILD SUCCESS, and `mvn -B test --no-transfer-progress` PASS / BUILD SUCCESS.

Remaining gaps: this is not complete section D closure. Full-body accumulation remains in S3 handler `DataBufferUtils.join` paths; the broad streaming rewrite was intentionally not attempted. Phase 5 multipart ETag behavior is preserved, but multipart part body persistence and final object assembly remain incomplete and tracked. Broader chunk identity, dedup short-circuit, complete admin catalog-backed error semantics, and full runtime correctness acceptance gates remain open unless separately validated.

### E. Storage-engine/MINIO_STANDARD/configuration ✅ Bounded first pass completed

| Field | Plan |
|---|---|
| Owner agent(s) | `java-domain-coder` for policy/topology model; `java-infra-coder` for YAML-backed repositories and runtime selection; `java-scaffolder` for profiles/autoconfig; `java-tester` for executable scenarios; `documenter` for ADR/ARC42 alignment |
| Affected modules/files | `storage-engine-domain`; `storage-engine-reactive-application`; `storage-engine-reactive-infrastructure`; `object-store-reactive-repository-storage-engine-infrastructure`; `bootstrap-application`; storage policy/device YAML directories; README/ARC42/ADR files |
| Concrete findings | Chunking must remain inside `DedupConfig` and be configurable by `StoragePolicy`. The configuration model requires one YAML file per `StoragePolicy` and one YAML file per `StorageDevice`, disk set, or topology entity. Repositories/catalogs must be YAML-backed. `MINIO_STANDARD` is the first concrete use case. Physical disk/device/topology mapping is required for placement and erasure coding. Backend profile/autoconfiguration selection is incomplete. |
| Expected correction outputs | Domain-pure policy/device/topology model; YAML-backed `StoragePolicy` and storage-device/topology catalogs; explicit `MINIO_STANDARD` YAML and tests; policy-driven dedup/chunking behavior; physical topology metadata sufficient for placement/EC decisions; backend profile/property/autoconfiguration wiring. |
| Acceptance gates | YAML catalog tests reject malformed, duplicate, and unresolved references; `MINIO_STANDARD` loads from YAML and produces deterministic plans; storage-engine backend starts only when required config is present; policy/device management APIs use the same catalogs as runtime. |

Current status / result — bounded first pass completed: All four acceptance gates are met. `YamlStoragePolicyCatalog`, `YamlStorageDeviceCatalog`, and `YamlDiskSetCatalog` are fully implemented with robust validation: 14 + 10 + 12 catalog tests cover malformed YAML, duplicate IDs, duplicate storageClassId, domain-invalid configs (replication factor, EC config, unknown algorithms), zero-device disk sets, blank device references, unknown failure domains, unresolved device references, and empty directories. `MINIO_STANDARD` is loaded from classpath YAML and produces deterministic persistence plans (`MinioStandardIntegrationTest`, 2 tests — EC `4 data / 2 parity`, dedup disabled, replication factor 1). The storage-engine backend fails fast on missing external config directories (`StorageEngineMissingExternalConfigTest`, 1 test). Backend profile selection is validated via Spring context tests proving mutually exclusive repository beans and YAML catalog loading (`StorageEngineBackendContextTest`, 3 tests — profile selection, catalog loading, write+read smoke). The Admin API catalog endpoints are integrated via the same `StoragePolicyCatalog`, `StorageDeviceCatalog`, and `DiskSetCatalog` Spring beans (`AdminRouterTest`, 9 tests — list/get policies/devices/disk-sets, validate, read-only mutation rejection, service-unavailable when catalog absent). Full suite: `mvn -B test` PASS — 902 tests, 0 failures, 0 errors.

Remaining gaps: no integration test exercises the Admin API through a real YAML-backed Spring Boot context (unit test uses stub catalogs); physical EC shard placement and multi-node topology are not validated at runtime; policy schema versioning and migration strategy are documented but not enforced at the infrastructure level.

### F. Testing/quality

| Field | Plan |
|---|---|
| Owner agent(s) | `java-tester` primary; `documenter` for report integration; implementation agents own fixes for failures in their modules |
| Affected modules/files | Surefire reports under `*/target/surefire-reports/**`; root and module test suites; `s3-reactive-api-adapter/src/test/features/**`; AWS CLI Cucumber features/steps; `test-aws-cli.sh`; JaCoCo configuration and reports; `docs/test-report.md`; CI scripts if present |
| Concrete findings | Current validation evidence is broad but not a project-wide completion claim: latest full Surefire XML aggregate is 833 tests, 0 failures, 0 errors, 11 skipped. Legacy WebTestClient Cucumber has 239 passed scenarios; the existing AWS CLI Cucumber parity suite has 26 passed scenarios; dedicated storage-engine AWS CLI Phase 2 validation adds 4 passed scenarios/examples; the isolated Phase 2 WebTestClient requirements runner has 11 examples total, 7 passed and 4 `@awscli` examples skipped/excluded. Phase 2 filesystem reliability is implemented-and-validated for declared scope. Phase 3 reactive pipeline behavior is implemented-not-e2e-validated with unit/application validation only; no Phase 3 WebTestClient, AWS CLI, or e2e Cucumber validation has been executed yet. Broader S3 semantic completion and distributed readiness remain pending. The standalone AWS CLI report is stale/failing and separate from Cucumber parity. Target design remains the same canonical scenarios executed with WebTestClient and AWS CLI drivers where possible. JaCoCo is the coverage baseline; Clover is optional/legacy. `docs/test-report.md` must stay evidence-based. |
| Expected correction outputs | Current test inventory by module; tests added for untested modules, especially `storage-engine-domain`; canonical Cucumber scenario model with WebTestClient and AWS CLI drivers/tags; fresh AWS CLI compatibility status; JaCoCo baseline documented; stale Clover-primary wording removed; updated test report that separates verified results from planned gates. |
| Acceptance gates | `mvn test` and targeted module gates pass; untested critical modules have meaningful tests or explicit risk entries; WebTestClient/AWS CLI scenario parity matrix exists; AWS CLI failures are reproducible or explicitly skipped with reasons; `docs/test-report.md` matches current command results and report paths. |

### G. Documentation/C4/API truthfulness

| Field | Plan |
|---|---|
| Owner agent(s) | `documenter` primary; `c4model` for C4 diagrams through `delegate_agent`; `java-tester` supplies verified test results; implementation agents supply factual module status |
| Affected modules/files | `README.md`; `PLAN.md`; `docs/arc42/**`; `docs/adr/**`; `docs/c4/**` or Structurizr files; `docs/test-report.md`; `docs/api-coverage.md`; `LICENSE`; API/admin documentation |
| Concrete findings | Docs claim an S3-only public API while `admin-api-adapter` exposes `/admin/**`. Storage Engine docs overstate profile/runtime integration. C4 diagrams still reference old router names and omit the admin API. README/PLAN links are missing or stale, including `LICENSE` and `docs/api-coverage.md`. ADR freshness issues and non-English fragments remain. |
| Expected correction outputs | Documentation distinguishes the public S3 object API from admin/configuration APIs; storage-engine runtime claims downgraded until verified; no documentation introduces a parallel public storage-engine API for S3 object semantics; C4 diagrams updated for current routers, admin API, and selected backends; README/PLAN links fixed; ADR index/status reviewed; non-English fragments translated unless intentionally quoted. |
| Acceptance gates | Documentation review finds no implemented-status overclaims; C4/ARC42 names match code; all linked docs exist or are deliberately removed; test and API coverage reports are consistent with current evidence; ADR statuses are current and in English. |

### H. Web/UI planning

| Field | Plan |
|---|---|
| Owner agent(s) | `documenter` for UI/API plan; `java-infra-coder` for backend admin contracts; frontend implementation requires an appropriate frontend workflow/agent outside the current Java workflow before code changes in `magrathea-ui` |
| Affected modules/files | `magrathea-ui/**`; `admin-api-adapter`; `storage-engine-reactive-application`; `storage-engine-reactive-infrastructure`; frontend/docs build configuration; `bootstrap-application/src/main/resources/static/**`; README/ARC42 admin sections |
| Concrete findings | Admin/web UI must manage `StoragePolicy`, `StorageDevice`, and disk-set/topology entities. Backend admin endpoints are now implemented as configuration-as-code/read-only catalog endpoints: YAML-backed policies/devices/disk sets are read at runtime, mutation requests are rejected, and the validation endpoint validates request bodies without persisting them. Generated bootstrap static resources are excluded from Docker build context, and the Dockerfile regenerates the Vue UI and documentation assets inside the builder stage. Frontend work requires an appropriate frontend workflow/agent outside the current Java workflow before changing `magrathea-ui`. |
| Expected correction outputs | UI plan for policy list/detail/validation, device list/detail, disk-set/topology overview, and backend/status screens; backend admin API contracts for read-only storage policies/devices/disk sets plus non-persistent policy validation; admin exposure remains limited to non-S3 storage-engine concepts and must not duplicate S3 object upload/read/delete/list semantics; Dockerfile-driven frontend/docs packaging that does not rely on host-generated bootstrap static resources; workflow handoff request before frontend implementation work. |
| Acceptance gates | Backend admin API contract tests pass for read-only catalog behavior and validation errors; UI work remains planned until frontend ownership is assigned; Docker image build regenerates frontend/docs assets without relying on host-generated bootstrap static resources; README/ARC42 describe admin UI scope without claiming unimplemented screens. |

### Prioritized roadmap

| Priority | Correction focus | Must happen before claiming completion |
|---|---|---|
| P0 — Stop compounding false signals ✅ (documentation + build hygiene done 2026-06-12) | Clean generated/tracked artifact boundaries; fix Maven/POM/profile hygiene; correct documentation overclaims; establish current test/report baseline; record package/layering risks. **Done 2026-06-12:** (1) README.md and docs/test-report.md documentation overclaims corrected; JaCoCo documented as primary baseline. (2) 789 generated/compiled artifacts untracked from Git (Vue bundles, JaCoCo JSON exports, .class files, META-INF); .gitignore extended. (3) `admin-api-adapter/pom.xml` schema URL fixed (maven-1.0.0.xsd → maven-4.0.0.xsd). (4) `docker-compose.yml` healthcheck changed from `curl` (missing in JRE image) to `wget --spider`. (5) `.dockerignore` excludes generated bootstrap static resources, and the `Dockerfile` regenerates documentation/frontend static assets inside the Maven/Node builder stage from source docs and UI sources instead of copying host-pre-generated resources. Remaining P0 gate: `mvn validate` from clean checkout. | `mvn validate` from a clean checkout; no generated source-tree mutations; README/PLAN/test report stop claiming unverified runtime/API status; JaCoCo baseline is clear. |
| P1 — Restore architectural and runtime correctness ⚠️ (layering/scanning done 2026-06-12; runtime correctness pending) | Fix application-to-infrastructure dependency inversion; make backend selection explicit; wire storage-engine backend end to end; repair critical read/write, manifest, chunk, dedup, route, multipart, ETag, and admin catalog behavior. **Done 2026-06-12:** (1) 4 exception classes moved from `object-store-reactive-infrastructure` to `object-store-reactive-repository-application` port module; all imports updated; old classes deprecated; infra dependency removed from application POM. (2) `@ComponentScan` extended with `com.example.magrathea.objectstorage` and `com.example.magrathea.storageengine`. (3) `@Profile("single-node")` added to 3 in-memory repository beans; `@Profile("storage-engine")` added to storage-engine adapter beans and ACL translator. (4) `application.properties` sets `spring.profiles.default=single-node`. (5) Pre-existing compile error in `ReactiveStorageOrchestrator` fixed (duplicate variable, type mismatch). Full project `mvn compile` → BUILD SUCCESS. Remaining: runtime correctness (read path, manifest/chunk/dedup, multipart, ETag, admin catalog) — tracked under P2/P5. | Context tests for both backends; S3 read-after-write through selected backend; storage-engine integration tests; route/multipart/admin tests pass. |
| P2 — Harden domain and configuration model | Add storage-engine domain tests; enforce invariants and immutability; implement YAML-backed policy/device/topology catalogs; define and test `MINIO_STANDARD`; model physical placement/topology for placement and EC. | Domain and catalog tests pass; malformed config fails clearly; `MINIO_STANDARD` is loaded from YAML and produces deterministic persistence plans. |
| P3 — Parity, documentation, and UI maturation ⚠️ (Phase 8 backend Admin API partial done; Phase 9 first increment done) | Bring AWS CLI Cucumber toward canonical WebTestClient parity; update C4/ARC42/API coverage; plan and hand off frontend admin UI. **Done for Phase 8 backend/API:** read-only configuration-as-code Admin API catalog endpoints and non-persistent policy validation are documented and tested. **Done for Phase 9 object CRUD, bucket operations, metadata/tagging, and multipart increments:** targeted AWS CLI Cucumber covers object CRUD basics (put default headers, get content, head, list v1/v2, delete, idempotent delete, `STANDARD` storage class via object attributes, slash-containing object keys), bucket operations (create, list, head, location, versioning enable, delete, and failure cases 404/409), ACL/tagging (bucket and object read/write/CRUD), object attributes (ETag + ObjectSize), and multipart upload lifecycle (initiate/upload/list-parts/complete/abort/list-uploads); production fixes for `uploadPart` ETag header, `putBucketTagging`, and `putObjectTagging` XML body handling (commit `a03bc4a`). Remaining: full AWS CLI parity beyond object CRUD and bucket lifecycle, selected backend/status contract beyond `/admin/health`, UI implementation handoff, ADR freshness, and remaining docs links. | Scenario parity matrix exists; docs match code and tests; C4 includes admin API/current routers; frontend workflow ownership is established before UI implementation. |

## S3 API Semantic Completion Plan

This section implements [ADR 0018](docs/adr/0018-course-correction-classify-s3-api-coverage-by-semantic-implementation-not-route-count.md). It is a planning correction only: route count, handler count, or a `111/111` operation inventory is **not** a completion metric. Such counts can only prove that an endpoint is mapped. They do not prove state transitions, persisted data, S3-compatible errors and headers, AWS CLI interoperability, or storage-engine behavior.

### Per-Operation Classification Levels

Every S3 operation must be classified independently. An operation may be mapped without being implemented, and it may be stateful without being AWS CLI compatible or storage-engine compatible.

| Level | Meaning | Evidence required before using the label |
|---|---|---|
| Mapped | An HTTP route, predicate, and handler entry point exist. | Route inventory and handler mapping test. |
| Stubbed | The route returns a nominal response or placeholder behavior but does not implement meaningful S3 state transitions, validation, or persistence. | Explicit test or code inventory showing documented unsupported/not-implemented/stub behavior. |
| Stateful | The operation creates, reads, updates, or deletes durable in-memory/domain state with observable follow-up behavior. | Stateful tests covering success, relevant errors, and follow-up reads/listings. |
| AWS CLI compatible | Current AWS CLI or equivalent black-box tests verify request signing/shape, response shape, headers, exit status, and common client behavior for the supported scope. | Current AWS CLI scenario result linked from the test report. |
| Storage-engine compatible | The operation uses the selected storage-engine backend correctly for object bytes, metadata, versions, policy state, or cleanup where relevant. | Storage-engine backend scenario, not only in-memory tests. |
| Semantically S3-compatible | The supported scope matches expected S3 behavior: state transitions, response shape, headers, errors, idempotency, consistency expectations, and edge cases. | Semantic scenario suite plus documented unsupported deviations. |

### Family-by-Family Completion Proposal

| Family | Priority | Affected modules | Expected outputs | Acceptance gates |
|---|---|---|---|---|
| Object CRUD baseline: `PutObject`, `GetObject`, `HeadObject`, `DeleteObject`, `CopyObject` | S3-P1 | `s3-reactive-api-adapter`; `object-store-domain`; `object-store-reactive-application`; `object-store-reactive-repository-application`; `object-store-reactive-infrastructure`; `object-store-reactive-repository-storage-engine-infrastructure`; `storage-engine-*`; `bootstrap-application` | Expand current reporting, which is effectively centered around `PutObject`, into full CRUD semantics: read-after-write, slash-containing keys, metadata persistence, checksum handling, ETag behavior, storage class handling, delete/idempotency semantics, copy semantics, and storage-engine backend read/write/delete. | WebTestClient and repository tests prove put/get/head/delete/copy state transitions; keys with slashes work; object body and metadata survive read-after-write; ETag/checksum/storage-class expectations are documented and asserted; storage-engine backend passes equivalent scenarios. |
| Bucket baseline: `CreateBucket`, `HeadBucket`, `DeleteBucket`, `ListBuckets`, `ListObjects`, `ListObjectsV2` | S3-P1 | `s3-reactive-api-adapter`; bucket services/repositories in object-store modules; in-memory and storage-engine repository adapters | Real bucket state, bucket existence checks, object index updates, list ordering/scope, prefixes, delimiters, continuation tokens, markers, max keys, empty buckets, non-empty delete behavior, and storage-engine backed object index behavior. | Stateful bucket lifecycle tests; list V1/V2 tests with prefixes, delimiters, continuation tokens, slash keys, empty buckets, and deleted objects; AWS CLI list/head/create/delete scenarios marked separately. |
| Multipart upload: `CreateMultipartUpload`, `UploadPart`, `UploadPartCopy`, `ListParts`, `CompleteMultipartUpload`, `AbortMultipartUpload`, `ListMultipartUploads` | S3-P2 | `s3-reactive-api-adapter`; `object-store-domain`; multipart application services; repositories; storage-engine ACL and orchestration modules | Persisted part bodies and metadata, upload state, part listing, part-copy source resolution, final assembly, multipart ETag semantics, abort cleanup, orphan cleanup policy, and storage-engine integration for parts and completed objects. | Multipart state machine tests; uploaded parts survive list/complete; completed object reads as assembled bytes; abort removes or hides parts; ETag behavior is asserted; storage-engine backend passes multipart scenarios. |
| Object metadata/configuration: ACLs, tags, object attributes, legal hold, retention, restore metadata | S3-P2 for persistence; S3-P4 for enforcement-heavy behavior | `s3-reactive-api-adapter`; object metadata handlers/services; object-store domain/repositories; storage-engine metadata persistence | Distinguish persisted metadata from enforcement. Persist and retrieve tags, ACL documents or supported grants, attributes, legal-hold flags, retention metadata, and restore status where in scope; document unsupported enforcement separately. | Put/get/delete metadata tests; metadata survives object read/head/list where relevant; unsupported enforcement returns documented errors or is labeled stubbed; AWS CLI metadata scenarios are separately marked. |
| Bucket configuration: CORS, lifecycle, website, logging, notification, replication, encryption, versioning, tagging, accelerate, location, ownership controls, request payment | S3-P3 for stateful config; S3-P4 for enforcement/execution | `s3-reactive-api-adapter`; bucket config/metadata handlers; bucket services/repositories; admin/config documentation where policy overlaps | Per-configuration classification as `config-only`, `enforced`, `unsupported`, or `stub`. Store/retrieve/delete configuration state when config-only; implement enforcement only when backed by tests; mark unsupported features explicitly instead of implying S3 compatibility. | Each configuration API has a matrix row and tests for get/put/delete state or documented unsupported response; lifecycle/replication/notification execution is not claimed unless background behavior exists and is tested. |
| Versioning/delete markers: version IDs, latest resolution, delete markers, versioned listing, versioned delete/get/head | S3-P3 | Object-store domain/application/repositories; S3 object and bucket versioning handlers; storage-engine metadata/index modules | Version ID generation, latest-version resolution, delete marker creation, versioned list semantics, version-specific get/head/delete, null-version behavior if supported, and storage-engine persistence of version metadata and bytes. | Versioning state machine tests; delete markers affect unversioned reads as S3-compatible; versioned list/get/head/delete scenarios pass; storage-engine backend preserves versions across operations. |
| Access/security/public controls: bucket policy, public access block, ACL enforcement, expected owner, ownership controls | S3-P4 | S3 handlers; object/bucket metadata services; security/authorization layer if introduced; repositories for policy state | Separate stateful storage of policies, ACLs, public access block, expected-owner metadata, and ownership controls from authorization enforcement. Do not claim enforcement until request authorization paths use the stored state. | Policy/control CRUD tests can mark stateful storage; separate authorization tests must prove deny/allow behavior before `enforced`; expected-owner headers tested; unsupported enforcement is documented. |
| Encryption/object lock/retention: SSE headers, bucket encryption config, object lock config, legal hold, retention enforcement | S3-P4, with metadata-only pieces possibly S3-P2/S3-P3 | S3 object/bucket metadata/config handlers; object-store repositories; storage-engine metadata and byte-storage layers | Clarify metadata-only versus enforced behavior. Persist SSE request/config metadata only when no encryption at rest is implemented; separately implement and test real encryption, object-lock, legal-hold, and retention enforcement before semantic claims. | Metadata-only tests verify round-trip and headers; enforcement tests prove blocked delete/overwrite and retention expiry behavior before `semantically S3-compatible`; storage-engine encryption behavior is tested before claiming encryption at rest. |
| Analytics/inventory/metrics/intelligent-tiering | S3-P4 | Bucket config handlers/services; repository metadata tables; reports documentation | Likely config-only first. Store and return configuration documents; reports must state that no background analytics, inventory file generation, metrics publication, or tiering job behavior exists unless implemented. | Config CRUD tests; report matrix notes `config-only` and `no background job behavior`; no completion claim without generated reports/jobs and tests. |
| Replication/lifecycle/notification execution | S3-P4 | Bucket config services; storage-engine/application background-job infrastructure if introduced; eventing modules if introduced | Distinguish storing configuration from executing background behaviors: lifecycle expiration/transitions, replication copy, and notifications require schedulers/event processing and observable side effects. | Config storage can be stateful; execution needs integration tests proving expiration/transition, replicated objects, or emitted notifications; otherwise status remains config-only or stubbed. |
| Advanced/specialized APIs: `SelectObjectContent`, `RestoreObject`, torrent, Object Lambda, directory buckets/sessions | S3-P4 or separate scope | S3 specialized handlers; object services; storage-engine cold-storage/select/query infrastructure if introduced | Mark likely unsupported or separate scope unless implemented. `RestoreObject` may be metadata-only until archival tiers exist; Select/Object Lambda/directory buckets require explicit feature designs before semantic claims. | Unsupported APIs return documented unsupported/not-implemented responses or are labeled stubbed; any implemented subset has explicit scenarios and documented deviations. |
| Admin/storage-engine integration APIs | S3-P0 through S3-P4 depending on dependency | `admin-api-adapter`; `storage-engine-domain`; `storage-engine-reactive-application`; `storage-engine-reactive-infrastructure`; ACL adapter; `bootstrap-application`; docs | These are not AWS S3 APIs and must not become a parallel external API for S3 object semantics. They are required only to make non-S3 storage-engine concepts visible and testable: storage policy, device, topology, backend status, and admin validation/reporting. Current backend Admin API scope is configuration-as-code: read-only catalog endpoints expose YAML-backed policies/devices/disk sets, mutation requests are rejected, and policy validation does not persist. Future backend/status evidence must still prove the selected backend and storage-engine scenarios used by S3 operations. | Admin API tests prove read-only catalog and validation behavior; selected-backend/storage-engine tests prove runtime backend state separately; S3 storage-engine compatibility rows cite passing backend scenarios; docs keep admin APIs separate from S3 API coverage. |

### Required S3 Semantic Reporting Table Template

Every API coverage report must include this table shape or an equivalent machine-generated matrix. `Route mapped` is only one column and must never be summarized as completion by itself.

| Family | Operation | Route mapped | Stateful behavior | AWS CLI scenario | Storage-engine scenario | Semantic status | Notes |
|---|---|---|---|---|---|---|---|
| Example: Object CRUD | `PutObject` | Yes/No | Mapped / Stubbed / Stateful | Pass / Fail / Missing / Not applicable | Pass / Fail / Missing / Not applicable | Mapped / Stubbed / Stateful / AWS CLI compatible / Storage-engine compatible / Semantically S3-compatible | Scope limits, deviations, linked tests, or unsupported reason. |

### S3 Priority Roadmap

| Priority | Correction focus | Must happen before claiming completion |
|---|---|---|
| S3-P0 ✅ (documentation truth reset done; full inventory pending) | Replace any `111/111` completion claim with a semantic matrix and inventory all routes, handlers, services, repositories, tests, and AWS CLI scenarios. Documentation truth reset completed 2026-06-12: README.md downgraded `111/111` to route-inventory-only; docs/test-report.md adds semantic matrix and stale-report warning; JaCoCo documented as primary coverage baseline; admin API surface acknowledged. Full per-operation semantic inventory requires a dedicated java-tester inventory pass (previous attempt interrupted by usage limit). | API coverage report uses the required matrix; every operation has an initial classification; stubs and unsupported operations are explicitly labeled. |
| S3-P1 ⚠️ (first AWS CLI object CRUD increment complete) | Finish Object CRUD and Bucket baseline against both the in-memory backend and the storage-engine backend. AWS CLI first increment now verifies canonical object CRUD basics: put default headers, get content, head, list v1/v2, delete, idempotent delete, `STANDARD` storage class via object attributes, and slash-containing object keys. | Remaining before S3-P1 completion: metadata/checksum/ETag/copy semantics, fuller bucket state/list prefixes/delimiters/continuation tokens, and storage-engine scenarios pass. Slash-containing keys are now supported for the targeted WebTestClient and AWS CLI object-key scenarios by catch-all object routes and key normalization. |
| S3-P2 | Finish multipart and object metadata/tagging/ACL persistence. | Multipart part persistence, assembly, ETag, abort cleanup, part-copy behavior, and object metadata/tagging/ACL round-trip tests pass; unsupported enforcement is documented. |
| S3-P3 | Finish bucket configuration statefulness and versioning/delete markers. | Bucket config APIs are classified as config-only/enforced/unsupported/stub; version IDs, latest resolution, delete markers, and versioned list/get/head/delete are tested. |
| S3-P4 | Finish authorization/enforcement/background jobs/advanced APIs. | Authorization, public controls, retention/object-lock enforcement, encryption-at-rest behavior, lifecycle/replication/notification jobs, analytics/inventory/metrics/tiering jobs, and advanced APIs are either implemented with tests or explicitly out of scope/unsupported. |

### S3 Semantic Acceptance Criteria

- No operation may be documented as implemented unless it has a semantic classification and passes stateful tests for the supported behavior.
- AWS CLI parity is a separate mark; WebTestClient or route tests do not imply AWS CLI compatibility.
- Storage-engine compatibility is a separate mark; in-memory backend success does not imply storage-engine success.
- Stubbed APIs must return documented unsupported/not-implemented behavior or be labeled `stubbed` in reports and documentation.
- Config APIs must distinguish `config-only`, `enforced`, `unsupported`, and `stub` behavior.
- Metadata-only behavior for encryption, legal hold, retention, restore, or object lock must not be described as enforcement.
- Background behavior such as lifecycle execution, replication, notifications, inventory generation, metrics publication, intelligent tiering, or restore processing must not be claimed unless a tested background mechanism exists.
- Final S3 API completion claims must be family-based and evidence-based, not derived from route counts.

## Course Correction Plan — Storage Engine Configuration and MINIO_STANDARD

This section supersedes earlier planning statements where they conflict with [ADR 0016](docs/adr/0016-course-correction-storage-engine-policy-device-configuration-and-minio-standard-use-case.md). It is based on the current project audit and must drive the next implementation phases.

### Current Baseline Summary

- The project is a **Java 21 / Spring Boot 4 WebFlux S3-compatible object store**.
- Object-store reactive modules exist and are mostly tested:
  - `object-store-domain`
  - `object-store-reactive-repository-application`
  - `object-store-reactive-application`
  - `object-store-reactive-infrastructure`
  - `s3-reactive-api-adapter`
- Storage-engine modules exist but are **not yet reliably runtime-wired as the active backend**:
  - `storage-engine-domain`
  - `storage-engine-reactive-application`
  - `storage-engine-reactive-infrastructure`
  - `object-store-reactive-repository-storage-engine-infrastructure`
- `admin-api-adapter` exists; therefore documentation that says the project is strictly "S3-only" is stale. The S3 API remains the public object API, but admin/configuration APIs must be documented separately and limited to non-S3 storage-engine concepts such as policies, devices, topology, backend status, and validation/reporting.
- **JaCoCo is the current coverage baseline**. Clover/OpenClover is optional/legacy and must not be documented as the primary coverage gate.
- AWS CLI Cucumber is **not yet scenario-parallel** to the WebTestClient Cucumber suite. The AWS CLI object CRUD, bucket operations, metadata/tagging, and multipart increments currently include 26 passing scenarios, and dedicated storage-engine AWS CLI Phase 2 validation adds 4 passing filesystem reliability scenarios/examples. The AWS CLI path must continue toward parity where the AWS CLI can express the same behavior; Phase 2 filesystem reliability is validated only for its declared storage-engine AWS CLI scope, not as full S3 semantic parity.
- Storage-engine policy/device configuration is partially implemented: YAML-backed policy/device/disk-set catalogs and the Admin API now treat these entities as configuration-as-code/read-only at runtime; the concrete `MINIO_STANDARD` policy path is test-backed, while selected-backend runtime read/write evidence remains pending.

### Architecture Correction Goals

1. Keep chunking inside `DedupConfig`; do **not** introduce chunking as an independent pipeline step.
2. Make chunking configurable through `StoragePolicy` by owning chunk settings inside the policy's dedup configuration.
3. Use separate YAML files: **one YAML file per `StoragePolicy`**.
4. Use separate YAML files: **one YAML file per `StorageDevice`, disk set, or topology entity**.
5. Add YAML-backed repositories/catalogs for `StoragePolicy` and `StorageDevice`/topology entities.
6. Make `MINIO_STANDARD` the first storage policy use case, with explicit expected behavior and tests.
7. Add web/admin management for `StoragePolicy`, `StorageDevice`, and disk-set/topology entities as configuration-as-code/read-only runtime catalogs with non-persistent validation.
8. Make the storage-engine backend selectable and actually wired at runtime.
9. Make AWS CLI Cucumber execute the same canonical scenarios as WebTestClient Cucumber where possible.
10. Keep object-store and storage-engine bounded contexts separated by the existing ACL module.

### Module Ownership Map

| Concern | Primary modules / paths | Notes |
|---|---|---|
| S3 object API | `s3-reactive-api-adapter` | Public S3-compatible API and WebTestClient/AWS CLI Cucumber suites |
| Object-store domain/application | `object-store-domain`, `object-store-reactive-application`, `object-store-reactive-repository-application` | S3 aggregate and repository ports |
| In-memory backend | `object-store-reactive-infrastructure` | Default development backend |
| Storage-engine backend ACL | `object-store-reactive-repository-storage-engine-infrastructure` | Translates object-store repository calls to storage-engine orchestration |
| Storage engine domain | `storage-engine-domain` | `StoragePolicy`, `DedupConfig`, devices/topology, planning, manifests |
| Storage engine application | `storage-engine-reactive-application` | Orchestrator and ports such as `StoragePolicyCatalog` |
| Storage engine infrastructure | `storage-engine-reactive-infrastructure` | Filesystem backend, YAML catalogs/repositories, runtime config |
| Runtime assembly | `bootstrap-application`, root `pom.xml` | Profiles/properties/auto-configuration/backend selection |
| Admin API | `admin-api-adapter` | Storage policy/device management endpoints |
| Admin UI | `magrathea-ui` | Vue UI; implementation may require a frontend workflow/agent outside the current Java workflow |
| Documentation/reports | `README.md`, `PLAN.md`, `docs/**` | ADR/ARC42/test/quality consistency |

### Phased Correction and Implementation Plan

#### Phase 0 — Documentation/Decision Sync

| Field | Plan |
|---|---|
| Owner agent | `documenter` |
| Affected files/modules | `PLAN.md`, `README.md`, `docs/adr/0016-*.md`, `docs/arc42/**`, `docs/test-report.md`, `docs/quality-report.md` if present |
| Expected outputs | ADR 0016 recorded as accepted; plan updated; stale Clover-primary wording removed; S3-only/admin contradiction corrected; storage-engine runtime claims corrected from "done" to planned/verified status |
| Acceptance criteria | Documentation consistently says JaCoCo is current and Clover is optional/legacy; admin API is acknowledged; storage-engine backend is not claimed complete until Phase 7 passes; `MINIO_STANDARD` is visible as the first policy use case |
| Test gates | Documentation review; link checks for ADR/PLAN/README references |

Tasks:
- Mark ADR 0016 as the accepted correction decision.
- Update README/ARC42/test report wording so coverage gates name JaCoCo as current baseline.
- Replace "only public HTTP API is S3" with precise wording: S3 is the public object API; admin API exists for operational/configuration management.
- Remove or downgrade overclaims that the storage-engine backend is already selectable and wired end to end.

#### Phase 1 — Storage-Engine Nomenclature and Module/Wiring Strategy

| Field | Plan |
|---|---|
| Owner agent | `java-scaffolder` with `documenter` support |
| Affected files/modules | Root `pom.xml`, `bootstrap-application/pom.xml`, `bootstrap-application/src/main/resources/**`, `storage-engine-*/pom.xml`, `object-store-reactive-repository-storage-engine-infrastructure/pom.xml`, `README.md`, ARC42 section 5 |
| Expected outputs | Reactive naming parity documented as `storage-engine-reactive-application` and `storage-engine-reactive-infrastructure`; clear Spring component scan / auto-configuration / profile strategy; no duplicate repository beans when selecting a backend |
| Acceptance criteria | One documented backend-selection mechanism exists; `single-node` selects in-memory repositories; `storage-engine` selects storage-engine repositories; module names and package scans are explained; application fails fast on ambiguous repository beans |
| Test gates | `mvn validate`; Spring context test for default backend; Spring context test for storage-engine backend once Phase 7 beans exist |

Tasks:
- Keep documentation, POM/module references, C4, and ARC42 aligned to the reactive naming parity.
- Define package scanning/autoconfiguration boundaries for storage-engine beans.
- Choose one primary activation model: Spring profiles/properties, Spring Boot auto-configuration, or a documented combination.
- Define duplicate-bean prevention rules using profiles/conditions rather than relying on classpath ordering.

#### Phase 2 — StoragePolicy Domain Hardening

| Field | Plan |
|---|---|
| Owner agent | `java-domain-coder` |
| Affected files/modules | `storage-engine-domain/src/main/java/**/StoragePolicy.java`, `DedupConfig.java`, `EffectiveStoragePolicy.java`, `EffectivePolicyResolver.java`, `PersistencePlanner.java`, `VirtualDevice.java`, `WorkflowCompatibilityKey.java`; related domain tests to be added under `storage-engine-domain/src/test/java/**` |
| Expected outputs | Strong `StoragePolicy` and `DedupConfig` invariants; chunking owned by dedup config; no hard-coded dedup threshold unless explicitly policy-driven; workflow/device compatibility validation |
| Acceptance criteria | Invalid policies fail in the domain; dedup chunk size/alignment/threshold are part of policy data; `EffectivePolicyResolver` resolves from policy values; incompatible device/workflow combinations are rejected with explicit decision reasons |
| Test gates | `mvn test -pl storage-engine-domain`; domain tests for policy invariants, dedup config, effective policy resolution, and workflow/device compatibility |

Tasks:
- Keep chunking configuration inside `DedupConfig`.
- Model policy-driven dedup thresholds and remove hard-coded thresholds from `EffectivePolicyResolver`, or make any default threshold explicit in `StoragePolicy`.
- Validate compression/encryption/erasure-coding/replication/dedup combinations.
- Ensure `StoragePolicy` can be serialized/deserialized cleanly by infrastructure without leaking YAML concerns into the domain.

#### Phase 3 — StorageDevice/Topology Domain

| Field | Plan |
|---|---|
| Owner agent | `java-domain-coder` |
| Affected files/modules | New or updated files in `storage-engine-domain/src/main/java/com/example/magrathea/storageengine/domain/valueobject/**`; likely `StorageDevice`, `StorageDeviceId`, `DiskSet`, `PlacementGroup`, `FailureDomain`, `DeviceHealth`, `Capacity`, `StoragePath`; `VirtualDeviceResolver.java`; domain tests |
| Expected outputs | Domain model for physical/logical storage devices, disk sets, placement groups/failure domains, paths, capacity, and health metadata sufficient for `MINIO_STANDARD` |
| Acceptance criteria | `MINIO_STANDARD` can select a valid device/topology; unavailable/unhealthy/insufficient devices are rejected; topology entities remain domain-pure and framework-free |
| Test gates | `mvn test -pl storage-engine-domain`; topology/device validation tests |

Tasks:
- Define the minimum topology needed for the first use case, avoiding premature cluster complexity.
- Capture real storage paths and logical placement without binding the domain to `java.nio.file.Path` if that would leak infrastructure concerns.
- Add health and capacity metadata needed for validation and future placement decisions.

#### Phase 4 — YAML-Backed Repositories and Catalogs

| Field | Plan |
|---|---|
| Owner agent | `java-infra-coder` |
| Affected files/modules | `storage-engine-reactive-application/src/main/java/**/StoragePolicyCatalog.java`; new `StorageDeviceCatalog`/repository ports if needed; `storage-engine-reactive-infrastructure/src/main/java/**/yaml/**`; `storage-engine-reactive-infrastructure/src/main/resources/storage-policies/*.yaml`; `storage-engine-reactive-infrastructure/src/main/resources/storage-devices/*.yaml`; tests under `storage-engine-reactive-infrastructure/src/test/**` |
| Expected outputs | YAML-backed catalog/repository implementation for one policy file per `StoragePolicy` and one device/topology file per entity; validation and reload semantics |
| Acceptance criteria | Duplicate IDs fail; malformed YAML fails with actionable diagnostics; catalog loads all files from configured directories; reload behavior is documented as startup-only, manual reload, or watched reload; no source-code constants are required to define `MINIO_STANDARD` |
| Test gates | `mvn test -pl storage-engine-reactive-infrastructure -am`; repository integration tests with temporary YAML directories; negative validation tests |

Tasks:
- Define schema/version fields for policy and device YAML.
- Support external configuration directories in addition to classpath examples.
- Implement catalog lookups by policy ID and device/topology ID.
- Validate references between policies and devices/topologies.
- Decide reload semantics: immutable startup snapshot first unless live reload is explicitly planned and tested.

#### Phase 5 — `MINIO_STANDARD` Policy ✅ Completed

| Field | Plan / Result |
|---|---|
| Status | Completed 2026-06-12 for policy semantics, YAML catalog loading, device selection, and deterministic domain persistence planning. This does **not** complete storage-engine backend runtime read/write wiring; Phase 6 and Phase 7 remain pending. |
| Owner agent | `java-domain-coder` for behavior, `java-infra-coder` for YAML/examples, `java-tester` for executable scenarios |
| Affected files/modules | `storage-engine-domain`, `storage-engine-reactive-infrastructure/src/main/resources/storage-policies/minio-standard.yaml`, `storage-engine-reactive-infrastructure/src/main/resources/storage-devices/*.yaml`, `docs/**`, storage-engine tests |
| Expected outputs | Delivered: concrete YAML example and explicit Phase 5 behavior for `MINIO_STANDARD`: S3 storage class `STANDARD`, deduplication disabled, erasure-coding planning enabled as `4 data / 2 parity`, replication factor `1`, compression disabled, and encryption disabled by default. |
| Acceptance criteria | Met for Phase 5 scope: policy semantics are explicit, `storageClassId` is corrected to `STANDARD`, behavior is deterministic and testable, and tests verify selected policy/device behavior plus resulting persistence planning. Physical EC shard placement and storage-engine runtime read/write are not claimed by this phase. |
| Test gates | Passed: `storage-engine-domain` 152 tests / 0 failures, including `PersistencePlannerMinioStandardTest`; `storage-engine-reactive-infrastructure` 26 tests / 0 failures, including `MinioStandardIntegrationTest`. |
| Completion evidence | Prerequisites: Phase 2/3 commit `abb426e`; Phase 4 commit `d53b543`. Phase 5 domain planning commit `b0a5f74`; Phase 5 infrastructure/YAML integration commit `0ec84cf`. |

Completed scope:
- Chosen and documented explicit MinIO-compatible `STANDARD` behavior for `MINIO_STANDARD`: no deduplication, EC planning `4 data / 2 parity`, replication factor `1`, compression disabled, and encryption disabled by default, without claiming runtime backend read/write completion or verified physical EC shard placement.
- Verified deterministic domain persistence planning through `PersistencePlannerMinioStandardTest`.
- Verified YAML catalog/device integration through `MinioStandardIntegrationTest`; `minio-standard.yaml` comments were updated and `storageClassId` was corrected to `STANDARD` for Phase 5 semantics.

#### Phase 6 — Orchestrator/Dedup/Store Consistency

| Field | Plan |
|---|---|
| Owner agent | `java-domain-coder` and `java-infra-coder` |
| Affected files/modules | `storage-engine-reactive-application/src/main/java/**/ReactiveStorageOrchestrator.java`, `Chunker.java`, `ApplicationChunkPayload.java`; `storage-engine-reactive-application/src/main/java/**/ContentAddressIndex.java`, `ChunkStorePort.java`, `ObjectManifestRepository.java`; `storage-engine-reactive-infrastructure/src/main/java/**/FileSystemContentAddressIndex.java`, `FileSystemChunkStorePort.java`, `FileSystemManifestRepository.java`; `storage-engine-domain/src/main/java/**/ObjectManifest.java`, `ChunkId.java`, `ChunkReferenceDescriptor.java`, `ChunkPersistenceTrace.java` |
| Expected outputs | Consistent chunk IDs across dedup index, manifest, filesystem persistence, and reads; duplicate hits skip duplicate chunk persistence when dedup is enabled |
| Acceptance criteria | Reads use manifest chunk references that match persisted chunks; dedup index maps content hash/chunk ID consistently; duplicate uploads avoid duplicate chunk writes when dedup is enabled; non-dedup policy still persists/read streams correctly |
| Test gates | `mvn test -pl storage-engine-reactive-application,storage-engine-reactive-infrastructure -am`; orchestrator tests for single chunk, multi-chunk, duplicate content, non-dedup content, and read-after-write |

Tasks:
- Keep chunking inside dedup behavior, not as a separate pipeline step.
- Ensure the manifest records exactly the chunk IDs persisted or reused.
- Define transaction/rollback behavior for partial writes at least at the filesystem consistency level.
- Add streaming tests large enough to cross chunk boundaries.

#### Phase 7 — Storage-Engine Backend Wiring

| Field | Plan |
|---|---|
| Owner agent | `java-infra-coder` with `java-scaffolder` support |
| Affected files/modules | `bootstrap-application/src/main/java/**`, `bootstrap-application/src/main/resources/application*.properties` / `application*.yaml`, `object-store-reactive-infrastructure`, `object-store-reactive-repository-storage-engine-infrastructure`, `storage-engine-reactive-infrastructure`, Spring configuration/auto-configuration resources, root `pom.xml` profiles |
| Expected outputs | Spring configuration/profile/property selection between in-memory and storage-engine backends; no duplicate repository implementations active in one context |
| Acceptance criteria | Default runtime starts with in-memory backend; `storage-engine` profile/property starts with storage-engine backend; startup logs selected backend; missing YAML/device directories fail fast only when storage-engine backend is selected; object S3 operations can exercise the selected backend end to end |
| Test gates | Spring Boot context tests for both backend modes; `mvn test -pl bootstrap-application -am`; smoke test through S3 API using storage-engine profile |

Tasks:
- Define a property such as `magrathea.object-store.backend=in-memory|storage-engine` or profile-equivalent.
- Guard in-memory and storage-engine repository beans with mutually exclusive conditions.
- Wire `ReactiveStorageOrchestrator` and filesystem ports only when the storage-engine backend is active.
- Ensure the ACL module has all dependencies and no package-scan gaps.

#### Phase 8 — Web/Admin API and UI Planning ⚠️ (backend read-only Admin API done; UI implementation pending)

| Field | Plan |
|---|---|
| Owner agent | `java-infra-coder` for backend endpoints, `documenter` for API/UI plan, frontend implementation requires a frontend workflow/agent outside the current Java workflow |
| Affected files/modules | `admin-api-adapter/src/main/java/**`, `admin-api-adapter/src/main/resources/**`, `storage-engine-reactive-application` ports/services, `storage-engine-reactive-infrastructure` YAML repositories, `magrathea-ui/src/**` (planned only), `README.md`, ARC42 runtime/admin sections |
| Expected outputs | Backend Admin API exposes configuration-as-code/read-only endpoints for `StoragePolicy`, `StorageDevice`, and disk-set/topology catalogs; policy validation accepts JSON and returns structured validation results without persisting; UI screen plan covers policy list/detail/validation, device list/detail, disk-set/topology, and backend/status; S3 object API remains explicitly separate, and no storage-engine external endpoint duplicates S3 upload/read/delete/list semantics |
| Acceptance criteria | ✅ Backend exposes list/get for policies, devices, and disk sets; ✅ storage policy validation errors are structured and non-persistent; ✅ mutation endpoints for policies are documented as read-only/runtime-disabled; ⚠️ backend/status beyond `/admin/health` and selected-backend evidence remain planned; ⚠️ UI implementation remains pending until frontend workflow ownership is assigned before modifying `magrathea-ui` |
| Test gates | ✅ `mvn -B -pl admin-api-adapter -am test` — 9 tests, 0 failures, build success; UI tests only after frontend workflow is assigned |

Tasks:
- [x] Decide runtime mutability: YAML-backed storage policies, devices, and disk sets/topology are configuration-as-code and read-only at runtime.
- [x] Add admin API resources for policies and devices/topologies: `/admin/storage-policies`, `/admin/storage-devices`, and `/admin/disk-sets` list/detail endpoints.
- [x] Add non-persistent storage policy validation: `POST /admin/storage-policies/validate` validates request payloads and returns structured results without writing YAML or runtime state.
- [x] Document that create/update/delete policy requests are rejected at runtime (`405 Method Not Allowed`) and changes must be made through YAML configuration/redeploy or reload.
- [ ] Plan/implement a richer selected active backend/status contract beyond `/admin/health` when runtime backend evidence is available.
- [ ] Plan Vue screens: policy list, policy detail, policy validation/report; device list, device detail; disk-set/topology overview/detail; backend/status dashboard.
- [ ] Request an appropriate frontend workflow/agent before changing `magrathea-ui`; do not implement Vue screens in the Java workflow.

#### Phase 9 — Cucumber Parity ⚠️ (AWS CLI object CRUD, bucket operations, metadata/tagging, and multipart increment complete)

| Field | Plan / Result |
|---|---|
| Owner agent | `java-tester` |
| Affected files/modules | `s3-reactive-api-adapter/src/test/features/object-store/**`, `s3-reactive-api-adapter/src/test/features/awscli/**`, `s3-reactive-api-adapter/src/test/java/com/example/magrathea/s3api/cucumber/**`, `s3-reactive-api-adapter/src/test/java/com/example/magrathea/s3api/awscli/**`, `test-aws-cli.sh`, `docs/test-report.md` |
| Expected outputs | Canonical shared scenarios with WebTestClient and AWS CLI drivers; unsupported/CLI-only cases tagged when still applicable; shell script checks migrated into Cucumber or retained as a compatibility report with clear scope. First increment delivered targeted AWS CLI object CRUD parity for canonical basics, including slash-containing object keys, but not full S3 parity. |
| Acceptance criteria | Partial: AWS CLI Cucumber now covers the first canonical object CRUD increment, including slash-containing object keys. Full Phase 9 remains open until every canonical S3 scenario has either both `@webclient` and `@awscli` coverage or an explicit skip/unsupported reason; `MINIO_STANDARD` storage-engine scenarios can run through WebTestClient and AWS CLI where possible; AWS CLI environment requirements are documented. |
| Test gates | ✅ `mvn -B -pl s3-reactive-api-adapter -am test -Dsurefire.failIfNoSpecifiedTests=false` passed: 814 total, 803 passed, 0 failed/errors, 11 skipped. Current Cucumber/requirements counts: legacy WebTestClient 239 passed; existing AWS CLI parity suite 26 passed; dedicated storage-engine AWS CLI Phase 2 validation 4 passed, 0 failed, 0 skipped; isolated Phase 2 WebTestClient requirements 11 total examples, 7 passed and 4 `@awscli` examples excluded/skipped. Phase 3 pipeline validation is unit/application only, not WebTestClient/AWS CLI/e2e. Optional `bash test-aws-cli.sh` compatibility report remains separate/stale. |
| Completion evidence | Phase 9 slash-key production commit `9ff5a08`; tests commit `94f3e47`. Phase 9 bucket operations production fix commit `b2c333c`; tests commit `cdbcc9d`. Phase 9 metadata/tagging and multipart parity commit `a03bc4a`. |

Completed first-increment scope:
- AWS CLI `put-object` succeeds with default AWS CLI headers.
- AWS CLI `get-object` reads stored content.
- AWS CLI `head-object` finds an existing object.
- AWS CLI `list-objects` and `list-objects-v2` show a stored object.
- AWS CLI `delete-object` removes an existing object.
- AWS CLI `delete-object` is idempotent for an already deleted object.
- AWS CLI `put-object` stores `STANDARD` storage class, verified through object attributes.
- AWS CLI slash-containing object keys are supported by catch-all object routes and key normalization.

Completed bucket operations scope:
- AWS CLI `create-bucket` succeeds and `list-buckets` returns the created bucket.
- AWS CLI `head-bucket` returns 200 for an existing bucket.
- AWS CLI `get-bucket-location` returns the bucket region.
- AWS CLI `get-bucket-versioning` returns the default versioning state.
- AWS CLI `put-bucket-versioning` enables versioning; `S3BucketOperationsHandler.putBucketVersioning` accepts missing/octet-stream Content-Type as XML (fix commit `b2c333c`).
- AWS CLI `delete-bucket` removes an existing bucket.
- AWS CLI `head-bucket` returns 404 for a non-existent bucket.
- AWS CLI `delete-bucket` returns 404 for a non-existent bucket.
- AWS CLI `create-bucket` returns 409 for a duplicate bucket.

Completed metadata/tagging and multipart parity scope (commit `a03bc4a`):
- AWS CLI `get-bucket-acl` and `put-bucket-acl` succeed (bucket ACL read/write).
- AWS CLI `get-object-acl` and `put-object-acl` succeed (object ACL read/write).
- AWS CLI `put-bucket-tagging`, `get-bucket-tagging`, and `delete-bucket-tagging` succeed (bucket tagging CRUD).
- AWS CLI `put-object-tagging`, `get-object-tagging`, and `delete-object-tagging` succeed (object tagging CRUD).
- AWS CLI `get-object-attributes` returns ETag and ObjectSize.
- AWS CLI multipart upload full lifecycle: `create-multipart-upload`, `upload-part` (now returns ETag header), `list-parts`, `complete-multipart-upload`.
- AWS CLI `abort-multipart-upload` removes an in-progress upload.
- AWS CLI `list-multipart-uploads` lists in-progress uploads.
- Production fixes: `S3MultipartHandler.uploadPart` returns `ETag` response header; `S3BucketMetadataHandler.putBucketTagging` and `S3ObjectMetadataHandler.putObjectTagging` consume request body as `String` to avoid 415 from AWS CLI XML.

Tasks:
- [ ] Extract canonical scenario definitions from WebTestClient-only features where possible.
- [x] Add first-increment AWS CLI step definitions that execute equivalent canonical object CRUD behavior instead of maintaining an unrelated one-scenario suite.
- [x] Use tags such as `@webclient` and `@awscli` for the first increment; apply `@unsupported-awscli` or `@cli-only` only where future scenarios require explicit gaps.
- [ ] Decide whether `test-aws-cli.sh` becomes generated/legacy, or remains a separate broad compatibility smoke report.

#### Phase 10 — Quality Gates ✅ DONE

| Field | Result |
|---|---|
| **Status** | ✅ DONE (2026-06-12, HEAD `351d088`) |
| Owner agent | `java-tester` with `documenter` for report integration |
| Affected files/modules | Root `pom.xml`, all module POMs, `docs/test-report.md`, JaCoCo report paths under `*/target/site/jacoco/**` |
| `mvn validate` | ✅ BUILD SUCCESS — all module POMs valid |
| `mvn test` | Historical Phase 10 gate: ✅ 768 tests, 0 failures, 0 errors, 0 skipped (all 10 modules). Latest Phase 3 validation: ✅ 833 tests, 0 failures, 0 errors, 11 skipped. |
| `mvn -Pcoverage test jacoco:report` | ✅ JaCoCo reports generated; see coverage table below |
| Acceptance criteria | All required Maven test gates documented and run; reports use JaCoCo as baseline; docs and tests agree on AWS CLI parity status |

**JaCoCo Coverage — Phase 10 (`mvn -Pcoverage test jacoco:report`)**

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

Verified gates (all ✅):

```bash
mvn validate                                                             # ✅ BUILD SUCCESS
mvn test                                                                 # Historical Phase 10: ✅ 768 tests, 0 failures; latest Phase 3 validation: ✅ 833 tests, 0 failures, 0 errors, 11 skipped
mvn test -pl storage-engine-domain -am                                   # ✅ 149 tests
mvn test -pl storage-engine-reactive-application -am                     # ✅ 4 tests
mvn test -pl storage-engine-reactive-infrastructure -am                  # ✅ 29 tests
mvn test -pl object-store-reactive-repository-storage-engine-infrastructure -am  # ✅ 1 test
mvn test -pl bootstrap-application -am                                   # ✅ 6 tests
mvn test -pl s3-reactive-api-adapter -am -Dsurefire.failIfNoSpecifiedTests=false  # ✅ passed; latest counts: 239 legacy WebTestClient passed, 26 existing AWS CLI parity scenarios passed, 4 dedicated Phase 2 AWS CLI scenarios/examples passed, 7/11 Phase 2 WebTestClient examples passed with 4 @awscli examples excluded/skipped
mvn -Pcoverage test jacoco:report                                        # ✅ reports generated
```

### Cross-Phase Acceptance Checklist

- [ ] ADR 0016 is accepted and all docs align with it.
- [x] JaCoCo is documented as the current coverage baseline; Clover is optional/legacy only.
- [ ] Admin API presence is documented and no longer contradicted by S3-only wording.
- [ ] `StoragePolicy` owns dedup/chunk configuration through `DedupConfig`.
- [x] One YAML file exists per `StoragePolicy`.
- [x] One YAML file exists per `StorageDevice`, disk set, or topology entity.
- [x] YAML-backed policy and device/topology catalogs validate IDs, schemas, and references.
- [ ] `MINIO_STANDARD` is explicitly defined, loaded from YAML, and testable.
- [x] Storage-engine backend can be selected at runtime without duplicate repositories.
- [x] S3 write/read path works end to end with the selected storage-engine backend.
- [x] Backend Admin API exposes read-only policy/device/disk-set catalogs and non-persistent validation as configuration-as-code.
- [ ] Admin UI plan covers policy/device/disk-set/backend-status screens and awaits frontend workflow ownership.
- [ ] AWS CLI Cucumber parity exists for canonical scenarios where possible. Object CRUD, bucket operations, metadata/tagging, and multipart increments are complete in the existing parity suite: 26 AWS CLI scenarios total, all run, 0 skipped, including slash-containing keys, bucket lifecycle operations, ACL/tagging (bucket and object), object attributes, and multipart upload lifecycle. Dedicated storage-engine AWS CLI Phase 2 filesystem reliability validation is also complete for declared scope: 4 scenarios/examples passed, 0 failed, 0 skipped. Full AWS CLI/WebTestClient parity beyond these scopes remains pending.
- [x] Documentation reports planned vs completed work accurately.

### Risk Table

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| Storage-engine backend wiring | Duplicate beans, wrong backend selected, or storage-engine code not exercised end to end | Use mutually exclusive Spring conditions/profiles; add context tests for both backends; fail fast on ambiguous wiring | `java-infra-coder`, `java-scaffolder` |
| Policy schema design | YAML becomes unstable or too coupled to current classes | Add schema versioning; keep domain framework-free; validate examples; document backwards compatibility expectations | `java-domain-coder`, `java-infra-coder` |
| Data migration | Future policy/device schema changes may strand existing manifests/chunks | Version manifests and YAML schemas; document no-migration assumption for first implementation; add migration ADR before persistent compatibility claims | `java-domain-coder`, `documenter` |
| Large-object streaming | Buffering entire objects or chunks may break WebFlux behavior and memory use | Add large-object/chunk-boundary tests; keep Flux streaming at application boundaries; avoid whole-object aggregation except in tests/in-memory adapters | `java-infra-coder`, `java-tester` |
| Real disk placement | Filesystem paths, capacity, health, and failure-domain behavior may diverge from simple tests | Model minimum topology now; add temporary-directory integration tests; document real-disk limitations until operational tests exist | `java-domain-coder`, `java-infra-coder` |
| MinIO semantic ambiguity | `MINIO_STANDARD` may be interpreted as single-node, replicated, EC, or dedup-backed | Phase 5 fixes the documented semantics as S3 `STANDARD`, dedup disabled, EC planning `4 data / 2 parity`, replication factor `1`, compression disabled, and encryption disabled by default; continue to avoid claims about runtime backend read/write or verified physical EC shard placement until tested | `java-domain-coder`, `documenter` |
| AWS CLI environment dependency | CLI tests may fail due to missing AWS CLI, credentials, endpoint, or port conflicts | Provide tagged tests, preflight checks, endpoint overrides, and clear skip/failure reporting | `java-tester` |
| Frontend workflow availability | Vue UI work may be blocked because current workflow is Java-focused | Plan backend contracts first; request frontend workflow/agent before modifying `magrathea-ui`; keep Java admin API independently testable | `documenter`, `java-infra-coder` |

---

## Existing Project Structure and Useful Historical Plan Content

The following sections preserve useful existing project context. They must be read in light of the correction plan above.

## Maven Modules

```text
magrathea-objectstorage/
├── pom.xml
├── s3-reactive-api-adapter/                    # Pluggable S3 HTTP adapter (RouterFunction, XML, Cucumber tests)
├── admin-api-adapter/                          # Admin/configuration API adapter
├── object-store-domain/                        # Pure S3 domain: aggregates, value objects, domain events
├── object-store-reactive-repository-application/ # Reactive CQS repository interfaces
├── object-store-reactive-application/          # Reactive application services and DTOs
├── object-store-reactive-infrastructure/       # Reactive in-memory repository implementations
├── storage-engine-domain/                      # Storage Engine domain: policy, workflow, device, trace, manifest
├── storage-engine-reactive-application/        # Storage Engine reactive orchestration and ports
├── storage-engine-reactive-infrastructure/     # Storage Engine filesystem cluster backend and future YAML catalogs
├── object-store-reactive-repository-storage-engine-infrastructure/ # ACL + adapter: Object Store → Storage Engine
├── bootstrap-application/                      # Spring Boot entry point
├── magrathea-ui/                               # Vue UI assets and documentation dashboard
├── docs/                                       # ARC42, ADR, C4, reports
└── test-aws-cli.sh                             # AWS CLI compatibility script/report path
```

### Two-Backend Repository Strategy — Target State

| Backend | Module | Activation | Description |
|---|---|---|---|
| In-memory | `object-store-reactive-infrastructure` | Default `single-node`/development mode | Reactive in-memory repositories for development and tests |
| Storage Engine | `object-store-reactive-repository-storage-engine-infrastructure` + `storage-engine-*` | `storage-engine` profile/property after Phase 7 | Filesystem-backed storage-engine bounded context |

The ACL/adapter module translates object-store repository commands into storage-engine commands. It remains the anti-corruption layer between the two bounded contexts.

Removed modules/components from earlier iterations:
- `shared-domain` removed.
- `InternalApiRouter` removed because it was not standard S3.
- Legacy blocking application/infrastructure module entries are obsolete; current runtime paths use reactive modules.

## S3 API Handler Organization

`s3-reactive-api-adapter` route mapping is intentionally split by context:

| Class | Responsibility |
|---|---|
| `S3PathRouter` | Route composition only |
| `S3BucketOperationsHandler` | Bucket lifecycle, bucket-level listings, location/versioning, directory-bucket listing |
| `S3BucketMetadataHandler` | Bucket ACL and tagging metadata |
| `S3BucketConfigHandler` | Bucket configuration, object-lock configuration, ABAC, and Phase F bucket metadata/table configurations |
| `S3ObjectOperationsHandler` | Object CRUD, copy, multi-delete, torrent, restore, rename, select, and Object Lambda response operations |
| `S3ObjectMetadataHandler` | Object ACL, tagging, attributes, legal hold, retention, and encryption metadata |
| `S3MultipartHandler` | Multipart upload lifecycle |
| `S3SessionHandler` | Phase F session creation |
| `S3WebSupport` | Shared request predicates and S3 XML error helpers |

`ContentStore` has been removed. Object content is saved through reactive object repository commands. Domain interfaces expose no framework types; reactive application implementations carry `Flux<DataBuffer>` at the application boundary.

## Pluggable S3 API

`s3-reactive-api-adapter` is loaded through Spring Boot auto-configuration:
- `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- `S3ApiConfig` guarded by reactive bucket/object service classes
- `S3ApiConfig` guarded by `s3.api.enabled=true` (`true` by default)

Activation modes:

| Mode | Behavior |
|---|---|
| `s3-reactive-api-adapter` dependency present + `s3.api.enabled=true` | S3 routes are active |
| `s3-reactive-api-adapter` dependency present + `s3.api.enabled=false` | S3 routes disabled |
| `s3-reactive-api-adapter` dependency absent | No S3 web API loaded |

## Testing and Coverage Strategy — Target Wording

| Level | Type | Command | Notes |
|---|---|---|---|
| 1 | Pure JUnit | `mvn test -pl object-store-domain` and `mvn test -pl storage-engine-domain -am` | Domain only, no Spring |
| 2 | Module tests | `mvn test -pl <module> -am` | Run affected modules during each phase |
| 3 | WebTestClient Cucumber | `mvn test -pl s3-reactive-api-adapter -am -Dsurefire.failIfNoSpecifiedTests=false` | Canonical S3 behavior through in-process WebFlux driver |
| 4 | AWS CLI Cucumber | `mvn -B -pl s3-reactive-api-adapter -am test -Dsurefire.failIfNoSpecifiedTests=false` | Existing object CRUD/bucket/metadata/multipart parity suite plus dedicated Phase 2 storage-engine AWS CLI validation run in the S3 adapter suite; must continue sharing canonical scenarios where possible |
| 5 | AWS CLI compatibility script | `bash test-aws-cli.sh` | May remain as separate compatibility report if not fully migrated |
| 6 | JaCoCo coverage | `mvn -Pcoverage test jacoco:report` or project-finalized equivalent | Current coverage baseline |
| 7 | Clover/OpenClover | Optional/legacy only | Do not use as primary gate unless a future ADR revives it |

## Historical Handler Correction Plan

The handler minimal-pattern correction remains useful historical context and is not the focus of ADR 0016.

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

### Already Cleaned Up (Minimal Pattern Applied)

All handlers except `S3BucketConfigHandler` were previously cleaned up to follow the minimal handler pattern: **extract → delegate → response**.

| Handler | Status | Notes |
|---|---|---|
| `S3ObjectOperationsHandler` | Done | Delegates to `ReactiveObjectService`; no bucket checks, no validation, no ETag |
| `S3ObjectMetadataHandler` | Done | Delegates to `ReactiveObjectService`; no bucket checks, no handler-local state |
| `S3MultipartHandler` | Done | Delegates to `ReactiveMultipartUploadService`; no bucket checks, no ETag |
| `S3BucketOperationsHandler` | Done | Delegates to `ReactiveBucketService`; no CORS/website, no validation |
| `S3BucketMetadataHandler` | Done | Delegates to `ReactiveBucketService`; no handler-local state, no CORS |
| `S3SessionHandler` | Done | Delegates to `ReactiveBucketService.createSession()` |
| `S3BucketConfigHandler` | Postponed | Complex registry pattern and local state require separate cleanup |

### Postponed Repository-Layer Items from Earlier Plan

| Item | Target | Status |
|---|---|---|
| Validation | Repository preconditions | Postponed |
| Bucket existence checks | Repository preconditions | Postponed |
| Bucket name validation | Repository preconditions | Postponed |
| ETag computation | Repository | Postponed |
| Handler-local ACL/grants/tagging | Repository | Postponed |
| CORS validation | Repository/service | Postponed |
| Website routing | Repository/service | Postponed |
| `S3BucketConfigHandler` cleanup | Separate phase | Postponed |

## TODO / Future Work

- [ ] Execute the course correction phases in order, starting with documentation synchronization and storage-engine wiring strategy.
- [ ] Replicate canonical WebTestClient Cucumber scenarios as AWS CLI Cucumber scenarios where possible.
- [ ] Keep this plan updated as phases move from planned to implemented, with test evidence before marking work complete.
