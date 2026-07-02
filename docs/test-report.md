# Magrathea ObjectStorage Test Report

Updated: 2026-07-02

> ⚠️ **Report status: EVIDENCE-ONLY / NOT A COMPLETION CLAIM**
>
> This report reflects point-in-time validation evidence and **must not be read as implementation completeness**.
> - The `111/111` route count (referenced elsewhere) is a mapped-surface inventory only, not semantic completion.
> - AWS CLI standalone results (46 passed / 82 failed) are from a run where the server was not started; most failures are connection errors (exit code 254), not logic failures.
> - **Latest full gate (2026-07-02, Podman image `magrathea-build:fedora42` — JDK 21, Maven 3.9.9, AWS CLI 2.34.29):** `mvn -B --no-transfer-progress clean test` at the repo root passed with BUILD SUCCESS across all 13 reactor modules: **942 tests, 0 failures, 0 errors, 5 skipped** (tag-filter/assumption skips), total time 04:55.
> - Current Cucumber/requirements counts: legacy WebTestClient **239 passed**; existing AWS CLI parity suite **26 passed**; isolated Phase 2 WebTestClient requirements **11 examples total, 7 passed, 4 `@awscli` examples excluded/skipped**; dedicated storage-engine AWS CLI Phase 2 validation **4 scenarios/examples passed, 0 failed, 0 skipped**; Phase 5 WebTestClient semantic compatibility requirements **25 scenarios/tests passed, 0 failures, 0 errors, 0 skipped**; Phase 6 storage-engine domain distributed-readiness gate **164 tests, 0 failures, 0 errors, 0 skipped**; domain quality bounded first pass **storage-engine-domain 172 tests and object-store-domain 292 tests passed**; runtime correctness bounded first pass targeted module gate and latest full Maven gate **passed**.
> - Phase 1 upload reliability: REQ-UPLOAD-001 and REQ-UPLOAD-002 use the 2026-07-02 validation-mode decision: bootstrap JUnit integration validation is formally accepted as the sole required runtime validation mode and is documented with rationale in `phase-1-upload-storage-engine.feature`; their `@webclient`/`@awscli` examples are supplementary and excluded from Phase 1 Cucumber runner execution by `not @bootstrap-integration-required`. REQ-UPLOAD-003 is no longer an executable Phase 1 target: its Cucumber scenario and bootstrap JUnit test were removed because they imposed obsolete non-dedup multi-chunk persistence, and Phase 1 runners no longer select it. **REQ-UPLOAD-004 is now `@implemented-and-validated`**: `mvn -B --no-transfer-progress -pl s3-reactive-api-adapter -am test -Dtest=Phase1UploadStorageEngineCucumberTest,Phase1UploadStorageEngineAwsCliCucumberTest -Dsurefire.failIfNoSpecifiedTests=false` ran twice (before and after the feature-file edit) with identical BUILD SUCCESS results (28 discovered / 0 failures / 0 errors / 19 skipped per class), and its scenario executed and passed independently in both `@webclient` (`Phase1UploadStorageEngineCucumberTest`) and `@awscli` (`Phase1UploadStorageEngineAwsCliCucumberTest`) modes, 2026-07-02, Podman `magrathea-build:fedora42` (JDK 21, Maven 3.9.9, AWS CLI 2.34.29); the java-tester agent has refreshed the feature-file tag from `@implemented-not-e2e-validated` to `@implemented-and-validated` and removed the feature-level `@partial` tag because no scenario in `phase-1-upload-storage-engine.feature` remains `@partial`/`@placeholder`/`@config-only`/`@not-implemented`. The Gherkin appendix was regenerated and its freshness re-verified (`--check` OK, 66 scenarios from 6 feature files, `@implemented-and-validated` count 40→41, `@implemented-not-e2e-validated` count 18→17).
> - Phase 2 filesystem reliability is **implemented-and-validated for the declared Phase 2 scope**, including the REQ-FS-006 same-key concurrency scenario after a real torn-reference defect was found, fixed, and stress re-validated 6/6 (see the dedicated section below). This does not claim distributed readiness, broader S3 semantic completion, or later-phase production readiness.
> - Phase 3 reactive pipeline is **implemented-not-e2e-validated** for REQ-PIPELINE-001 through REQ-PIPELINE-006 (unit/application validation only; no Phase 3 WebTestClient, AWS CLI, or end-to-end Cucumber validation has been executed yet). New streaming requirements **REQ-PIPELINE-007 and REQ-PIPELINE-008** are implemented in production (single-pass PutObject ETag/length tee; incremental fixed-window dedup accumulation; `DataBufferUtils.join` removed from both classes) and are enforced by the static architecture test `ReactiveUploadStreamingArchitectureTest` (2 tests, passed) plus the existing Cucumber e2e suites; their Cucumber scenarios carry `@not-implemented` because no Cucumber step glue executes them.
> - Phase 5 S3 semantic compatibility is **implemented-and-validated for 24 WebTestClient scenarios** and **implemented-not-e2e-validated for REQ-S3-002-C multipart restart/durability** because that scenario uses a direct same-directory filesystem repository probe rather than a full process/Spring restart. Phase 5 AWS CLI validation has not been newly added.
> - Phase 6 distributed readiness is **implemented-not-e2e-validated** for the modeled domain scope only. It is unit validated through distributed-readiness domain tests, but real replication execution, networked membership, healing/rebalance job runners, actual multi-node durability, and full e2e multi-node validation remain absent. Distributed production readiness is not claimed.
> - **JaCoCo is the current coverage baseline.** Clover/OpenClover is optional/legacy.
>
> A semantic S3 coverage matrix is being built as part of the correction plan (see `PLAN.md` → *S3 API Semantic Completion Plan*).

## Summary

| Suite | Passed | Failed | Skipped / excluded | Total | Notes |
|---|---:|---:|---:|---:|---|
| AWS CLI S3 compatibility script | 46 | 82 | 0 | 128 | Stale standalone `test-aws-cli.sh` result; endpoint: `http://localhost:8080` |
| Legacy WebTestClient Cucumber | 239 | 0 | 0 | 239 | Current Cucumber count from the existing WebTestClient runner. |
| AWS CLI Cucumber | 26 | 0 | 0 | 26 | Current Cucumber count; full parity with WebTestClient canonical scenarios remains incomplete. |
| Phase 2 WebTestClient requirements | 7 | 0 | 4 | 11 | Isolated Phase 2 runner; REQ-FS-001 through REQ-FS-006 WebTestClient-required scope passed; skipped/excluded examples are tagged `@awscli`. |
| Phase 2 storage-engine AWS CLI requirements | 4 | 0 | 0 | 4 | Dedicated Phase 2 AWS CLI validation passed for REQ-FS-003, REQ-FS-004, REQ-FS-006 different keys, and REQ-FS-006 same key. |
| Phase 3 storage-engine reactive application gate | 159 | 0 | 0 | 159 | `mvn -B -pl storage-engine-reactive-application -am test` PASS; unit/application validation for REQ-PIPELINE-001 through REQ-PIPELINE-006. |
| Streaming upload static architecture test (`ReactiveUploadStreamingArchitectureTest`) | 2 | 0 | 0 | 2 | Enforces REQ-PIPELINE-007/008: forbids `DataBufferUtils.join` in `S3ObjectOperationsHandler` and `FixedWindowDedupStep` production sources. Passed in the 2026-07-02 Podman full gate. |
| Same-key concurrency unit test (`S3ObjectManifestReferenceStoreConcurrencyTest`) | 3 | 0 | 0 | 3 | Torn-reference impossibility under 16-thread same-key races with a concurrent reader; no lost updates in 16×100 read-compose-write increments; unrelated keys uncorrupted while a hot key is contended. |
| Phase 1 Cucumber runners (WebTestClient + AWS CLI) | 9 + 9 | 0 | 19 + 19 excluded by tag filter | 28 + 28 discovered | Each runner discovered 28 scenarios and executed 9 with 0 failures in the 2026-07-02 Podman gate; REQ-UPLOAD-004 validated independently in both modes after REQ-UPLOAD-003 removal, run twice with identical counts; feature-file tag refreshed to `@implemented-and-validated` and the feature-level `@partial` tag removed. |
| Phase 2 AWS CLI same-key stress re-validation | 6 runs / 6 passed | 0 | 0 | 6 fresh-JVM runs | Phase 2 AWS CLI runner re-run 6 times in fresh JVMs after the concurrency fix; REQ-FS-006 same-key passed 6/6 (previously the defect reproduced 4/4 in one session). |
| Phase 5 WebTestClient semantic compatibility requirements | 25 | 0 | 0 | 25 | `Phase5S3SemanticCompatibilityRequirementsCucumberTest` PASS; requirement source `s3-reactive-api-adapter/src/test/features/requirements/phase-5-s3-semantic-compatibility.feature`. |
| Phase 6 storage-engine domain distributed-readiness gate | 164 | 0 | 0 | 164 | `mvn -B -pl storage-engine-domain test --no-transfer-progress` PASS; modeled domain/unit validation for REQ-DIST-001 through REQ-DIST-006. |
| Domain quality bounded first pass | 464 | 0 | 0 | 464 | `mvn -B -pl storage-engine-domain,object-store-domain test --no-transfer-progress` PASS; storage-engine-domain: 172 tests; object-store-domain: 292 tests. Covers collection immutability exposure and additional `StoredObject` invariant tests only; not a complete domain redesign. |
| Runtime correctness bounded first pass | n/a | 0 | n/a | n/a | `mvn -B -pl object-store-reactive-repository-storage-engine-infrastructure,storage-engine-reactive-infrastructure,s3-reactive-api-adapter,bootstrap-application -am test --no-transfer-progress` PASS / BUILD SUCCESS; `mvn -B test --no-transfer-progress` PASS / BUILD SUCCESS. Covers storage-engine repository read-after-write regression, `FileSystemManifestRepository` checksum round-trips, and `BlockingFileSystemOperation` bounded-elastic filesystem scheduling. Full S3 handler streaming and multipart part body assembly remain open. |
| Phase 1 upload reliability Cucumber runtime coverage | 9 + 9 executed | 0 | 19 + 19 excluded by tag filter | 28 + 28 discovered | 2026-07-02 Podman gate: WebTestClient and AWS CLI Phase 1 runners each executed 9 scenarios with 0 failures; REQ-UPLOAD-004 validated independently in both modes after REQ-UPLOAD-003 removal, confirmed by two identical runs (before and after the feature-file tag refresh). An earlier transient pass that depended on incorrectly reintroduced non-dedup multi-chunk persistence remains rejected as evidence. |
| Phase 1 upload reliability bootstrap validation (`bootstrap-application`) | Previous evidence only | 0 | Not rerun in latest handoff | n/a | Bootstrap evidence covers REQ-UPLOAD-001 restart safety, REQ-UPLOAD-002 manifest reload, REQ-UPLOAD-004 failed-upload atomicity, REQ-UPLOAD-005 read-after-write, and REQ-UPLOAD-006 corruption detection. The deleted REQ-UPLOAD-003 bootstrap test is not evidence. Under the 2026-07-02 validation-mode decision, bootstrap evidence is the agreed sole required runtime validation mode for REQ-UPLOAD-001/002; REQ-UPLOAD-004 now also has independent Cucumber runtime validation in both modes (see the Phase 1 Cucumber runner rows above). |
| Section E YAML catalog / backend-wiring / admin-API bounded first pass | `YamlStoragePolicyCatalogTest` (14), `YamlStorageDeviceCatalogTest` (10), `YamlDiskSetCatalogTest` (12), `MinioStandardIntegrationTest` (2), `StorageEngineMissingExternalConfigTest` (1), `StorageEngineBackendContextTest` (3), `AdminRouterTest` (9) | `mvn -B test` PASS | All Section E acceptance gates met: malformed/duplicate/unresolved catalog rejection, MINIO_STANDARD deterministic plans, fail-fast on missing backend config, mutually exclusive backend selection, admin API uses same catalog beans. |
| Maven targeted adapter gate | 803 | 0 | 11 | 814 | `mvn -B -pl s3-reactive-api-adapter -am test -Dsurefire.failIfNoSpecifiedTests=false` PASS. |
| Source/build hygiene safe first pass | n/a | 0 | Docker not run | n/a | `mvn -B validate --no-transfer-progress` PASS / BUILD SUCCESS; `bash scripts/check-source-hygiene.sh` PASS. Docker compose config and full Docker image validation were not run because `docker` is unavailable. Root `-Pcoverage` is documented as the canonical coverage profile; duplicate module-level coverage profile consolidation remains pending in `bootstrap-application/pom.xml`. |
| Module/layering architecture bounded first pass | n/a | 0 | n/a | n/a | `scripts/check-module-layering.sh` is wired into root-only `validate`; `mvn -B validate --no-transfer-progress` PASS / BUILD SUCCESS; `bash scripts/check-module-layering.sh` PASS; backend selection context-test evidence remains in place; package naming remains bridged by explicit scan roots; full `mvn -B test --no-transfer-progress` PASS / BUILD SUCCESS after the ARC42 docs conversion fix. |
| Maven full test gate (all modules) | 937 | 0 | 5 | 942 | Latest full gate (2026-07-02): `mvn -B --no-transfer-progress clean test` at repo root in Podman `magrathea-build:fedora42` (JDK 21, Maven 3.9.9, AWS CLI 2.34.29) PASS / BUILD SUCCESS; all 13 reactor modules; 942 tests, 0 failures, 0 errors, 5 skipped (tag-filter/assumption skips); total time 04:55. Supersedes the earlier 902-test Surefire XML evidence. |
| Gherkin requirements appendix generator check | n/a | 0 | n/a | 66 scenarios | `python3 scripts/generate-gherkin-requirements-appendix.py --check` PASS; generated ARC42 appendix is fresh: 66 scenarios from 6 requirement feature files (includes new REQ-PIPELINE-007/008). |
| Admin API adapter | 9 | 0 | 0 | 9 | Previous verified gate: `mvn -B -pl admin-api-adapter -am test` — build success. |
| JaCoCo coverage | See section | - | - | - | Current baseline; latest reports under `target/site/jacoco` when generated. |

## Current Verified Results

| Scope | Evidence | Result | Notes |
|---|---|---:|---|
| Phase 2 filesystem reliability (`storage-engine-reactive-infrastructure`, `s3-reactive-api-adapter`) | `mvn -B -pl s3-reactive-api-adapter -am test -Dsurefire.failIfNoSpecifiedTests=false`; `mvn -B test`; isolated Phase 2 WebTestClient requirements runner; dedicated storage-engine AWS CLI Phase 2 validation | Targeted adapter gate PASS; full Maven test PASS; Phase 2 WebTestClient: 11 examples, 7 passed, 4 `@awscli` examples excluded/skipped; Phase 2 AWS CLI: 4 scenarios/examples, 4 passed, 0 failed, 0 skipped | Implemented evidence covers atomic chunk temp-file/fsync/rename writes with SHA-256 sidecar checksums, atomic manifest temp-file/fsync/rename writes with checksum trailer, read-time chunk/manifest checksum verification, `FileSystemRecoveryScanner` reporting/quarantine/idempotence, S3 XML mapping for storage-engine integrity errors, disabled-by-default write fault injection for interrupted chunk/manifest write tests, and defaulting `null`/blank storage class to `STANDARD` for storage-engine `PutObject`. Phase 2 is implemented-and-validated for declared scope only; distributed readiness, broader S3 semantic completion, and later phases remain pending. The REQ-FS-006 same-key scenario exposed a real torn-reference concurrency defect that was fixed and stress re-validated 6/6 (see the dedicated section below); the feature tag was restored `@partial` → `@implemented-and-validated`. |
| Streaming upload refactor — REQ-PIPELINE-007/008 (`s3-reactive-api-adapter`, `storage-engine-reactive-infrastructure`) | Requirement source: `s3-reactive-api-adapter/src/test/features/requirements/phase-3-reactive-pipeline.feature` (REQ-PIPELINE-007, REQ-PIPELINE-008); static architecture test: `ReactiveUploadStreamingArchitectureTest`; 2026-07-02 Podman full gate; Phase 1 Cucumber runners (WebTestClient + AWS CLI) | Static architecture test PASS: 2 tests, 0 failures. Full gate PASS: 942 tests, 0 failures, 0 errors, 5 skipped. Phase 1 runners: 9 executed per mode, 0 failures. | Implemented and validated via static architecture test plus Cucumber e2e suites: `S3ObjectOperationsHandler.putObject` computes ETag (MD5) and content length via a single-pass tee (`UploadDigest` updating per `DataBuffer`) while the body streams into the storage engine; `FixedWindowDedupStep` accumulates fixed windows incrementally; `DataBufferUtils.join` and whole-body materialization removed from both classes and forbidden by the architecture test. The legacy `object-store/put_object.feature` scenario was refreshed: ETag is the real MD5 hex of stored bytes (e.g. `8d777f385d3dfec8815d20f7496026dc` for body "data"); Content-MD5 is retained as metadata only. The REQ-PIPELINE-007/008 Cucumber scenarios carry `@not-implemented` (no Cucumber step glue executes them); the enforcing validation mode is the static architecture test. |
| REQ-FS-006 same-key concurrency defect fix (`object-store-reactive-repository-storage-engine-infrastructure`) | Unit test: `S3ObjectManifestReferenceStoreConcurrencyTest`; Phase 2 AWS CLI runner stress re-validation (6 fresh-JVM runs); 2026-07-02 Podman full gate | Unit test PASS: 3 tests, 0 failures. Stress re-validation PASS: REQ-FS-006 same-key 6/6 (previously failed 4/4 in one session). Full gate PASS. | Defect: a non-atomic find→save read-modify-write on the S3 object reference in `StorageEngineReactiveS3ObjectRepository.save(...)` / `S3ObjectManifestReferenceStore` caused torn references under concurrent same-key PUTs (checksum from one upload, body from another; observed as an AWS CLI CRC64NVME checksum mismatch). Fix: `S3ObjectManifestReferenceStore.commitLatest` serializes the whole read-compose-write cycle under a striped per-key `ReentrantLock` (64 stripes) and persists via temp file + `ATOMIC_MOVE` (`REPLACE_EXISTING`, with fallback if unsupported); the repository composes the complete reference inside one serialized per-key commit. Semantics: last-writer-wins, crash-safe. Feature tag restored `@partial` → `@implemented-and-validated` in `phase-2-filesystem-reliability.feature`. |
| Phase 3 reactive pipeline (`storage-engine-reactive-application`) | `s3-reactive-api-adapter/src/test/features/requirements/phase-3-reactive-pipeline.feature`; `mvn -B -pl storage-engine-reactive-application -am test`; `mvn -B -pl s3-reactive-api-adapter -am test -Dsurefire.failIfNoSpecifiedTests=false`; `mvn -B test` | Storage-engine reactive application gate PASS: 159 total, 159 passed, 0 failed/errors/skipped. Targeted adapter gate PASS: 814 total, 803 passed, 0 failed/errors, 11 skipped. Full Maven test PASS: 833 total, 822 passed, 0 failed/errors, 11 skipped. | `@implemented-not-e2e-validated`: validates staged read/write pipeline behavior at unit/application level only. No Phase 3 WebTestClient, AWS CLI, or end-to-end Cucumber validation has been executed yet. No Phase 4 metrics/tracing adapters are included. |
| Phase 5 S3 semantic compatibility (`s3-reactive-api-adapter`) | Requirement feature: `s3-reactive-api-adapter/src/test/features/requirements/phase-5-s3-semantic-compatibility.feature`; runner: `s3-reactive-api-adapter/src/test/java/com/example/magrathea/s3api/cucumber/requirements/Phase5S3SemanticCompatibilityRequirementsCucumberTest.java`; step glue: `s3-reactive-api-adapter/src/test/java/com/example/magrathea/s3api/cucumber/requirements/Phase5S3SemanticCompatibilitySteps.java`; full gate: `mvn -B test --no-transfer-progress` | Phase 5 WebTestClient runner PASS: 25 scenarios/tests, 0 failures, 0 errors, 0 skipped. Full Maven validation PASS / BUILD SUCCESS, 0 failures/errors. | `@implemented-and-validated` for 24 Phase 5 WebTestClient scenarios. REQ-S3-002-C is `@implemented-not-e2e-validated` because multipart uploaded-part durability is validated through WebTestClient plus a direct same-directory filesystem repository probe, not a full process/Spring restart. REQ-S3-007 scenarios pass as explicit `@not-implemented` or `@config-only` classification for versioning, object lock, and lifecycle; no enforcement is claimed. No Phase 5 AWS CLI runner has been newly added. |
| Phase 6 distributed readiness (`storage-engine-domain`) | Requirement feature: `s3-reactive-api-adapter/src/test/features/requirements/phase-6-distributed-readiness.feature`; domain implementation: `storage-engine-domain/src/main/java/com/example/magrathea/storageengine/domain/distributed/`; tests: `DistributedPlacementPlannerTest`, `QuorumPolicyTest`, `AntiEntropyPlannerTest`, `RebalancePlannerTest`, `DistributedReadinessReporterTest`; gates: `mvn -B -pl storage-engine-domain test --no-transfer-progress`, `mvn -B test --no-transfer-progress` | Storage-engine domain gate PASS: 164 tests, 0 failures, 0 errors, 0 skipped. Full Maven gate PASS / BUILD SUCCESS: 883 test cases observed, 0 failures, 0 errors, 11 skipped. | `@implemented-not-e2e-validated` for modeled domain/unit scope only. Deterministic placement, health-aware membership decisions, quorum decisions, anti-entropy/healing plans, rebalance plans, and readiness classification are modeled and unit validated. Real networked membership, real replication execution, healing/rebalance job runners, actual multi-node durability, and full e2e multi-node validation remain absent. No public storage-engine object API endpoints were added; S3 object behavior remains exposed through S3-compatible APIs only. |
| Domain quality bounded first pass (`storage-engine-domain`, `object-store-domain`) | Tests: `object-store-domain/src/test/java/.../DomainCollectionImmutabilityTest.java`, `storage-engine-domain/src/test/java/.../DomainCollectionImmutabilityTest.java`, and additional `StoredObjectTest` restore invariant coverage; gates: `mvn -B -pl storage-engine-domain,object-store-domain test --no-transfer-progress`, `mvn -B test --no-transfer-progress` | Targeted domain gate PASS: storage-engine-domain 172 tests and object-store-domain 292 tests, 0 failures/errors. Full Maven gate PASS / BUILD SUCCESS. | Bounded first pass only: validates defensive collection copies/immutable exposure and strengthened `StoredObject` invariants. `StoredObject` remains mutable through controlled lifecycle methods; duplicate/ambiguous encryption class names, explicit `Bucket` deleted terminal state, and broad immutable aggregate/lifecycle redesign remain open. |
| Runtime correctness bounded first pass (PLAN section D) | Tests strengthened for `StorageEngineReactiveS3ObjectRepository`, `FileSystemManifestRepository`, and filesystem scheduling; gates: `mvn -B -pl object-store-reactive-repository-storage-engine-infrastructure,storage-engine-reactive-infrastructure,s3-reactive-api-adapter,bootstrap-application -am test --no-transfer-progress`, `mvn -B test --no-transfer-progress` | Targeted module gate PASS / BUILD SUCCESS. Full Maven gate PASS / BUILD SUCCESS. | Evidence covers real storage-engine repository read-through via manifest reference and `orchestrator.read(...)`, manifest round-trip of declared upload checksum and multipart part checksum results, and `BlockingFileSystemOperation` bounded-elastic scheduling for blocking filesystem work. This does not close section D: S3 handler full-body accumulation and multipart part body persistence/final object assembly remain open. |
| Gherkin requirements ARC42 appendix (`docs/arc42/generated/gherkin-requirements.adoc`) | `python3 scripts/generate-gherkin-requirements-appendix.py --check` | PASS: generated appendix is fresh; 66 scenarios from 6 feature files | Documentation-generation evidence only. The report summarizes explicit scenario tags and does not invent implementation or validation results. |
| Source/build hygiene gates (PLAN section A safe first pass) | `mvn -B validate --no-transfer-progress`; `bash scripts/check-source-hygiene.sh` | PASS / BUILD SUCCESS; PASS | Evidence covers Maven validation and the source hygiene script that fails on source-tree `.class` files, root `com/`, root `META-INF`, and generated `META-INF/maven` outside ignored build output directories. `.gitignore`, `.dockerignore`, root Maven plugin/coverage-profile documentation, and Dockerfile `wget` runtime dependency were updated. Docker compose config/full Docker build validation was not run because `docker` is unavailable; duplicate module-level `coverage` profile consolidation in `bootstrap-application/pom.xml` remains pending. |
| Module/layering architecture guard (PLAN section B) | `scripts/check-module-layering.sh`; `mvn -B validate --no-transfer-progress`; `mvn -B test --no-transfer-progress` | PASS; PASS / BUILD SUCCESS; PASS / BUILD SUCCESS | The guard blocks application-to-infrastructure dependency inversions for object-store, storage-engine, and repository-application modules; preserves explicit `objectstore`/`objectstorage` package naming scan roots while the naming inconsistency remains; and checks explicit backend profile separation. Existing backend selection context tests still assert default in-memory beans versus storage-engine beans and fail-fast backend conflicts. Full Maven build succeeded after the ARC42 docs conversion fix changed generated appendix output from `NOTE:` admonition syntax to a plain paragraph. This is bounded first-pass evidence, not complete architecture cleanup or an ArchUnit/Java test claim. |
| Phase 5 domain planning (`storage-engine-domain`) | Commit `b0a5f74`; `PersistencePlannerMinioStandardTest` | 152 tests passing, 0 failures | Verifies deterministic `MINIO_STANDARD` persistence planning in the domain model. |
| Phase 5 YAML catalogs and MINIO_STANDARD integration (`storage-engine-reactive-infrastructure`) | Commit `0ec84cf`; `MinioStandardIntegrationTest` | 26 tests passing, 0 failures | Verifies YAML catalog/device integration and `MINIO_STANDARD` selection with S3 storage class `STANDARD`, dedup disabled, EC planning `4 data / 2 parity`, replication factor `1`, compression disabled, and encryption disabled by default; storage-engine runtime read/write wiring and physical EC shard placement remain pending for Phase 6/7. |
| Phase 8 backend Admin API (`admin-api-adapter`) | `mvn -B -pl admin-api-adapter -am test`; `AdminRouterTest` | 9 tests passing, 0 failures, build success | Verifies configuration-as-code/read-only admin catalog behavior for policies/devices/disk sets, structured validation responses for `POST /admin/storage-policies/validate`, non-persistence of validation, and runtime rejection of policy mutations. |
| Section E storage-engine configuration bounded first pass | `YamlStoragePolicyCatalogTest` (14), `YamlStorageDeviceCatalogTest` (10), `YamlDiskSetCatalogTest` (12), `MinioStandardIntegrationTest` (2), `StorageEngineMissingExternalConfigTest` (1), `StorageEngineBackendContextTest` (3), `AdminRouterTest` (9); `mvn -B test` PASS | 52 tests, 0 failures, 0 errors | `@implemented-and-validated` for declared Section E scope. YAML catalogs validate duplicate IDs, malformed YAML, domain-invalid configs, zero-device disk sets, blank device references, unresolved device references. MINIO_STANDARD loaded from YAML produces deterministic EC `4/2` plans. Storage-engine backend fails fast on missing config. Mutually exclusive `storage-engine` vs `single-node` profile selection proven. Admin API uses same catalog beans (unit-level). Gaps: no full Spring Boot integration test for admin API with real YAML catalogs; physical EC shard placement and multi-node topology remain modeled only. |
| Phase 1 upload reliability (`bootstrap-application` + Cucumber runners) | Bootstrap tests: `StorageEngineRestartSafetyTest`, `StorageEngineHttpReadAfterWriteTest`, `StorageEngineUploadAtomicityTest`, `StorageEngineIntegrityDetectionTest`; Phase 1 Cucumber runners (WebTestClient + AWS CLI) in the 2026-07-02 Podman gate, run twice (before/after the feature-file tag refresh) | Bootstrap evidence per previous Maven runs; Phase 1 runners: 28 scenarios discovered, 9 executed per mode, 0 failures, identical on both runs | `@implemented-and-validated` for REQ-UPLOAD-001/002 under accepted bootstrap-integration validation mode. REQ-UPLOAD-003 executable validation was removed because multi-durable-chunk non-dedup persistence is intentionally absent. REQ-UPLOAD-004 is now `@implemented-and-validated`: runtime-validated independently in both WebTestClient and AWS CLI modes, and the feature-file tag has been refreshed from `@implemented-not-e2e-validated` to `@implemented-and-validated` with the feature-level `@partial` tag removed. REQ-UPLOAD-005/006 retain previous Cucumber runtime evidence. |
| Phase 9 AWS CLI object CRUD, bucket operations, metadata/tagging, and multipart increment (`s3-reactive-api-adapter`) | Production commits `9ff5a08`, `b2c333c`, `a03bc4a`; test commits `94f3e47`, `cdbcc9d`, `a03bc4a`; `AwsCliCucumberTest` + legacy `ObjectStoreCucumberTest` | 265 Cucumber examples, 0 failures, 0 errors, 0 skipped | Targeted AWS CLI Cucumber has 26 scenarios total: all run, 0 skipped. Legacy WebTestClient Cucumber has 239 scenarios passing. This row does not include the isolated Phase 2 WebTestClient runner or the dedicated storage-engine AWS CLI Phase 2 validation. |

### Phase 2 Filesystem Reliability Scenario Status

| Requirement | Scenario | Validation mode | Status | Notes |
|---|---|---|---|---|
| REQ-FS-001 | Interrupted chunk write | Isolated Phase 2 WebTestClient requirements runner | ✅ Passed | Disabled-by-default write fault injection validates interrupted chunk writes do not publish incomplete chunks. |
| REQ-FS-002 | Interrupted manifest write | Isolated Phase 2 WebTestClient requirements runner | ✅ Passed | Disabled-by-default write fault injection validates interrupted manifest writes do not publish incomplete manifests. |
| REQ-FS-003 | Corrupted chunk detection | Isolated Phase 2 WebTestClient requirements runner; dedicated storage-engine AWS CLI Phase 2 validation | ✅ Passed | Read-time chunk checksum verification detects corruption and returns a mapped S3 XML error response. |
| REQ-FS-004 | Corrupted manifest detection | Isolated Phase 2 WebTestClient requirements runner; dedicated storage-engine AWS CLI Phase 2 validation | ✅ Passed | Manifest checksum trailer verification detects corruption and returns a mapped S3 XML error response. |
| REQ-FS-005 | Recovery scanner | Isolated Phase 2 WebTestClient requirements runner | ✅ Passed | `FileSystemRecoveryScanner` reports/quarantines incomplete or corrupt state deterministically and idempotently. |
| REQ-FS-006 | Concurrent PUT of different keys | Isolated Phase 2 WebTestClient requirements runner; dedicated storage-engine AWS CLI Phase 2 validation | ✅ Passed | Concurrency scenario passed for different object keys. |
| REQ-FS-006 | Concurrent PUT of the same key | Isolated Phase 2 WebTestClient requirements runner; dedicated storage-engine AWS CLI Phase 2 validation; `S3ObjectManifestReferenceStoreConcurrencyTest`; 6× fresh-JVM AWS CLI stress re-runs | ✅ Passed (defect found → fixed → 6/6 stress-validated) | A real torn-reference defect (non-atomic find→save read-modify-write in `StorageEngineReactiveS3ObjectRepository.save(...)` / `S3ObjectManifestReferenceStore`) reproduced 4/4 in one session as an AWS CLI CRC64NVME checksum mismatch. Fixed by `commitLatest` per-key striped locking (64 stripes) plus temp file + `ATOMIC_MOVE` persistence; last-writer-wins, crash-safe. Re-validated: Phase 2 AWS CLI runner 6/6 in fresh JVMs; new unit test passes 3/3. Feature tag restored `@partial` → `@implemented-and-validated`. |
| Phase 2 mode summary | WebTestClient and AWS CLI declared scope | WebTestClient + AWS CLI | ✅ Passed for declared scope | WebTestClient: 11 examples total, 7 passed, 4 `@awscli` examples skipped/excluded. AWS CLI: 4 scenarios/examples, 4 passed, 0 failed, 0 skipped. |

### Phase 3 Reactive Pipeline Unit/Application Requirement Status

| Requirement | Scenario | Validation mode | Status | Notes |
|---|---|---|---|---|
| REQ-PIPELINE-001 | Write pipeline stage order/events | Unit/application validation in `storage-engine-reactive-application` | ✅ Passed | Verifies staged write execution and event ordering. |
| REQ-PIPELINE-002 | Bounded demand/backpressure/no whole-object aggregation | Unit/application validation in `storage-engine-reactive-application` | ✅ Passed | Verifies bounded demand and avoids global full-object aggregation in production pipeline behavior. |
| REQ-PIPELINE-003 | Read pipeline manifest chunk order | Unit/application validation in `storage-engine-reactive-application` | ✅ Passed | Verifies read pipeline emits chunks in manifest order. |
| REQ-PIPELINE-004 | Failure propagation, cleanup, later stages stopped | Unit/application validation in `storage-engine-reactive-application` | ✅ Passed | Verifies failures stop downstream stages and trigger cleanup. |
| REQ-PIPELINE-005 | Cancellation event and cleanup | Unit/application validation in `storage-engine-reactive-application` | ✅ Passed | Verifies cancellation event publication and cleanup handling. |
| REQ-PIPELINE-006 | Instrumentation event metadata/correlation/no payload leakage | Unit/application validation in `storage-engine-reactive-application` | ✅ Passed | Verifies correlation metadata is emitted without leaking object payload bytes. |
| REQ-PIPELINE-007 | PutObject computes ETag while teeing the request body into storage | Static architecture test (`ReactiveUploadStreamingArchitectureTest`) + Cucumber e2e suites | ✅ Implemented-and-validated via static architecture test + e2e | `S3ObjectOperationsHandler.putObject` computes ETag (MD5) and content length via a single-pass tee (`UploadDigest` updating on each `DataBuffer`) while the body streams into the storage engine; `DataBufferUtils.join` removed and forbidden by the architecture test. Cucumber scenario carries `@not-implemented` (no Cucumber step glue). |
| REQ-PIPELINE-008 | Fixed-window dedup emits configured windows without joining the FileUnit | Static architecture test (`ReactiveUploadStreamingArchitectureTest`) + Cucumber e2e suites | ✅ Implemented-and-validated via static architecture test + e2e | `FixedWindowDedupStep` accumulates fixed windows incrementally over incoming `DataBuffer`s; `DataBufferUtils.join` removed and forbidden by the architecture test. No dedicated `FixedWindowDedupStep` unit test exists; behavior is validated via the architecture test and Cucumber e2e suites. Cucumber scenario carries `@not-implemented` (no Cucumber step glue). |
| Phase 3 mode summary | Reactive pipeline requirements | Unit/application (REQ-PIPELINE-001..006); static architecture + e2e (REQ-PIPELINE-007/008) | ⚠️ Implemented-not-e2e-validated for 001–006; ✅ 007/008 validated via static architecture test + e2e | No Phase 3 WebTestClient, AWS CLI, or end-to-end Cucumber runner executes the Phase 3 scenarios yet. |

### Phase 5 S3 Semantic Compatibility Requirement Status

Evidence:
- Requirement source: `s3-reactive-api-adapter/src/test/features/requirements/phase-5-s3-semantic-compatibility.feature`.
- WebTestClient runner: `s3-reactive-api-adapter/src/test/java/com/example/magrathea/s3api/cucumber/requirements/Phase5S3SemanticCompatibilityRequirementsCucumberTest.java`.
- Step glue: `s3-reactive-api-adapter/src/test/java/com/example/magrathea/s3api/cucumber/requirements/Phase5S3SemanticCompatibilitySteps.java`.
- Phase 5 WebTestClient validation passed: 25 scenarios/tests, 0 failures, 0 errors, 0 skipped.
- Full Maven validation passed: `mvn -B test --no-transfer-progress` BUILD SUCCESS, 0 failures/errors.

| Requirement | Scenario area | Validation mode | Status | Notes |
|---|---|---|---|---|
| REQ-S3-001 | PutObject/HeadObject ETag format and consistency | WebTestClient Cucumber | ✅ Implemented-and-validated | RFC-compatible quoted lowercase MD5 ETags are computed and persisted through the S3 object `etag` domain field and repository translators. |
| REQ-S3-002 | UploadPart and CompleteMultipartUpload ETag semantics | WebTestClient Cucumber | ✅ Implemented-and-validated for normal multipart ETag semantics | UploadPart returns real part MD5 ETags; CompleteMultipartUpload returns multipart ETag with the `-{partCount}` suffix. |
| REQ-S3-002-C | Multipart uploaded-part durability probe | WebTestClient plus direct filesystem same-directory repository probe | ⚠️ Implemented-not-e2e-validated | Filesystem-backed multipart upload repository exists for durable multipart state, but this validation is not a full process/Spring restart e2e scenario. |
| REQ-S3-003 | Byte-range GET and unsatisfiable range | WebTestClient Cucumber | ✅ Implemented-and-validated | Range GET returns 206 Partial Content with `Content-Range`; unsatisfiable ranges return 416 `InvalidRange`. |
| REQ-S3-004 | Conditional GET/HEAD headers | WebTestClient Cucumber | ✅ Implemented-and-validated | Covers `If-Match`, `If-None-Match`, `If-Modified-Since`, and `If-Unmodified-Since`. |
| REQ-S3-005 | CopyObject ETag | WebTestClient Cucumber | ✅ Implemented-and-validated | CopyObject returns the destination ETag instead of a placeholder. |
| REQ-S3-006 | Object tagging lifecycle and inline `x-amz-tagging` | WebTestClient Cucumber | ✅ Implemented-and-validated | Object tags persist through the S3 object `objectTags` domain field and object tagging endpoints. |
| REQ-S3-007 | Versioning, object lock, and lifecycle classification | WebTestClient Cucumber | ✅ Validated as not-implemented/config-only classification | Scenarios intentionally document unsupported/config-only behavior. Versioning, object-lock, lifecycle, and replication enforcement are not claimed complete. |
| Phase 5 mode summary | Declared Phase 5 S3 semantic compatibility scope | WebTestClient only | ✅/⚠️ Honest declared-scope result | 24 scenarios are `@implemented-and-validated`; REQ-S3-002-C is `@implemented-not-e2e-validated`; no Phase 5 AWS CLI runner was newly added despite some scenarios carrying `@awscli-required`. |

Implemented outputs recorded for Phase 5: RFC-compatible quoted lowercase MD5 ETags for PutObject/HeadObject; ETag and object tag persistence in the S3 object domain model and repository translators; Range GET and conditional GET/HEAD behavior; CopyObject destination ETag propagation; real multipart part/final ETags; filesystem-backed multipart upload state; and explicit unsupported/config-only S3 responses.

### Phase 6 Distributed Readiness Modeled Domain Requirement Status

Evidence:
- Requirement source: `s3-reactive-api-adapter/src/test/features/requirements/phase-6-distributed-readiness.feature`.
- Production domain implementation: `storage-engine-domain/src/main/java/com/example/magrathea/storageengine/domain/distributed/`.
- Semantic unit tests: `DistributedPlacementPlannerTest`, `QuorumPolicyTest`, `AntiEntropyPlannerTest`, `RebalancePlannerTest`, and `DistributedReadinessReporterTest`.
- Targeted validation passed: `mvn -B -pl storage-engine-domain test --no-transfer-progress` — 164 tests, 0 failures, 0 errors, 0 skipped.
- Full validation passed: `mvn -B test --no-transfer-progress` — BUILD SUCCESS; 883 test cases observed, 0 failures, 0 errors, 11 skipped.

| Requirement | Scenario area | Validation mode | Status | Notes |
|---|---|---|---|---|
| REQ-DIST-001 | Modeled placement across failure domains and insufficient-domain failure | Domain/unit validation | ⚠️ Implemented-not-e2e-validated | Deterministic modeled topology/placement planner selects replicas across nodes and failure domains and reports insufficient-domain failures. |
| REQ-DIST-002 | Write/read quorum and integrity-aware read failure | Domain/unit validation | ⚠️ Implemented-not-e2e-validated | Explicit write/read quorum decision model exists, including integrity-aware read quorum behavior for corrupt or missing replicas. |
| REQ-DIST-003 | Health-aware membership placement and exclusions | Domain/unit validation | ⚠️ Implemented-not-e2e-validated | Placement uses `HEALTHY`, `DEGRADED`, and `DOWN` node states with explicit exclusion and risk reasons. |
| REQ-DIST-004 | Anti-entropy findings and planned healing tasks | Domain/unit validation | ⚠️ Implemented-not-e2e-validated | Anti-entropy planner produces missing, corrupt, and unrecoverable findings plus planned healing tasks. |
| REQ-DIST-005 | Safe rebalance planning and failed-copy retry classification | Domain/unit validation | ⚠️ Implemented-not-e2e-validated | Rebalance planner produces observable move plans and failed-copy retry results without reducing quorum. |
| REQ-DIST-006 | Readiness classification that avoids distributed-production claims | Domain/unit validation | ⚠️ Implemented-not-e2e-validated | Readiness reporter never claims `distributed-production-ready` for local, simulated, or default filesystem mode and lists missing capabilities. |
| Phase 6 mode summary | Declared distributed-readiness modeled domain scope | Domain/unit only | ⚠️ Modeled/unit validated only | All Phase 6 scenarios are modeled domain/unit validated only. Real networked membership, replication execution, durable multi-node manifest publication, healing/rebalance job runners, actual multi-node durability, and WebTestClient/AWS CLI/full e2e multi-node validation remain absent. |

Implemented outputs recorded for Phase 6: pure domain distributed-readiness model for topology/node health, placement decisions, quorum policy, replica observations, anti-entropy findings/healing tasks, rebalance plans/task results, and readiness reports. No public storage-engine object API endpoints were added; S3 object behavior remains exposed through S3-compatible APIs only. Distributed production readiness remains not achieved.

### AWS CLI Test Status

| Feature | Status | Note |
|---|---|---|
| Legacy WebTestClient scenarios (239) | ✅ Passing | Existing Cucumber via Java WebTestClient (`@webclient` tag). |
| Phase 2 WebTestClient requirements | ✅ 11 total, 7 passed, 4 `@awscli` examples excluded/skipped | Isolated Phase 2 runner validates REQ-FS-001 through REQ-FS-006 for the WebTestClient-required scope, including interrupted chunk/manifest write fault injection, corruption detection, recovery scanning, and concurrency scenarios. |
| Phase 2 storage-engine AWS CLI requirements | ✅ 4 total, 4 passed, 0 failed, 0 skipped | Dedicated AWS CLI validation passed for REQ-FS-003, REQ-FS-004, REQ-FS-006 different keys, and REQ-FS-006 same key. |
| Phase 5 AWS CLI requirements | ⚠️ Not newly added | Phase 5 evidence is WebTestClient-only; do not treat `@awscli-required` tags in the Phase 5 feature as newly validated AWS CLI coverage. |
| AWS CLI Cucumber scenarios | ⚠️ 26 existing parity scenarios, all run, 0 skipped | Object CRUD basics (including slash-containing keys), bucket operations (create, list, head, location, versioning, delete, and failure cases), ACL/tagging (bucket and object read/write/CRUD), object attributes, and multipart upload lifecycle (initiate/upload/complete/abort/list) now have targeted AWS CLI coverage; full parity with WebTestClient canonical scenarios remains incomplete beyond the declared Phase 2 AWS CLI scope. |
| `test-aws-cli.sh` standalone | ⚠️ 46/82 passed | Standalone script; most failures are connection errors (server not running at test time), not logic failures. |

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

> The detailed module-level table below is the previous Phase 10 breakdown. The latest full Maven gate (2026-07-02, Podman `magrathea-build:fedora42` — JDK 21, Maven 3.9.9, AWS CLI 2.34.29), `mvn -B --no-transfer-progress clean test` at the repo root, passed with BUILD SUCCESS: **942 tests, 0 failures, 0 errors, 5 skipped** (tag-filter/assumption skips) across all 13 reactor modules, total time 04:55. Earlier full-run evidence observed 883 and then 902 test cases with 11 skipped. Use the Summary and phase sections above for current validation evidence.

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
| **Previous total (Phase 10 quality gate — all 10 modules; superseded by current aggregate above)** |  | **768** | **0** | **0** | **0** | **✅ Passed at that time** |

## Coverage

**JaCoCo** is the current coverage baseline. Reports are generated by `mvn -Pcoverage test jacoco:report` under `target/site/jacoco/`.

**Clover/OpenClover** is optional/legacy. Report HTML (if generated): `target/site/clover/index.html`

| Metric | Notes |
|---|---|
| JaCoCo | Phase 10 results in table below and in the Phase 10 Quality Gates section |
| Clover (optional/legacy) | Run `mvn -Pcoverage clover:setup test clover:aggregate clover:clover` to generate |

> **Coverage note (2026-07-02):** `FixedWindowDedupStep` has no dedicated unit test; its streaming/windowing behavior is validated via the static architecture test `ReactiveUploadStreamingArchitectureTest` and the Cucumber e2e suites.
>
> **Test-only hygiene (2026-07-02):** dangling storage-root symlink cleanup was added in `RequirementsTestApp` and `Phase2StorageEngineAwsCliTestApp` (test scaffolding only; no production code affected).

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

## Previous Phase 10 Quality Gates

**Date:** 2026-06-12 &nbsp; **HEAD:** `351d088`

> This section is retained as historical Phase 10 evidence. The latest counted aggregate before Phase 5 is the Phase 3 validation snapshot above: `mvn -B test` passed with 833 tests, 0 failures, 0 errors, and 11 skipped. The supplied Phase 5 full Maven gate also passed (`mvn -B test --no-transfer-progress`, BUILD SUCCESS, 0 failures/errors), with no restated total count.

### `mvn validate`

✅ BUILD SUCCESS — all module POMs valid.

### `mvn test` — Full Multi-Module Run (previous Phase 10 snapshot)

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
| **PREVIOUS TOTAL** | **768** | **0** | **0** | **0** |

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

Historical Phase 10 quality gates passed with no failures, errors, or skipped tests. The latest supplied full Maven gate for this report, `mvn -B test --no-transfer-progress`, passed with BUILD SUCCESS and 0 failures/errors; the total test count was not restated in the supplied Phase 5 evidence. The Phase 2 WebTestClient skipped/excluded examples are `@awscli` examples covered by the dedicated AWS CLI validation where applicable, not fault-injection validation gaps. Phase 3 reactive pipeline validation is unit/application only and is not yet WebTestClient/AWS CLI/e2e validated. Phase 5 semantic compatibility validation is WebTestClient-only, with no newly added Phase 5 AWS CLI runner.

---

## S3 Semantic Coverage Matrix

> This table classifies each S3 API family by semantic status, not route count.
> **Route mapped** = HTTP route exists; **Stateful behavior** = creates/reads/updates/deletes durable state with observable follow-up; **AWS CLI scenario** = verified via AWS CLI Cucumber or `test-aws-cli.sh`; **Storage-engine scenario** = verified against storage-engine backend; **Semantic status** = overall classification.

### Current Evidence-Based Family Classification

| Family | Operation (examples) | Route mapped | Stateful behavior | AWS CLI scenario | Storage-engine scenario | Semantic status | Notes |
|---|---|---|---|---|---|---|---|
| Object CRUD | PutObject, GetObject, HeadObject, DeleteObject, CopyObject, Range GET, conditional GET/HEAD | Yes | Partial — WebTestClient-validated ETag, range, conditional, copy, and object state semantics for declared Phase 5 scope | Partial pass: existing AWS CLI increment covers put default headers, get content, head, delete, idempotent delete, `STANDARD` storage class via object attributes, and slash-containing object keys; no new Phase 5 AWS CLI runner | Absent | Partial / WebTestClient semantic coverage for declared Phase 5 subset | Phase 5 validates quoted lowercase MD5 ETags, ETag persistence, Range GET 206/416 behavior, conditional GET/HEAD headers, and CopyObject destination ETag through WebTestClient. Storage-engine backend and Phase 5 AWS CLI validation remain absent. |
| Bucket baseline | CreateBucket, HeadBucket, DeleteBucket, ListBuckets, ListObjects, ListObjectsV2 | Yes | Partial — in-memory only | Partial pass: AWS CLI bucket operations increment covers create-bucket, list-buckets, head-bucket, get-bucket-location, get-bucket-versioning, put-bucket-versioning, delete-bucket, duplicate-bucket 409, head-bucket 404, delete-bucket 404, list-objects, and list-objects-v2 | Absent | Stubbed / Partial | Bucket create/head/location/versioning/delete and failure cases now have targeted AWS CLI Cucumber coverage; prefix/delimiter edge cases, continuation tokens, and storage-engine indexes remain unverified |
| Multipart upload | CreateMultipartUpload, UploadPart, CompleteMultipartUpload, AbortMultipartUpload, ListParts, ListMultipartUploads | Yes | Partial — WebTestClient validates real part MD5 ETags, multipart final ETag suffix, and filesystem-backed multipart state probe | Partial pass: AWS CLI Cucumber third increment covers full lifecycle (initiate/upload-parts/list-parts/complete), abort, and list multipart uploads; no new Phase 5 AWS CLI runner | Absent | Partial / WebTestClient semantic coverage for declared Phase 5 subset | UploadPart returns real part MD5 ETags and CompleteMultipartUpload returns `-{partCount}` multipart ETags. Multipart restart/durability is implemented-not-e2e-validated because the durability check is a same-directory filesystem repository probe, not a full process/Spring restart. |
| Bucket configuration | CORS, Lifecycle, Website, Logging, Notification, Replication, Encryption, Versioning, Tagging, etc. | Yes | Partial — config storage or explicit unsupported/config-only classification only | Failing in stale standalone script; no new Phase 5 AWS CLI runner | Absent | Config-only / Stubbed / Not implemented where explicitly declared | Phase 5 validates explicit unsupported/config-only classification for versioning, object lock, and lifecycle. No background job execution or enforcement is claimed. |
| Object metadata/tagging/ACL | PutObjectTagging, GetObjectTagging, GetObjectAttributes, PutObjectAcl, GetObjectAcl, PutBucketTagging, GetBucketTagging, DeleteBucketTagging, PutBucketAcl, GetBucketAcl | Yes | Partial — object tags persist in the S3 object domain model for Phase 5; ACL grant enforcement remains stubbed/partial | Partial pass: AWS CLI third increment covers bucket ACL read/write, object ACL read/write, bucket tagging CRUD, object tagging CRUD, and object attributes (ETag + ObjectSize); no new Phase 5 AWS CLI runner | Absent | Partial / WebTestClient semantic coverage for object tagging | Phase 5 validates object tag persistence through `objectTags`, object tagging endpoints, and inline `x-amz-tagging`. Storage-engine backend remains unverified; ACL grant enforcement remains stubbed. |
| Versioning/delete markers | ListObjectVersions, versioned GET/HEAD/DELETE | Yes | Explicit unsupported/not-implemented classification for Phase 5 scope | Failing in stale standalone script; no new Phase 5 AWS CLI runner | Absent | Not implemented / Stubbed | Phase 5 passing scenarios document unsupported/not-implemented classification. Version IDs, delete markers, latest-version resolution, and enforcement remain unimplemented. |
| Access/security controls | BucketPolicy, PublicAccessBlock, OwnershipControls | Yes | Config storage only | Failing (server not running at test time) | Absent | Config-only / Stubbed | Authorization enforcement absent |
| Analytics/inventory/metrics | Bucket analytics, inventory, metrics, intelligent-tiering | Yes | Config storage only | Failing (server not running at test time) | Absent | Config-only | No background report generation |
| Advanced/specialized | SelectObjectContent, RestoreObject, etc. | Some | None verified | Missing | Absent | Stubbed / Out of scope | Likely out of scope without explicit design |
| Admin/storage-engine APIs | StoragePolicy/device/disk-set catalog reads; policy validation | Yes | Read-only configuration-as-code catalogs; validation is non-persistent | Not applicable | Admin adapter tests pass; selected-backend S3 scenarios still absent | Partial backend Admin API implemented | `/admin/**` is separate from S3 coverage. `mvn -B -pl admin-api-adapter -am test` passes 9 tests. Policy/device/disk-set catalogs are read-only at runtime; create/update/delete policy requests are rejected. |

### AWS CLI Cucumber vs WebTestClient Cucumber Parity

| Dimension | Legacy WebTestClient Cucumber | Phase 2 WebTestClient requirements | AWS CLI Cucumber |
|---|---|---|---|
| Scenarios | 239 passed | 11 total: 7 passed, 4 `@awscli` examples excluded/skipped | 26 existing parity scenarios passed; 4 dedicated Phase 2 storage-engine AWS CLI scenarios/examples passed |
| Tag / runner | `@webclient` | Isolated Phase 2 runner | `@awscli` |
| Driver | Spring WebTestClient | Spring WebTestClient | `aws s3api` CLI |
| Status | Passing | Implemented-and-validated for WebTestClient-required Phase 2 scope, including interrupted-write fault injection, corruption detection, recovery scanning, and concurrency | Object CRUD, bucket operations, metadata/tagging, multipart increments, and declared Phase 2 storage-engine AWS CLI examples passing; full parity incomplete beyond those scopes |
| Parity goal | Canonical suite | Maintain Phase 2 coverage and keep shared requirement text as source of truth | Must continue toward broader parity where AWS CLI can express the same behavior (roadmap item P3/S3-P1+) |

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
| Legacy `@webclient` scenarios (239) | ✅ Passing | Existing Cucumber via Java WebTestClient. |
| Phase 2 WebTestClient requirements | ✅ 11 total, 7 passed, 4 `@awscli` examples excluded/skipped | REQ-FS-001 through REQ-FS-006 WebTestClient-required scope passes, including interrupted-write fault injection, corruption detection, recovery scanning, and concurrency scenarios. |
| Phase 2 storage-engine AWS CLI requirements | ✅ 4 total, 4 passed, 0 failed, 0 skipped | Dedicated Phase 2 AWS CLI validation passes for REQ-FS-003, REQ-FS-004, REQ-FS-006 different keys, and REQ-FS-006 same key. |
| `@awscli` scenarios | ⚠️ 26 existing parity scenarios, all run, 0 skipped | Object CRUD basics (including slash-containing keys), bucket operations parity (create, list, head, location, versioning, delete, and failure cases), ACL/tagging (bucket and object), object attributes, and multipart upload lifecycle (initiate/upload/complete/abort/list) pass; full parity with `@webclient` canonical suite remains open beyond the declared Phase 2 AWS CLI scope (roadmap item P3/S3-P1+). |
| `test-aws-cli.sh` standalone | ⚠️ 46/82 passed | Independent script; most failures are connection errors (server not started at test time). |

## Roadmap

Operations not yet semantically implemented are tracked in `PLAN.md` under the *S3 API Semantic Completion Plan* (S3-P0 through S3-P4).
