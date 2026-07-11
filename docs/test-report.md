# Magrathea ObjectStorage Test Report

Updated: 2026-07-11

> ⚠️ **Report status: EVIDENCE-ONLY / NOT A COMPLETION CLAIM**
>
> This report reflects point-in-time validation evidence and **must not be read as implementation completeness**.
> - The `111/111` route count (referenced elsewhere) is a mapped-surface inventory only, not semantic completion.
> - AWS CLI standalone results (46 passed / 82 failed) are from a run where the server was not started; most failures are connection errors (exit code 254), not logic failures.
> - **Latest full Maven gate (2026-07-11, local sandbox — Java 25.0.3):** `mvn -B --no-transfer-progress test` at the repository root passed with BUILD SUCCESS across all 13 reactor modules. Fresh Surefire XML contains **104 reports, 1,325 discovered report entries, 1,053 executed tests/examples, 272 skipped or tag-filtered entries, 0 failures, and 0 errors**. Execution counts are distinct from the generated Gherkin inventory and shared features may be rediscovered by multiple filtered runners.
> - **Root JVM Docker image gate (2026-07-10):** `Dockerfile` now uses public ECR mirrored Maven/Temurin base images, copies the full `scripts/` gate set, runs the Gherkin appendix freshness gate before packaging, regenerates docs/UI assets, and packages without Maven fail-never mode. Packaged single-node containers activate the `storage-engine` profile, declare `magrathea.object-store.backend=storage-engine`, copy YAML catalogs under `/app/config`, and persist under `/app/data/storage-engine`. `docker build --network=host -f Dockerfile -t magrathea-objectstorage:jvm .` passed. Container smoke validation with host networking passed for `/admin/health`, `/admin/live`, `/admin/ready` returning ready catalog status, S3 ListBuckets XML, bucket `PUT`, object `PUT`, and object `GET`; the runtime log confirmed `Selected object-store backend: storage-engine`; build/runtime logs contained 0 Spring Boot generated-password banners.
> - **Native image packaging slice (2026-07-10):** the local native toolchain was aligned to Oracle GraalVM 25 because Spring Boot 4 rejects Java 21 native images at startup. `mvn -Pnative -pl bootstrap-application -am -DskipTests native:compile` passed and produced `bootstrap-application/target/magrathea-objectstorage`; a native smoke run returned healthy JSON from `/admin/health` and did not emit Spring Boot's generated-password banner. `Dockerfile.native` and the maintainer-facing `specs/phase-ka5-distribution.feature` were added for a GraalVM 25 native-image builder and JVM-free Alpine runtime. `docker build --network=host -f Dockerfile.native -t magrathea-objectstorage:native .` passed, including Gherkin appendix freshness, docs/UI regeneration, musl/static native compilation, and final Alpine image creation. Container smoke validation passed with host networking in the local Docker sandbox: `/admin/health` returned healthy JSON, S3 ListBuckets XML/JSON plus `PUT` bucket / `PUT` object / `GET` object returned the expected object payload, no generated-password banner appeared, no native reflection or shared-arena runtime errors appeared in the container log, `java`/`javac` were absent in the runtime image, binary size was 139.3 MiB, and image size was 63,378,011 bytes.
> - **Post-native follow-up gate (2026-07-10):** `mvn -pl bootstrap-application -am test -Dsurefire.failIfNoSpecifiedTests=false` passed with BUILD SUCCESS after fixing RestoreObject tier normalization (`Standard`/`Bulk` → enum values) and native ListBuckets reflection hints. The log was checked for Spring Boot's generated-password banner and none was found. Single-node WebTestClient restore scenarios that previously failed with 404 now pass.
> - **EP-3 static architecture and multipart assembly/copy/error/streaming slice (2026-07-10):** `mvn -pl s3-reactive-api-adapter -Dtest=Phase3ReactivePipelineStaticArchitectureSpecsCucumberTest test` passed with BUILD SUCCESS after adding `REQ-PIPELINE-011/012`. Cucumber validates `REQ-PIPELINE-007..012` as `@implemented-and-validated`: PutObject/static dedup constraints, non-range GetObject streaming, ranged GetObject per-buffer slicing, UploadPart/UploadPartCopy no-join streaming to part storage, and part-store DataBuffer streaming reads/writes. Multipart assembly `REQ-S3-002-D/F` is also `@implemented-and-validated`: `S3MultipartPartStore` persists uploaded and UploadPartCopy copied bytes and `CompleteMultipartUpload` assembles a readable ordered object. Multipart part-body restart completion `REQ-S3-002-E` is full-process validated by `Phase5MultipartFullRestartCucumberTest`. Multipart XML error semantics `REQ-S3-002-G..K` validate malformed copy-source, unknown upload ID, malformed complete XML, invalid part references, and abort/complete conflicts. Phase 5 WebTestClient and AWS CLI regressions passed. Generated-password banner count: 0 for all logs.
> - **EP-3 runtime backpressure boundary slice (2026-07-11):** `REQ-PIPELINE-013` adds runtime Cucumber evidence for a shared four-DataBuffer demand window on existing PutObject, GetObject/Range, UploadPart/UploadPartCopy, and multipart part-file read/write boundaries. The demand-controlled probe streams 12 ordered 64 KiB buffers, verifies each observed upstream request signal remains at or below four buffers, verifies byte order/range bytes, and exercises real filesystem part persistence/readback. Focused static/runtime, storage-engine reactive application, Phase 5 WebTestClient, and Phase 5 AWS CLI reports pass. This is finite-demand adapter-boundary evidence only; it does not independently claim complete staged StorageStage ordering, publication, lifecycle/cancellation, large-workload, or end-to-end validation.
> - **EP-3 REQ-PIPELINE-002 large pipeline-unit and WebTestClient slice (2026-07-11):** `Phase3PipelineUnitSpecsCucumberTest` passed with 21 discovered examples, 9 executed, 12 tag-filtered/skipped. Both modes generate a deterministic 256 MiB body on demand, run it through the production staged pipeline and real filesystem, measure live Netty source-payload buffers and upstream request signals against the four-buffer ceiling, verify manifest order/length and exact streamed SHA-256 readback, and guard production object-content stages against whole-object assembly. WebTestClient selects the dedicated `PIPELINE` test catalog policy with 1 MiB dedup windows through `x-amz-storage-class`. The adapter requests three upstream buffers per batch so transport plus active processing remains within the declared four-buffer ceiling. REQ-PIPELINE-002 is `@implemented-and-validated` for both declared modes.
> - **EP-3 REQ-PIPELINE-005 client cancellation slice (2026-07-11):** `Phase3ReactivePipelineCucumberTest` now passes with 21 discovered examples, 5 executed, 16 tag-filtered/skipped. The WebTestClient runner opens a temporary loopback Reactor Netty HTTP server over the production S3 router, starts a throttled 256 MiB `PIPELINE` PutObject, waits for two unpublished 1 MiB chunks, then disposes the live HTTP client subscription. The scenario observes upstream cancellation and stable post-cancellation demand, releases emitted and prefetched DataBuffers, receives one `STAGE_CANCELLED` plus `CLEANUP_COMPLETED`, removes chunks/temp files, commits no manifest/object/reference, and receives 404 on later GetObject. REQ-PIPELINE-005 is `@implemented-and-validated` in both declared modes.
> - **EP-3 REQ-PIPELINE-003 large-read evidence refresh (2026-07-11):** the pipeline-unit and WebTestClient examples now use the declared deterministic 256 MiB fixture rather than replacing it with a 1 MiB in-memory payload. Both uploads select the 1 MiB-window `PIPELINE` policy. Pipeline-unit preserves controlled first-chunk demand and computes length/SHA-256 incrementally; WebTestClient consumes response DataBuffers into an incremental digest without collecting the response. Focused runners pass. REQ-PIPELINE-003 remains `@partial`: deterministic HTTP integrity errors still require a complete non-retaining preflight followed by a separate response read, so first-byte latency and single-pass filesystem I/O are not claimed.
> - **Current Cucumber/requirements/specs counts from the latest Maven gates:** generated ARC42 appendix now covers **409 scenarios from 23 feature files**; single-node WebTestClient requirements runner **260 discovered / 234 passed / 26 skipped** from the previous broad gate; single-node AWS CLI requirements runner **72 discovered / 26 passed / 46 skipped** from the previous broad gate; specs runner **3 passed**; Phase 1 WebTestClient and AWS CLI runners **28 discovered / 9 passed / 19 skipped each**; Phase 2 WebTestClient spec runner **11 discovered / 7 passed / 4 skipped**; Phase 2 AWS CLI spec runner **11 discovered / 4 passed / 7 skipped**; Phase 5 WebTestClient **33 discovered / 32 passed / 1 skipped**; Phase 5 AWS CLI **33 discovered / 14 passed / 19 skipped**; Phase 5 multipart full-process restart runner **33 discovered / 1 passed / 32 skipped**; Phase 3 pipeline-unit runner **21 discovered / 9 passed / 12 skipped**; Phase 3 WebTestClient runner **21 discovered / 5 passed / 16 skipped**; Phase 3 static specs runner **21 discovered / 6 passed / 15 skipped**; Phase 3 runtime backpressure specs runner **21 discovered / 1 passed / 20 skipped**; EP-5 storage migration specs **6 passed**; EP-5 SLO/alerting bundle validation included in operability; EP-1 security/identity WebTestClient **13 passed**; EP-1 security/identity AWS CLI/e2e **13 passed**; EP-1 backing-service specs **4 passed**; EP-2 metadata durability WebTestClient restart-simulation **21 discovered / 3 passed / 18 skipped**; EP-2 metadata durability full Spring restart **21 discovered / 18 passed / 3 skipped**; EP-5 operability **14 passed**; opt-in EP-5 live alert delivery **1 passed / 14 tag-filtered**; S3 API semantic coverage report spec **1 passed**.
> - **Gherkin requirements/specs ARC42 appendix:** `python3 scripts/generate-gherkin-requirements-appendix.py --check` passes and reports **409 scenarios from 23 feature files** after extending the generator to scan both `requirements/` and `specs` and group scenarios by ADR 0020 stakeholder audience (`Business Need` vs `Ability`).
> - **EP-1 targeted Spring Security Reactive security slice plus specs sanity (2026-07-10):** `mvn -pl s3-reactive-api-adapter -Dtest=PhaseEp1SecurityIdentityRequirementsCucumberTest,PhaseEp1SecurityIdentityAwsCliCucumberTest,SigV4SecuritySpecsCucumberTest,Ep1SecurityServicesSpecsCucumberTest,SpecificationsCucumberTest test` passed with **40 Surefire tests/examples, 0 failures, 0 errors, 0 skipped** and no Spring Boot generated-password log. The Business Need runners execute all 13 EP-1 scenario examples in both WebTestClient and AWS CLI/e2e modes against a real RANDOM_PORT S3 API with `s3.security.enabled=true`: SigV4 auth, exact payload hashes, invalid auth rejection, deny-by-default, explicit deny, PublicAccessBlock/public ACL denial, expected bucket owner mismatch, durable redacted file audit, and an SSE-S3 encrypted-at-rest inspection slice. The path is wired through Spring Security Reactive (`SecurityWebFilterChain`, SigV4 `ServerAuthenticationConverter`, reactive authentication/authorization managers, and S3 XML security handlers). The maintainer-facing `specs/sigv4-verifier.feature` Ability runner still executes 7 component-spec examples for verifier/filter decisions; the existing specs runner still passes. This validates current REQ-SEC-001..009 scenarios plus REQ-SEC-010..013 durable backing-service specs, closing EP-1 for the declared local built-in scope. External identity federation remains future KA-4 scope.
> - **EP-2 metadata durability targeted restart-simulation slice (2026-07-10):** `mvn -pl s3-reactive-api-adapter -Dtest=PhaseEp2MetadataDurabilityCucumberTest test` passed with **21 discovered Cucumber scenarios, 3 executed, 0 failures, 0 errors, 18 skipped/excluded** and no Spring Boot generated-password log. The selected `@ep2-webclient-restart` Business Need scenarios validate storage-engine object tags, durable object ACL sidecars, and a combined bucket/object/multipart metadata restart-simulation path via WebTestClient and repository `reloadFromDisk()`.
> - **EP-2 metadata durability full Spring restart slice (2026-07-10):** `mvn -pl s3-reactive-api-adapter -am -Dtest=PhaseEp2MetadataDurabilityCucumberTest,PhaseEp2MetadataDurabilityFullRestartCucumberTest -Dsurefire.failIfNoSpecifiedTests=false test` passed with **42 discovered Cucumber scenarios, 21 executed, 0 failures, 0 errors, 21 skipped/excluded** and no Spring Boot generated-password log. The selected `@ep2-full-process-restart` Business Need scenarios stop and start two independent Spring application contexts with the same storage-engine filesystem root and prove bucket registry, multipart upload state, legal hold, object lock configuration, retention, object encryption, object restore state, object tags, object ACLs, CORS, notification, bucket object-lock, inventory-table, journal-table, ABAC, metadata, metadata-table, and the combined bucket/object-tag/object-ACL/multipart metadata path survive. The multipart validation also exposed and fixed that `ListMultipartUploads` was listing aborted uploads; the handler now filters to active uploads. The remaining skipped EP-2 scenarios are the explicit `@in-memory-exemption` cases, not storage-engine durability gaps.
> - **Current adapter-module local gate with AWS CLI visible (2026-07-10, AWS CLI 2.34.32):** `mvn -pl s3-reactive-api-adapter test` passed with **470 Surefire tests/examples, 0 failures, 0 errors, 102 skipped** and no Spring Boot generated-password log. The gate includes actual AWS CLI-backed Cucumber execution, including the new EP-1 AWS CLI/e2e runner (`13 passed / 0 skipped`).
> - Phase 1 upload reliability: REQ-UPLOAD-001 and REQ-UPLOAD-002 use the 2026-07-02 validation-mode decision: bootstrap JUnit integration validation is formally accepted as the sole required runtime validation mode and is documented with rationale in `phase-1-upload-storage-engine.feature`; their `@webclient`/`@awscli` examples are supplementary and excluded from Phase 1 Cucumber runner execution by `not @bootstrap-integration-required`. REQ-UPLOAD-003 is no longer an executable Phase 1 target. REQ-UPLOAD-004 is `@implemented-and-validated` from the previously recorded dual WebTestClient/AWS CLI runtime evidence.
> - Phase 2 filesystem reliability remains **implemented-and-validated for the declared Phase 2 scope**, including the REQ-FS-006 same-key concurrency scenario after a real torn-reference defect was found, fixed, and stress re-validated 6/6 (see the dedicated section below). This does not claim distributed readiness, broader S3 semantic completion, or later-phase production readiness.
> - Phase 3 now has pipeline-unit and WebTestClient Cucumber evidence for selected staged-pipeline requirements: REQ-PIPELINE-001, deterministic 256 MiB REQ-PIPELINE-002, and client-cancellation REQ-PIPELINE-005 execute in both modes and are `@implemented-and-validated`; REQ-PIPELINE-003 and REQ-PIPELINE-004 remain `@partial`; REQ-PIPELINE-006 is implemented-and-validated for its declared pipeline-unit mode. Streaming requirements REQ-PIPELINE-007..012 are implemented-and-validated by Cucumber static-architecture specs; REQ-PIPELINE-013 is implemented-and-validated only for the finite-demand adapter boundary covered by its runtime runner. This does not close the staged-pipeline scope.
> - Phase 5 S3 semantic compatibility is **implemented-and-validated for 32 WebTestClient-executed scenarios** and **implemented-not-e2e-validated for REQ-S3-002-C multipart restart/durability** because that scenario uses a direct same-directory filesystem repository probe rather than a full process/Spring restart. Phase 5 AWS CLI validation runs the same feature through an AWS CLI runner for its `@awscli-required` subset (14 passed / 19 skipped in the latest focused gate).
> - **EP-5 operability probe, shutdown, backup/restore, DR, and alerting slice (2026-07-10):** `PhaseEp5OperabilityRequirementsCucumberTest` passed with 14 Business Need scenarios, 0 failures/errors, and 1 opt-in live-monitoring scenario tag-filtered, validating Admin API `/admin/live` liveness, `/admin/ready` storage-catalog readiness, fail-closed `503 not-ready` behavior when required catalogs are unavailable (`REQ-OPS-001..003`), `REQ-OPS-004` SIGTERM shutdown/recovery evidence, `REQ-OPS-009` SIGTERM draining of an active 524,288-byte streaming PutObject with restart byte-count/checksum verification, `REQ-OPS-010` draining of an active 524,288-byte multipart UploadPart followed by restart completion and final checksum verification, `REQ-OPS-011` draining of two concurrent 262,144-byte PutObjects with restart checksum verification for both, `REQ-OPS-012` draining of an active CompleteMultipartUpload with restart checksum verification of the assembled object, `REQ-OPS-013` cancellation and abort of an active UploadPart followed by restart verification that no object, active upload, or part artifacts remain, `REQ-OPS-014` abort overlapping an active CompleteMultipartUpload during SIGTERM with HTTP 204/NoSuchUpload and restart cleanup verification, `REQ-OPS-015` bounded mixed-load draining of three streaming writes and two throttled reads with response and restart checksum verification, `REQ-OPS-005` offline backup/restore evidence, `REQ-OPS-006` single-node DR objectives: RTO 30 seconds and RPO last completed offline backup, and `REQ-OPS-008` shipped SLO/alerting bundle coverage for Admin probes, S3 smoke, storage capacity, backup age, generated-password regressions, and manifest-schema regressions. `docs/runbooks/graceful-shutdown.md`, `docs/runbooks/backup-restore.md`, `docs/runbooks/disaster-recovery.md`, and `docs/runbooks/slo-alerts.md` record the validated procedures and their scope. The opt-in `PhaseEp5LiveAlertDeliveryRequirementsCucumberTest` also passed `REQ-OPS-021` with 1 executed and 14 tag-filtered scenarios, validating the exact shipped Prometheus rules with `promtool`, evaluating the Admin liveness alert against a failing probe, and delivering `MagratheaAdminLivenessProbeDown` through live Prometheus and Alertmanager containers to an operator webhook receiver. The EP-5 storage migration specs (`REQ-OPS-007`, `REQ-OPS-016..020`) also passed, proving current object manifests, multipart upload sessions, bucket registry JSON, object configuration JSON, S3 object manifest references, and object ACL sidecars declare schema version `1`, legacy records without the key remain readable as compatibility version `0`, and unsupported future schema version `999` is rejected. The focused CI gate including EP-1, EP-2, EP-3, EP-5, KA-5, and Phase 5 runners passed with 206 tests/examples, 0 failures/errors, and 77 skipped. Generated-password banner count: 0.
> - Phase 6 distributed readiness remains **implemented-not-e2e-validated** for the modeled domain scope only. It is unit validated through distributed-readiness domain tests, but real replication execution, networked membership, healing/rebalance job runners, actual multi-node durability, and full e2e multi-node validation remain absent. Distributed production readiness is not claimed.
> - **JaCoCo is the current coverage baseline.** Clover/OpenClover is optional/legacy.
>
> The generated semantic S3 coverage matrix is available at `docs/api-coverage.md`. Its conservative baseline is 111 official operations, 108 with router mappings, 20 with explicit operation-linked `@implemented-and-validated` evidence, and 91 not yet eligible for a 100% completion claim.

## Summary

| Suite | Passed | Failed | Skipped / excluded | Total | Notes |
|---|---:|---:|---:|---:|---|
| AWS CLI S3 compatibility script | 46 | 82 | 0 | 128 | Stale standalone `test-aws-cli.sh` result; endpoint: `http://localhost:8080` |
| Single-node backend WebTestClient requirements | 234 | 0 | 26 | 260 | `SingleNodeBackendWebTestClientRequirementsCucumberTest` PASS in the 2026-07-09 full Maven gate; migrated ADR 0021 requirements replacing the legacy `object-store/` runner. |
| Single-node backend AWS CLI requirements | 26 | 0 | 46 | 72 | `SingleNodeBackendAwsCliRequirementsCucumberTest` PASS in the 2026-07-09 full Maven gate; same migrated single-node backend requirements filtered to `@awscli-required`. |
| Phase 2 WebTestClient specs | 7 | 0 | 4 | 11 | Isolated Phase 2 runner; REQ-FS-001 through REQ-FS-006 WebTestClient-required scope passed from `specs/phase-2-filesystem-reliability.feature`; skipped/excluded examples are tagged `@awscli`. |
| Phase 2 storage-engine AWS CLI specs | 4 | 0 | 7 | 11 | `Phase2StorageEngineAwsCliRequirementsCucumberTest` PASS in the latest full Maven gate against `specs/phase-2-filesystem-reliability.feature`; 4 `@awscli-required` examples executed, 7 skipped/excluded by tag filtering. |
| EP-2 metadata durability WebTestClient restart-simulation | 3 | 0 | 18 | 21 | `PhaseEp2MetadataDurabilityCucumberTest` PASS: object tags, object ACL, and combined bucket/object/multipart metadata scenarios executed against storage-engine profile and `reloadFromDisk()` restart simulation. |
| EP-2 metadata durability full Spring restart | 18 | 0 | 3 | 21 | `PhaseEp2MetadataDurabilityFullRestartCucumberTest` PASS: bucket registry, multipart upload state, legal hold, object lock configuration, retention, object encryption, object restore state, object tags, object ACL, eight bucket-config family scenarios, and combined bucket/object-tag/object-ACL/multipart metadata scenarios executed by stopping one Spring context and starting another against the same storage-engine filesystem root. |
| Phase 3 storage-engine reactive application gate | 159 | 0 | 0 | 159 | `mvn -B -pl storage-engine-reactive-application -am test` PASS; unit/application validation for REQ-PIPELINE-001 through REQ-PIPELINE-006. |
| Phase 3 pipeline-unit Cucumber specs | 9 | 0 | 12 | 21 | `Phase3PipelineUnitSpecsCucumberTest` PASS: executes REQ-PIPELINE-001..006 selected pipeline-unit examples through the production staged pipeline and real filesystem adapters; includes the deterministic 256 MiB REQ-PIPELINE-002 bounded-demand/live-buffer slice, canonical UUID/sidecar interoperability, upstream-failure cleanup, cancellation cleanup, and instrumentation. |
| Phase 3 WebTestClient Cucumber specs | 5 | 0 | 16 | 21 | `Phase3ReactivePipelineCucumberTest` PASS: executes REQ-PIPELINE-001, deterministic 256 MiB REQ-PIPELINE-002, REQ-PIPELINE-003, the supported REQ-PIPELINE-004 chunk-fault example, and REQ-PIPELINE-005 loopback HTTP client cancellation through the S3 adapter. |
| Phase 3 static architecture Cucumber specs | 6 | 0 | 15 | 21 | `Phase3ReactivePipelineStaticArchitectureSpecsCucumberTest` PASS: enforces REQ-PIPELINE-007..012 for PutObject, fixed-window dedup, non-range/range GetObject, UploadPart/UploadPartCopy, and multipart part-store streaming constraints. |
| Phase 3 runtime backpressure boundary Cucumber spec | 1 | 0 | 20 | 21 | `Phase3RuntimeBackpressureSpecsCucumberTest` PASS: REQ-PIPELINE-013 applies and probes a four-buffer demand window across existing S3 object/multipart boundaries; not staged-pipeline e2e evidence. |
| Same-key concurrency unit test (`S3ObjectManifestReferenceStoreConcurrencyTest`) | 3 | 0 | 0 | 3 | Torn-reference impossibility under 16-thread same-key races with a concurrent reader; no lost updates in 16×100 read-compose-write increments; unrelated keys uncorrupted while a hot key is contended. |
| Phase 1 Cucumber runners (WebTestClient + AWS CLI) | 9 + 9 | 0 | 19 + 19 excluded by tag filter | 28 + 28 discovered | Each runner discovered 28 scenarios and executed 9 with 0 failures in the 2026-07-02 Podman gate; REQ-UPLOAD-004 validated independently in both modes after REQ-UPLOAD-003 removal, run twice with identical counts; feature-file tag refreshed to `@implemented-and-validated` and the feature-level `@partial` tag removed. |
| Phase 2 AWS CLI same-key stress re-validation | 6 runs / 6 passed | 0 | 0 | 6 fresh-JVM runs | Phase 2 AWS CLI runner re-run 6 times in fresh JVMs after the concurrency fix; REQ-FS-006 same-key passed 6/6 (previously the defect reproduced 4/4 in one session). |
| Phase 5 WebTestClient semantic compatibility requirements | 32 | 0 | 1 | 33 | `Phase5S3SemanticCompatibilityRequirementsCucumberTest` PASS; requirement source `s3-reactive-api-adapter/src/test/features/requirements/phase-5-s3-semantic-compatibility.feature`. |
| Phase 6 storage-engine domain distributed-readiness gate | 164 | 0 | 0 | 164 | `mvn -B -pl storage-engine-domain test --no-transfer-progress` PASS; modeled domain/unit validation for REQ-DIST-001 through REQ-DIST-006. |
| Domain quality bounded first pass | 464 | 0 | 0 | 464 | `mvn -B -pl storage-engine-domain,object-store-domain test --no-transfer-progress` PASS; storage-engine-domain: 172 tests; object-store-domain: 292 tests. Covers collection immutability exposure and additional `StoredObject` invariant tests only; not a complete domain redesign. |
| Runtime correctness bounded first pass | n/a | 0 | n/a | n/a | `mvn -B -pl object-store-reactive-repository-storage-engine-infrastructure,storage-engine-reactive-infrastructure,s3-reactive-api-adapter,bootstrap-application -am test --no-transfer-progress` PASS / BUILD SUCCESS; `mvn -B test --no-transfer-progress` PASS / BUILD SUCCESS. Covers storage-engine repository read-after-write regression, `FileSystemManifestRepository` checksum round-trips, and `BlockingFileSystemOperation` bounded-elastic filesystem scheduling. Broader staged pipeline/runtime backpressure evidence remains open. |
| Phase 1 upload reliability Cucumber runtime coverage | 9 + 9 executed | 0 | 19 + 19 excluded by tag filter | 28 + 28 discovered | 2026-07-02 Podman gate: WebTestClient and AWS CLI Phase 1 runners each executed 9 scenarios with 0 failures; REQ-UPLOAD-004 validated independently in both modes after REQ-UPLOAD-003 removal, confirmed by two identical runs (before and after the feature-file tag refresh). An earlier transient pass that depended on incorrectly reintroduced non-dedup multi-chunk persistence remains rejected as evidence. |
| Phase 1 upload reliability bootstrap validation (`bootstrap-application`) | 17 bootstrap tests total | 0 | 0 | 17 | Latest full Maven gate includes `bootstrap-application`: 17 tests, 0 failures, 0 errors, 0 skipped. Bootstrap evidence covers REQ-UPLOAD-001 restart safety, REQ-UPLOAD-002 manifest reload, REQ-UPLOAD-004 failed-upload atomicity, REQ-UPLOAD-005 read-after-write, and REQ-UPLOAD-006 corruption detection. The deleted REQ-UPLOAD-003 bootstrap test is not evidence. Under the 2026-07-02 validation-mode decision, bootstrap evidence is the agreed sole required runtime validation mode for REQ-UPLOAD-001/002; REQ-UPLOAD-004 also has independent Cucumber runtime validation in both modes. |
| Section E YAML catalog / backend-wiring / admin-API bounded first pass | `YamlStoragePolicyCatalogTest` (14), `YamlStorageDeviceCatalogTest` (10), `YamlDiskSetCatalogTest` (12), `MinioStandardIntegrationTest` (2), `StorageEngineMissingExternalConfigTest` (1), `StorageEngineBackendContextTest` (3), `AdminRouterTest` (9) | `mvn -B test` PASS | All Section E acceptance gates met: malformed/duplicate/unresolved catalog rejection, MINIO_STANDARD deterministic plans, fail-fast on missing backend config, mutually exclusive backend selection, admin API uses same catalog beans. |
| Maven adapter module local gate | 368 | 0 | 102 | 470 | `mvn -pl s3-reactive-api-adapter test` PASS with AWS CLI visible (`/usr/bin/aws`, AWS CLI 2.34.32); the run also checked that Spring Boot did not emit a generated-password log. This includes actual AWS CLI-backed Cucumber execution, including EP-1 AWS CLI/e2e validation. |
| EP-5 Admin API operability, graceful shutdown, backup/restore, DR, and SLO alerting | 14 | 0 | 1 | 15 | `PhaseEp5OperabilityRequirementsCucumberTest` PASS: Admin API `/admin/live` returns liveness status and link to readiness; `/admin/ready` returns readiness status with storage-policy, storage-device, and disk-set catalog components ready; missing catalogs return `503 not-ready`; SIGTERM shutdown exits without forced termination and preserves a committed S3 object across storage-engine process restart; single PutObject, multipart UploadPart, CompleteMultipartUpload, and two concurrent PutObject requests are drained with HTTP 200 before exit, a bounded mixed load of three writes and two reads is also drained, while cancelled-and-aborted and abort-wins-over-completion paths leave no multipart artifacts; the PutObject survives restart directly, while the drained part ETag completes the multipart upload after restart and the final byte count/checksum matches; offline backup/restore copies the storage root, simulates primary data loss, restores, and reads the committed S3 object back; single-node DR rehearsal validates RTO 30 seconds and RPO last completed offline backup; shipped Prometheus/Loki alert rules and `docs/runbooks/slo-alerts.md` cover SLO objectives, first-response links, generated-password regression detection, and manifest-schema regression detection. Generated-password count: 0. |
| EP-5 live Prometheus/Alertmanager delivery (opt-in Docker runner) | 1 | 0 | 14 | 15 | `PhaseEp5LiveAlertDeliveryRequirementsCucumberTest` PASS: shipped rule pack passes `promtool`; Prometheus evaluates the Admin liveness alert against a failing probe and Alertmanager delivers it to the temporary operator webhook. Generated-password and Netty leak counts: 0. |
| EP-5 storage-engine metadata schema migration specs | 6 | 0 | 0 | 6 | `PhaseEp5StorageMigrationSpecsCucumberTest` PASS: current object manifests, multipart upload sessions, bucket registry records, object configuration records, S3 object manifest references, and object ACL sidecars declare schema version `1`, legacy records without the key remain readable as compatibility version `0`, and unsupported future schema version `999` is rejected. |
| Source/build hygiene and packaging first pass | n/a | 0 | Docker bridge unavailable locally; validated with host network | n/a | `mvn -B validate --no-transfer-progress` PASS / BUILD SUCCESS; `bash scripts/check-source-hygiene.sh` PASS. Native host packaging validates with Oracle GraalVM 25: `mvn -Pnative -pl bootstrap-application -am -DskipTests native:compile` PASS, followed by a native process smoke check on ports 18080/18081 where `/admin/health` returned healthy JSON and no generated Spring Security password banner appeared. Native Docker packaging validates with `docker build --network=host -f Dockerfile.native -t magrathea-objectstorage:native .` PASS and container smoke validation against `/admin/health`, S3 ListBuckets XML/JSON, and S3 bucket/object PUT/GET; runtime image contains no `java`/`javac` and now defaults packaged single-node containers to storage-engine profile/catalog settings. Root JVM Docker packaging validates with `docker build --network=host -f Dockerfile -t magrathea-objectstorage:jvm .` PASS, appendix freshness/docs/UI regeneration gates, unmasked Maven packaging, non-root runtime ownership of `/app/data`, packaged storage-engine YAML catalogs, Admin health/live/ready smoke, S3 ListBuckets XML, bucket/object PUT/GET smoke, selected storage-engine backend log verification, and 0 generated-password banners in build/runtime logs. The sandbox's default Docker bridge still fails before project steps with veth setup errors, so host networking was used for Docker validation. Root `-Pcoverage` is documented as the canonical coverage profile; duplicate module-level coverage profile consolidation remains pending in `bootstrap-application/pom.xml`. |
| Module/layering architecture bounded first pass | n/a | 0 | n/a | n/a | `scripts/check-module-layering.sh` is wired into root-only `validate`; latest full `mvn -B --no-transfer-progress clean test -o` gate passed and includes the root validate/layering guard. Backend selection context-test evidence remains in place; package naming remains bridged by explicit scan roots. |
| Maven full test gate (all modules) | 990 | 0 | 148 | 1138 | Latest full Maven gate (2026-07-10): `mvn test` at repo root PASS / BUILD SUCCESS; all 13 reactor modules; Surefire XML aggregate: 1138 discovered tests/examples, 990 passed/executed, 0 failures, 0 errors, 148 skipped/excluded. The log was checked for Spring Boot's generated-password banner and none was found. |
| S3 API semantic coverage report | 1 | 0 | 0 | 1 | `S3ApiSemanticCoverageReportSpecsCucumberTest` and `python3 scripts/generate-s3-api-coverage.py --check` PASS: all 111 official operations have one row; 108 have mapped handlers, 20 have explicit operation-linked implemented-and-validated evidence, and 91 remain pending completion or stronger evidence/classification. |
| Gherkin requirements/specs appendix generator check | n/a | 0 | n/a | 409 scenarios | `python3 scripts/generate-gherkin-requirements-appendix.py --check` PASS; generated ARC42 appendix is fresh: 409 scenarios from 23 feature files (13 `Business Need`, 10 `Ability`) grouped by ADR 0020 stakeholder audience. |
| EP-1 Spring Security Reactive targeted security slice plus specs sanity | 40 | 0 | 0 | 40 Surefire tests/examples | `mvn -pl s3-reactive-api-adapter -Dtest=PhaseEp1SecurityIdentityRequirementsCucumberTest,PhaseEp1SecurityIdentityAwsCliCucumberTest,SigV4SecuritySpecsCucumberTest,Ep1SecurityServicesSpecsCucumberTest,SpecificationsCucumberTest test` PASS. Business Need Cucumber executes all 13 EP-1 secured-mode scenario examples through Spring Security Reactive in both WebTestClient and AWS CLI/e2e modes, including exact payload-hash replay, payload-hash mismatch rejection, deny-by-default, explicit-deny-overrides-allow, PublicAccessBlock/public ACL denial, expected-owner mismatch, durable redacted file audit, and an SSE-S3 encrypted-at-rest inspection slice; Ability Cucumber executes 7 maintainer-facing verifier/filter component-spec examples; existing fs-concurrency specs still pass. EP-1 backing-service specs validate durable encrypted credentials/revocation, policy reload, tamper-evident audit, and durable key-management material. |
| Admin API adapter | 9 | 0 | 0 | 9 | Previous verified gate: `mvn -B -pl admin-api-adapter -am test` — build success. |
| JaCoCo coverage | See section | - | - | - | Current baseline; latest reports under `target/site/jacoco` when generated. |

## Current Verified Results

| Scope | Evidence | Result | Notes |
|---|---|---:|---|
| Phase 2 filesystem reliability (`storage-engine-reactive-infrastructure`, `s3-reactive-api-adapter`) | Spec source: `s3-reactive-api-adapter/src/test/features/specs/phase-2-filesystem-reliability.feature`; `mvn -B -pl s3-reactive-api-adapter -am test -Dsurefire.failIfNoSpecifiedTests=false`; `mvn -B test`; isolated Phase 2 WebTestClient spec runner; dedicated storage-engine AWS CLI Phase 2 validation | Targeted adapter gate PASS; full Maven test PASS; Phase 2 WebTestClient: 11 examples, 7 passed, 4 `@awscli` examples excluded/skipped; Phase 2 AWS CLI: 11 discovered, 4 passed, 7 skipped/excluded, 0 failed | Implemented evidence covers atomic chunk temp-file/fsync/rename writes with SHA-256 sidecar checksums, atomic manifest temp-file/fsync/rename writes with checksum trailer, read-time chunk/manifest checksum verification, `FileSystemRecoveryScanner` reporting/quarantine/idempotence, S3 XML mapping for storage-engine integrity errors, disabled-by-default write fault injection for interrupted chunk/manifest write tests, and defaulting `null`/blank storage class to `STANDARD` for storage-engine `PutObject`. Phase 2 is implemented-and-validated for declared scope only; distributed readiness, broader S3 semantic completion, and later phases remain pending. The REQ-FS-006 same-key scenario exposed a real torn-reference concurrency defect that was fixed and stress re-validated 6/6 (see the dedicated section below); the feature tag was restored `@partial` → `@implemented-and-validated`. |
| Streaming architecture specs — REQ-PIPELINE-007..012 (`s3-reactive-api-adapter`, `storage-engine-reactive-infrastructure`) | Spec source: `s3-reactive-api-adapter/src/test/features/specs/phase-3-reactive-pipeline.feature`; Cucumber static architecture runner: `Phase3ReactivePipelineStaticArchitectureSpecsCucumberTest`; fast static guard: `ReactiveUploadStreamingArchitectureTest` for REQ-PIPELINE-007/008 | Cucumber static architecture runner PASS: 9 discovered, 6 passed, 3 skipped. Phase 5 WebTestClient/AWS CLI semantic regressions also PASS after multipart streaming changes. | Implemented and validated: `S3ObjectOperationsHandler.putObject` computes ETag/length via a single-pass `UploadDigest` tee; `FixedWindowDedupStep` accumulates fixed windows incrementally; non-range/range GetObject streams content; `S3MultipartHandler.uploadPart` and `uploadPartCopy` pass DataBuffer streams to `S3MultipartPartStore`; the part store writes with `DataBufferUtils.write` and reads with `DataBufferUtils.read`. `DataBufferUtils.join` and whole-body/whole-part materialization are forbidden in the covered methods. |
| REQ-FS-006 same-key concurrency defect fix (`object-store-reactive-repository-storage-engine-infrastructure`) | Unit test: `S3ObjectManifestReferenceStoreConcurrencyTest`; Phase 2 AWS CLI runner stress re-validation (6 fresh-JVM runs); 2026-07-09 full Maven gate | Unit test PASS: 3 tests, 0 failures. Stress re-validation PASS: REQ-FS-006 same-key 6/6 (previously failed 4/4 in one session). Latest full Maven gate PASS. | Defect: a non-atomic find→save read-modify-write on the S3 object reference in `StorageEngineReactiveS3ObjectRepository.save(...)` / `S3ObjectManifestReferenceStore` caused torn references under concurrent same-key PUTs (checksum from one upload, body from another; observed as an AWS CLI CRC64NVME checksum mismatch). Fix: `S3ObjectManifestReferenceStore.commitLatest` serializes the whole read-compose-write cycle under a striped per-key `ReentrantLock` (64 stripes) and persists via temp file + `ATOMIC_MOVE` (`REPLACE_EXISTING`, with fallback if unsupported); the repository composes the complete reference inside one serialized per-key commit. Semantics: last-writer-wins, crash-safe. Feature tag restored `@partial` → `@implemented-and-validated` in `phase-2-filesystem-reliability.feature`. |
| Phase 3 reactive pipeline (`storage-engine-reactive-application`) | `s3-reactive-api-adapter/src/test/features/specs/phase-3-reactive-pipeline.feature`; `mvn -B -pl storage-engine-reactive-application -am test`; `mvn -B -pl s3-reactive-api-adapter -am test -Dsurefire.failIfNoSpecifiedTests=false`; `mvn -B test` | Storage-engine reactive application gate PASS: 159 total, 159 passed, 0 failed/errors/skipped. Targeted adapter gate PASS: 814 total, 803 passed, 0 failed/errors, 11 skipped. Full Maven test PASS: 833 total, 822 passed, 0 failed/errors, 11 skipped. | `@implemented-not-e2e-validated`: validates staged read/write pipeline behavior at unit/application level only. No Phase 3 WebTestClient, AWS CLI, or end-to-end Cucumber validation has been executed yet. No Phase 4 metrics/tracing adapters are included. |
| Phase 5 S3 semantic compatibility (`s3-reactive-api-adapter`) | Requirement feature: `s3-reactive-api-adapter/src/test/features/requirements/phase-5-s3-semantic-compatibility.feature`; WebTestClient runner: `Phase5S3SemanticCompatibilityRequirementsCucumberTest`; AWS CLI runner: `Phase5S3SemanticCompatibilityAwsCliCucumberTest`; step glue under `s3-reactive-api-adapter/src/test/java/com/example/magrathea/s3api/cucumber/requirements/` and `.../phase5awscli/`; focused gates listed in the summary. | Phase 5 WebTestClient runner PASS: 33 discovered, 32 passed, 1 skipped. Phase 5 AWS CLI runner PASS: 33 discovered, 14 passed, 19 skipped/excluded, 0 failures/errors. Phase 5 multipart full-process restart runner PASS: 33 discovered, 1 passed, 32 skipped. | `@implemented-and-validated` for declared Phase 5 WebTestClient semantics including UploadPartCopy copied-byte assembly and multipart XML error semantics. REQ-S3-002-C is `@implemented-not-e2e-validated` because multipart uploaded-part durability is validated through WebTestClient plus a direct same-directory filesystem repository probe, not a full process/Spring restart. REQ-S3-007 scenarios pass as explicit `@not-implemented` or `@config-only` classification for versioning, object lock, and lifecycle; no enforcement is claimed. Phase 5 AWS CLI validates the `@awscli-required` subset selected by its runner. |
| Phase 6 distributed readiness (`storage-engine-domain`) | Spec source: `s3-reactive-api-adapter/src/test/features/specs/phase-6-distributed-readiness.feature`; domain implementation: `storage-engine-domain/src/main/java/com/example/magrathea/storageengine/domain/distributed/`; tests: `DistributedPlacementPlannerTest`, `QuorumPolicyTest`, `AntiEntropyPlannerTest`, `RebalancePlannerTest`, `DistributedReadinessReporterTest`; gates: `mvn -B -pl storage-engine-domain test --no-transfer-progress`, `mvn -B test --no-transfer-progress` | Storage-engine domain gate PASS: 164 tests, 0 failures, 0 errors, 0 skipped. Full Maven gate PASS / BUILD SUCCESS: 883 test cases observed, 0 failures, 0 errors, 11 skipped. | `@implemented-not-e2e-validated` for modeled domain/unit scope only. Deterministic placement, health-aware membership decisions, quorum decisions, anti-entropy/healing plans, rebalance plans, and readiness classification are modeled and unit validated. Real networked membership, real replication execution, healing/rebalance job runners, actual multi-node durability, and full e2e multi-node validation remain absent. No public storage-engine object API endpoints were added; S3 object behavior remains exposed through S3-compatible APIs only. |
| Domain quality bounded first pass (`storage-engine-domain`, `object-store-domain`) | Tests: `object-store-domain/src/test/java/.../DomainCollectionImmutabilityTest.java`, `storage-engine-domain/src/test/java/.../DomainCollectionImmutabilityTest.java`, and additional `StoredObjectTest` restore invariant coverage; gates: `mvn -B -pl storage-engine-domain,object-store-domain test --no-transfer-progress`, `mvn -B test --no-transfer-progress` | Targeted domain gate PASS: storage-engine-domain 172 tests and object-store-domain 292 tests, 0 failures/errors. Full Maven gate PASS / BUILD SUCCESS. | Bounded first pass only: validates defensive collection copies/immutable exposure and strengthened `StoredObject` invariants. `StoredObject` remains mutable through controlled lifecycle methods; duplicate/ambiguous encryption class names, explicit `Bucket` deleted terminal state, and broad immutable aggregate/lifecycle redesign remain open. |
| Runtime correctness bounded first pass (PLAN section D) | Tests strengthened for `StorageEngineReactiveS3ObjectRepository`, `FileSystemManifestRepository`, and filesystem scheduling; gates: `mvn -B -pl object-store-reactive-repository-storage-engine-infrastructure,storage-engine-reactive-infrastructure,s3-reactive-api-adapter,bootstrap-application -am test --no-transfer-progress`, `mvn -B test --no-transfer-progress` | Targeted module gate PASS / BUILD SUCCESS. Full Maven gate PASS / BUILD SUCCESS. | Evidence covers real storage-engine repository read-through via manifest reference and `orchestrator.read(...)`, manifest round-trip of declared upload checksum and multipart part checksum results, and `BlockingFileSystemOperation` bounded-elastic scheduling for blocking filesystem work. Later EP-3 work has closed GetObject streaming and semantic multipart assembly/copy; broader staged pipeline/runtime backpressure evidence remains open. |
| Gherkin requirements/specs ARC42 appendix (`docs/arc42/generated/gherkin-requirements.adoc`) | `python3 scripts/generate-gherkin-requirements-appendix.py --check` | PASS: generated appendix is fresh; 409 scenarios from 23 feature files | Documentation-generation evidence only. The report summarizes explicit scenario tags and does not invent implementation or validation results. |
| Source/build hygiene gates (PLAN section A safe first pass) | `mvn -B validate --no-transfer-progress`; `bash scripts/check-source-hygiene.sh`; root JVM `docker build` and smoke | PASS / BUILD SUCCESS; PASS; Docker PASS with host networking | Evidence covers Maven validation and the source hygiene script that fails on source-tree `.class` files, root `com/`, root `META-INF`, and generated `META-INF/maven` outside ignored build output directories. `.gitignore`, `.dockerignore`, root Maven plugin/coverage-profile documentation, and Dockerfile runtime healthcheck support were updated. Root JVM Docker validation now passes with public ECR mirrored base images, full `scripts/` copy for Maven layering checks, no Maven fail-never mode, non-root writable `/app/data`, packaged storage-engine YAML catalogs, Admin `/admin/health`, `/admin/live`, `/admin/ready` ready status, S3 ListBuckets XML, bucket/object PUT/GET smoke, selected storage-engine backend log verification, and 0 generated-password banners. Duplicate module-level `coverage` profile consolidation in `bootstrap-application/pom.xml` remains pending. |
| Module/layering architecture guard (PLAN section B) | `scripts/check-module-layering.sh`; `mvn -B validate --no-transfer-progress`; `mvn -B test --no-transfer-progress` | PASS; PASS / BUILD SUCCESS; PASS / BUILD SUCCESS | The guard blocks application-to-infrastructure dependency inversions for object-store, storage-engine, and repository-application modules; preserves explicit `objectstore`/`objectstorage` package naming scan roots while the naming inconsistency remains; and checks explicit backend profile separation. Existing backend selection context tests still assert default in-memory beans versus storage-engine beans and fail-fast backend conflicts. Full Maven build succeeded after the ARC42 docs conversion fix changed generated appendix output from `NOTE:` admonition syntax to a plain paragraph. This is bounded first-pass evidence, not complete architecture cleanup or an ArchUnit/Java test claim. |
| Phase 5 domain planning (`storage-engine-domain`) | Commit `b0a5f74`; `PersistencePlannerMinioStandardTest` | 152 tests passing, 0 failures | Verifies deterministic `MINIO_STANDARD` persistence planning in the domain model. |
| Phase 5 YAML catalogs and MINIO_STANDARD integration (`storage-engine-reactive-infrastructure`) | Commit `0ec84cf`; `MinioStandardIntegrationTest` | 26 tests passing, 0 failures | Verifies YAML catalog/device integration and `MINIO_STANDARD` selection with S3 storage class `STANDARD`, dedup disabled, EC planning `4 data / 2 parity`, replication factor `1`, compression disabled, and encryption disabled by default; storage-engine runtime read/write wiring and physical EC shard placement remain pending for Phase 6/7. |
| Phase 8 backend Admin API (`admin-api-adapter`) | `mvn -B -pl admin-api-adapter -am test`; `AdminRouterTest` | 9 tests passing, 0 failures, build success | Verifies configuration-as-code/read-only admin catalog behavior for policies/devices/disk sets, structured validation responses for `POST /admin/storage-policies/validate`, non-persistence of validation, and runtime rejection of policy mutations. |
| Section E storage-engine configuration bounded first pass | `YamlStoragePolicyCatalogTest` (14), `YamlStorageDeviceCatalogTest` (10), `YamlDiskSetCatalogTest` (12), `MinioStandardIntegrationTest` (2), `StorageEngineMissingExternalConfigTest` (1), `StorageEngineBackendContextTest` (3), `AdminRouterTest` (9); `mvn -B test` PASS | 52 tests, 0 failures, 0 errors | `@implemented-and-validated` for declared Section E scope. YAML catalogs validate duplicate IDs, malformed YAML, domain-invalid configs, zero-device disk sets, blank device references, unresolved device references. MINIO_STANDARD loaded from YAML produces deterministic EC `4/2` plans. Storage-engine backend fails fast on missing config. Mutually exclusive `storage-engine` vs `single-node` profile selection proven. Admin API uses same catalog beans (unit-level). Gaps: no full Spring Boot integration test for admin API with real YAML catalogs; physical EC shard placement and multi-node topology remain modeled only. |
| Phase 1 upload reliability (`bootstrap-application` + Cucumber runners) | Bootstrap tests: `StorageEngineRestartSafetyTest`, `StorageEngineHttpReadAfterWriteTest`, `StorageEngineUploadAtomicityTest`, `StorageEngineIntegrityDetectionTest`; Phase 1 Cucumber runners (WebTestClient + AWS CLI) in the 2026-07-02 Podman gate, run twice (before/after the feature-file tag refresh) | Bootstrap evidence per previous Maven runs; Phase 1 runners: 28 scenarios discovered, 9 executed per mode, 0 failures, identical on both runs | `@implemented-and-validated` for REQ-UPLOAD-001/002 under accepted bootstrap-integration validation mode. REQ-UPLOAD-003 executable validation was removed because multi-durable-chunk non-dedup persistence is intentionally absent. REQ-UPLOAD-004 is now `@implemented-and-validated`: runtime-validated independently in both WebTestClient and AWS CLI modes, and the feature-file tag has been refreshed from `@implemented-not-e2e-validated` to `@implemented-and-validated` with the feature-level `@partial` tag removed. REQ-UPLOAD-005/006 retain previous Cucumber runtime evidence. |
| ADR 0021 single-node backend requirements migration (`s3-reactive-api-adapter`) | `SingleNodeBackendWebTestClientRequirementsCucumberTest` + `SingleNodeBackendAwsCliRequirementsCucumberTest`; migrated `requirements/single-node-backend-*.feature` files | WebTestClient: 260 discovered / 234 passed / 26 skipped; AWS CLI: 72 discovered / 26 passed / 46 skipped; 0 failures/errors | Replaces the legacy `features/object-store/` and `features/awscli/` runners. The migrated requirements explicitly scope these scenarios to the single-node in-memory backend, not the storage-engine backend. |

### Phase 2 Filesystem Reliability Scenario Status

| Requirement | Scenario | Validation mode | Status | Notes |
|---|---|---|---|---|
| REQ-FS-001 | Interrupted chunk write | Isolated Phase 2 WebTestClient spec runner | ✅ Passed | Disabled-by-default write fault injection validates interrupted chunk writes do not publish incomplete chunks. |
| REQ-FS-002 | Interrupted manifest write | Isolated Phase 2 WebTestClient spec runner | ✅ Passed | Disabled-by-default write fault injection validates interrupted manifest writes do not publish incomplete manifests. |
| REQ-FS-003 | Corrupted chunk detection | Isolated Phase 2 WebTestClient spec runner; dedicated storage-engine AWS CLI Phase 2 validation | ✅ Passed | Read-time chunk checksum verification detects corruption and returns a mapped S3 XML error response. |
| REQ-FS-004 | Corrupted manifest detection | Isolated Phase 2 WebTestClient spec runner; dedicated storage-engine AWS CLI Phase 2 validation | ✅ Passed | Manifest checksum trailer verification detects corruption and returns a mapped S3 XML error response. |
| REQ-FS-005 | Recovery scanner | Isolated Phase 2 WebTestClient spec runner | ✅ Passed | `FileSystemRecoveryScanner` reports/quarantines incomplete or corrupt state deterministically and idempotently. |
| REQ-FS-006 | Concurrent PUT of different keys | Isolated Phase 2 WebTestClient spec runner; dedicated storage-engine AWS CLI Phase 2 validation | ✅ Passed | Concurrency scenario passed for different object keys. |
| REQ-FS-006 | Concurrent PUT of the same key | Isolated Phase 2 WebTestClient spec runner; dedicated storage-engine AWS CLI Phase 2 validation; `S3ObjectManifestReferenceStoreConcurrencyTest`; 6× fresh-JVM AWS CLI stress re-runs | ✅ Passed (defect found → fixed → 6/6 stress-validated) | A real torn-reference defect (non-atomic find→save read-modify-write in `StorageEngineReactiveS3ObjectRepository.save(...)` / `S3ObjectManifestReferenceStore`) reproduced 4/4 in one session as an AWS CLI CRC64NVME checksum mismatch. Fixed by `commitLatest` per-key striped locking (64 stripes) plus temp file + `ATOMIC_MOVE` persistence; last-writer-wins, crash-safe. Re-validated: Phase 2 AWS CLI runner 6/6 in fresh JVMs; new unit test passes 3/3. Feature tag restored `@partial` → `@implemented-and-validated`. |
| Phase 2 mode summary | WebTestClient and AWS CLI declared scope | WebTestClient + AWS CLI | ✅ Passed for declared scope | WebTestClient: 11 examples total, 7 passed, 4 `@awscli` examples skipped/excluded. AWS CLI: 4 scenarios/examples, 4 passed, 0 failed, 0 skipped. |

### Phase 3 Reactive Pipeline Unit/Application Requirement Status

| Requirement | Scenario | Validation mode | Status | Notes |
|---|---|---|---|---|
| REQ-PIPELINE-001 | Write pipeline stage order, canonical chunk publication, and events | Pipeline-unit + WebTestClient Cucumber | ✅ Implemented-and-validated | Proves stage order, publication order, UUID chunk filenames, SHA-256 sidecars, canonical filesystem-node readability, clean recovery scan, and exact readback. |
| REQ-PIPELINE-002 | Bounded demand/backpressure/no whole-object aggregation | Pipeline-unit + WebTestClient Cucumber | ✅ Implemented-and-validated | Both modes stream a deterministic 256 MiB demand-controlled body through the production staged pipeline and real filesystem with measured live source-buffer retention and request signals bounded by four, ordered manifest/length validation, exact streamed SHA-256 readback, and anti-aggregation source guards. WebTestClient uses the dedicated 1 MiB-window `PIPELINE` catalog policy. |
| REQ-PIPELINE-003 | Read pipeline manifest chunk order | Pipeline-unit + WebTestClient Cucumber | ⚠️ Partial | Both modes use the actual deterministic 256 MiB fixture and incremental SHA-256 readback without test-side response aggregation. Pipeline-unit proves first-chunk streaming under controlled demand. WebTestClient preserves deterministic REQ-FS-003/004 XML failures through a bounded, non-retaining integrity preflight before opening a separate response stream; it does not claim first-byte latency, single-pass filesystem I/O, or active per-chunk demand control. |
| REQ-PIPELINE-004 | Failure propagation, cleanup, later stages stopped | Pipeline-unit + supported WebTestClient example | ⚠️ Partial | Pipeline-unit covers chunk fault, manifest fault, and upstream-body failure; WebTestClient covers the chunk-persistence fault. |
| REQ-PIPELINE-005 | Cancellation event and cleanup | Pipeline-unit + WebTestClient-runner loopback HTTP Cucumber | ✅ Implemented-and-validated | Both modes cancel after at least two unpublished chunks and prove cancellation event propagation, stable upstream demand, DataBuffer release, lifecycle-owned cleanup, no manifest/object/reference, and later 404. |
| REQ-PIPELINE-006 | Instrumentation event metadata/correlation/no payload leakage | Pipeline-unit Cucumber | ✅ Implemented-and-validated for declared mode | Proves payload-free independently attachable stage observers without changing upstream demand. |
| REQ-PIPELINE-007..012 | Static streaming constraints for PutObject, dedup, GetObject, UploadPart/UploadPartCopy, and part storage | Cucumber static architecture specs + `ReactiveUploadStreamingArchitectureTest` for 007/008 | ✅ Implemented-and-validated | PutObject uses a single-pass tee, dedup emits bounded windows, GetObject streams non-range and range responses, UploadPart/UploadPartCopy stream DataBuffers into part storage, and part files are written/read with DataBufferUtils streaming APIs. |
| REQ-PIPELINE-013 | Finite runtime demand at existing S3 streaming boundaries | Runtime-backpressure Cucumber runner | ✅ Implemented-and-validated for adapter-boundary scope | Twelve ordered 64 KiB buffers traverse object, range, multipart write/copy, and part-file read paths with each observed upstream request signal capped at four buffers; no staged lifecycle/event claim. |
| Phase 3 mode summary | Reactive pipeline requirements | Pipeline-unit/WebTestClient selections (REQ-PIPELINE-001..006); static architecture Cucumber (REQ-PIPELINE-007..012); runtime boundary Cucumber (REQ-PIPELINE-013) | Mixed status; EP-3 remains open | REQ-PIPELINE-003/004 remain partial; the GetObject integrity-preflight latency/single-pass trade-off remains open. No AWS CLI runner validates the staged internal pipeline contract. |

### Phase 5 S3 Semantic Compatibility Requirement Status

Evidence:
- Requirement source: `s3-reactive-api-adapter/src/test/features/requirements/phase-5-s3-semantic-compatibility.feature`.
- WebTestClient runner: `s3-reactive-api-adapter/src/test/java/com/example/magrathea/s3api/cucumber/requirements/Phase5S3SemanticCompatibilityRequirementsCucumberTest.java`.
- Step glue: `s3-reactive-api-adapter/src/test/java/com/example/magrathea/s3api/cucumber/requirements/Phase5S3SemanticCompatibilitySteps.java`.
- Phase 5 WebTestClient validation passed: 33 discovered, 32 passed, 0 failures, 0 errors, 1 skipped (full-process restart scenario selected by its dedicated runner).
- Full Maven validation passed: `mvn -B test --no-transfer-progress` BUILD SUCCESS, 0 failures/errors.

| Requirement | Scenario area | Validation mode | Status | Notes |
|---|---|---|---|---|
| REQ-S3-001 | PutObject/HeadObject ETag format and consistency | WebTestClient Cucumber | ✅ Implemented-and-validated | RFC-compatible quoted lowercase MD5 ETags are computed and persisted through the S3 object `etag` domain field and repository translators. |
| REQ-S3-002 | Multipart upload/copy, completion and XML error semantics | WebTestClient + AWS CLI subset + full-process restart Cucumber | ✅ Implemented-and-validated for declared multipart semantics | UploadPart returns real part MD5 ETags; CompleteMultipartUpload returns multipart ETags with the `-{partCount}` suffix and readable assembled objects; UploadPartCopy copies source bytes into persisted part bodies; XML error semantics are covered for malformed copy-source, unknown upload ID, malformed complete XML, invalid part references, and abort/complete conflicts. |
| REQ-S3-002-C | Multipart uploaded-part durability probe | WebTestClient plus direct filesystem same-directory repository probe | ⚠️ Implemented-not-e2e-validated | Filesystem-backed multipart upload repository exists for durable multipart state, but this validation is not a full process/Spring restart e2e scenario. |
| REQ-S3-003 | Byte-range GET and unsatisfiable range | WebTestClient Cucumber | ✅ Implemented-and-validated | Range GET returns 206 Partial Content with `Content-Range`; unsatisfiable ranges return 416 `InvalidRange`. |
| REQ-S3-004 | Conditional GET/HEAD headers | WebTestClient Cucumber | ✅ Implemented-and-validated | Covers `If-Match`, `If-None-Match`, `If-Modified-Since`, and `If-Unmodified-Since`. |
| REQ-S3-005 | CopyObject ETag | WebTestClient Cucumber | ✅ Implemented-and-validated | CopyObject returns the destination ETag instead of a placeholder. |
| REQ-S3-006 | Object tagging lifecycle and inline `x-amz-tagging` | WebTestClient Cucumber | ✅ Implemented-and-validated | Object tags persist through the S3 object `objectTags` domain field and object tagging endpoints. |
| REQ-S3-007 | Versioning, object lock, and lifecycle classification | WebTestClient Cucumber | ✅ Validated as not-implemented/config-only classification | Scenarios intentionally document unsupported/config-only behavior. Versioning, object-lock, lifecycle, and replication enforcement are not claimed complete. |
| Phase 5 mode summary | Declared Phase 5 S3 semantic compatibility scope | WebTestClient + AWS CLI subset + full-process restart runner | ✅/⚠️ Honest declared-scope result | Declared semantic scenarios are `@implemented-and-validated` except REQ-S3-002-C, which remains `@implemented-not-e2e-validated`; the Phase 5 AWS CLI runner validates the `@awscli-required` subset. |

Implemented outputs recorded for Phase 5: RFC-compatible quoted lowercase MD5 ETags for PutObject/HeadObject; ETag and object tag persistence in the S3 object domain model and repository translators; Range GET and conditional GET/HEAD behavior; CopyObject destination ETag propagation; real multipart part/final ETags; UploadPartCopy copied-byte final assembly; S3-shaped multipart XML errors; filesystem-backed multipart upload state; and explicit unsupported/config-only S3 responses.

### Phase 6 Distributed Readiness Modeled Domain Requirement Status

Evidence:
- Spec source: `s3-reactive-api-adapter/src/test/features/specs/phase-6-distributed-readiness.feature`.
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
| Single-node backend WebTestClient requirements | ✅ 260 discovered, 234 passed, 26 skipped/excluded | `SingleNodeBackendWebTestClientRequirementsCucumberTest` validates the migrated ADR 0021 single-node backend requirement files through WebTestClient. |
| Single-node backend AWS CLI requirements | ✅ 72 discovered, 26 passed, 46 skipped/excluded | `SingleNodeBackendAwsCliRequirementsCucumberTest` validates the `@awscli-required` subset of the same migrated single-node backend requirement files. |
| Phase 2 WebTestClient specs | ✅ 11 total, 7 passed, 4 `@awscli` examples excluded/skipped | Isolated Phase 2 runner validates REQ-FS-001 through REQ-FS-006 for the WebTestClient-required scope from `specs/phase-2-filesystem-reliability.feature`, including interrupted chunk/manifest write fault injection, corruption detection, recovery scanning, and concurrency scenarios. |
| Phase 2 storage-engine AWS CLI specs | ✅ 11 discovered, 4 passed, 7 skipped/excluded | Dedicated AWS CLI validation passed for the `@awscli-required` Phase 2 examples from `specs/phase-2-filesystem-reliability.feature`: REQ-FS-003, REQ-FS-004, REQ-FS-006 different keys, and REQ-FS-006 same key. |
| Phase 5 AWS CLI requirements | ✅ 33 discovered, 14 passed, 19 skipped/excluded | Phase 5 AWS CLI runner validates the `@awscli-required` subset in `phase-5-s3-semantic-compatibility.feature`; remaining examples are skipped by tag/profile filtering. |
| EP-1 security/identity AWS CLI/e2e requirements | ✅ 13 passed, 0 skipped | `PhaseEp1SecurityIdentityAwsCliCucumberTest` validates REQ-SEC-001..009 against the shared EP-1 Business Need feature with AWS CLI/raw signed HTTP e2e steps and a real RANDOM_PORT server. |
| EP-1 local backing-service specs | ✅ 4 passed, 0 skipped | `Ep1SecurityServicesSpecsCucumberTest` validates REQ-SEC-010..013: encrypted durable access-key storage/revocation, durable policy reload with explicit deny, tamper-evident audit, and durable key-management/SSE material. |
| AWS CLI Cucumber scenarios | ✅ Current dedicated AWS CLI runners green | Single-node backend, Phase 1 upload, Phase 2 filesystem reliability, Phase 5 semantic compatibility, and EP-1 security/identity AWS CLI runners all pass their selected subsets in the latest local gates. |
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

> The detailed module-level table below is retained as a previous Phase 10 breakdown. The current aggregate evidence is the 2026-07-09 full Maven gate summarized above: `mvn -B --no-transfer-progress clean test -o` passed with BUILD SUCCESS across all 13 reactor modules, and Surefire XML reports record **1091 discovered tests/examples, 943 passed/executed, 0 failures, 0 errors, 148 skipped/excluded**, total time 04:42. Some rows below may remain historical snapshots; use the Summary and Current Verified Results sections above for current validation evidence.

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
| s3-reactive-api-adapter | com.example.magrathea.s3api.awscli.SingleNodeBackendAwsCliRequirementsCucumberTest.txt | 72 | 0 | 0 | 46 | ✅ Passed (26 executed) |
| s3-reactive-api-adapter | com.example.magrathea.s3api.cucumber.requirements.SingleNodeBackendWebTestClientRequirementsCucumberTest.txt | 260 | 0 | 0 | 26 | ✅ Passed (234 executed) |
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

Historical Phase 10 quality gates passed with no failures, errors, or skipped tests. The latest supplied full Maven gate for this report, `mvn -B test --no-transfer-progress`, passed with BUILD SUCCESS and 0 failures/errors; the total test count was not restated in the supplied Phase 5 evidence. The Phase 2 WebTestClient skipped/excluded examples are `@awscli` examples covered by the dedicated AWS CLI validation where applicable, not fault-injection validation gaps. Phase 3 reactive pipeline validation is unit/application only and is not yet WebTestClient/AWS CLI/e2e validated. Phase 5 semantic compatibility now has a dedicated AWS CLI runner for its selected subset, and EP-1 security/identity now has WebTestClient plus AWS CLI/e2e evidence for REQ-SEC-001..009.

---

## S3 Semantic Coverage Matrix

> This table classifies each S3 API family by semantic status, not route count.
> **Route mapped** = HTTP route exists; **Stateful behavior** = creates/reads/updates/deletes durable state with observable follow-up; **AWS CLI scenario** = verified via AWS CLI Cucumber or `test-aws-cli.sh`; **Storage-engine scenario** = verified against storage-engine backend; **Semantic status** = overall classification.

### Current Evidence-Based Family Classification

| Family | Operation (examples) | Route mapped | Stateful behavior | AWS CLI scenario | Storage-engine scenario | Semantic status | Notes |
|---|---|---|---|---|---|---|---|
| Object CRUD | PutObject, GetObject, HeadObject, DeleteObject, CopyObject, Range GET, conditional GET/HEAD | Yes | Partial — WebTestClient-validated ETag, range, conditional, copy, and object state semantics for declared Phase 5 scope | Partial pass: existing AWS CLI increment covers put default headers, get content, head, delete, idempotent delete, `STANDARD` storage class via object attributes, and slash-containing object keys; no new Phase 5 AWS CLI runner | Absent | Partial / WebTestClient semantic coverage for declared Phase 5 subset | Phase 5 validates quoted lowercase MD5 ETags, ETag persistence, Range GET 206/416 behavior, conditional GET/HEAD headers, and CopyObject destination ETag through WebTestClient. Storage-engine profile and Phase 5 AWS CLI subsets have focused validation; broader parity remains scoped. |
| Bucket baseline | CreateBucket, HeadBucket, DeleteBucket, ListBuckets, ListObjects, ListObjectsV2 | Yes | Partial — in-memory only | Partial pass: AWS CLI bucket operations increment covers create-bucket, list-buckets, head-bucket, get-bucket-location, get-bucket-versioning, put-bucket-versioning, delete-bucket, duplicate-bucket 409, head-bucket 404, delete-bucket 404, list-objects, and list-objects-v2 | Absent | Stubbed / Partial | Bucket create/head/location/versioning/delete and failure cases now have targeted AWS CLI Cucumber coverage; prefix/delimiter edge cases, continuation tokens, and storage-engine indexes remain unverified |
| Multipart upload | CreateMultipartUpload, UploadPart, UploadPartCopy, CompleteMultipartUpload, AbortMultipartUpload, ListParts, ListMultipartUploads | Yes | Partial — WebTestClient validates real part MD5 ETags, UploadPartCopy copied bytes, final assembly, multipart final ETag suffix, XML error semantics, and filesystem-backed multipart state probe | Partial pass: AWS CLI Cucumber validates the selected multipart lifecycle and UploadPartCopy copied-byte assembly subset | Absent | Partial / WebTestClient semantic coverage for declared Phase 5 subset | UploadPart returns real part MD5 ETags, UploadPartCopy persists copied source bytes, and CompleteMultipartUpload returns `-{partCount}` multipart ETags with readable assembled objects. Multipart restart/durability is implemented-not-e2e-validated for REQ-S3-002-C because that specific check is a same-directory filesystem repository probe; REQ-S3-002-E separately validates part-body completion after a full Spring restart. |
| Bucket configuration | CORS, Lifecycle, Website, Logging, Notification, Replication, Encryption, Versioning, Tagging, etc. | Yes | Partial — config storage or explicit unsupported/config-only classification only | Failing in stale standalone script; no new Phase 5 AWS CLI runner | Absent | Config-only / Stubbed / Not implemented where explicitly declared | Phase 5 validates explicit unsupported/config-only classification for versioning, object lock, and lifecycle. No background job execution or enforcement is claimed. |
| Object metadata/tagging/ACL | PutObjectTagging, GetObjectTagging, GetObjectAttributes, PutObjectAcl, GetObjectAcl, PutBucketTagging, GetBucketTagging, DeleteBucketTagging, PutBucketAcl, GetBucketAcl | Yes | Partial — object tags persist in the S3 object domain model for Phase 5; ACL grant enforcement remains stubbed/partial | Partial pass: AWS CLI third increment covers bucket ACL read/write, object ACL read/write, bucket tagging CRUD, object tagging CRUD, and object attributes (ETag + ObjectSize); no new Phase 5 AWS CLI runner | Absent | Partial / WebTestClient semantic coverage for object tagging | Phase 5 validates object tag persistence through `objectTags`, object tagging endpoints, and inline `x-amz-tagging`. Storage-engine backend remains unverified; ACL grant enforcement remains stubbed. |
| Versioning/delete markers | ListObjectVersions, versioned GET/HEAD/DELETE | Yes | Explicit unsupported/not-implemented classification for Phase 5 scope | Failing in stale standalone script; no new Phase 5 AWS CLI runner | Absent | Not implemented / Stubbed | Phase 5 passing scenarios document unsupported/not-implemented classification. Version IDs, delete markers, latest-version resolution, and enforcement remain unimplemented. |
| Access/security controls | BucketPolicy, PublicAccessBlock, OwnershipControls | Yes | Config storage only | Failing (server not running at test time) | Absent | Config-only / Stubbed | Authorization enforcement absent |
| Analytics/inventory/metrics | Bucket analytics, inventory, metrics, intelligent-tiering | Yes | Config storage only | Failing (server not running at test time) | Absent | Config-only | No background report generation |
| Advanced/specialized | SelectObjectContent, RestoreObject, etc. | Some | None verified | Missing | Absent | Stubbed / Out of scope | Likely out of scope without explicit design |
| Admin/storage-engine APIs | StoragePolicy/device/disk-set catalog reads; policy validation | Yes | Read-only configuration-as-code catalogs; validation is non-persistent | Not applicable | Admin adapter tests pass; selected-backend S3 scenarios still absent | Partial backend Admin API implemented | `/admin/**` is separate from S3 coverage. `mvn -B -pl admin-api-adapter -am test` passes 9 tests. Policy/device/disk-set catalogs are read-only at runtime; create/update/delete policy requests are rejected. |

### AWS CLI Cucumber vs WebTestClient Cucumber Parity

| Dimension | Single-node WebTestClient requirements | Phase 2 WebTestClient specs | AWS CLI Cucumber |
|---|---|---|---|
| Scenarios/examples | 260 discovered: 234 passed, 26 skipped/excluded | 11 total: 7 passed, 4 `@awscli` examples excluded/skipped | Single-node: 72 discovered, 26 passed, 46 skipped/excluded; Phase 2 storage-engine: 11 discovered, 4 passed, 7 skipped/excluded; Phase 5: 33 discovered, 14 passed, 19 skipped/excluded |
| Tag / runner | `@webclient-required` via `SingleNodeBackendWebTestClientRequirementsCucumberTest` | Isolated Phase 2 runner | `@awscli-required` via dedicated AWS CLI runners |
| Driver | Spring WebTestClient | Spring WebTestClient | `aws s3api` CLI |
| Status | Passing for migrated ADR 0021 single-node backend scope | Implemented-and-validated for WebTestClient-required Phase 2 scope, including interrupted-write fault injection, corruption detection, recovery scanning, and concurrency | Selected AWS CLI subsets pass for single-node backend, Phase 1 upload, Phase 2 storage-engine reliability, and Phase 5 semantic compatibility; parity remains scoped by scenario tags and runner filters |
| Parity goal | Shared requirement text remains canonical | Maintain Phase 2 coverage and keep shared requirement text as source of truth | Continue using shared features with runner-specific glue/config/tags rather than duplicating requirements |

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
| Single-node `@webclient-required` scenarios | ✅ 260 discovered, 234 passed, 26 skipped/excluded | Migrated ADR 0021 single-node backend requirements pass via WebTestClient. |
| Phase 2 WebTestClient specs | ✅ 11 total, 7 passed, 4 `@awscli` examples excluded/skipped | REQ-FS-001 through REQ-FS-006 WebTestClient-required scope passes, including interrupted-write fault injection, corruption detection, recovery scanning, and concurrency scenarios. |
| Phase 2 storage-engine AWS CLI specs | ✅ 11 discovered, 4 passed, 7 skipped/excluded | Dedicated Phase 2 AWS CLI validation passes for its `@awscli-required` examples: REQ-FS-003, REQ-FS-004, REQ-FS-006 different keys, and REQ-FS-006 same key. |
| `@awscli-required` scenarios | ✅ Dedicated AWS CLI runners green for selected subsets | Single-node backend (72 discovered / 26 passed / 46 skipped), Phase 1 upload (28 / 9 / 19), Phase 2 filesystem reliability (11 / 4 / 7), and Phase 5 semantic compatibility (25 / 12 / 13) pass their selected AWS CLI subsets in the latest full Maven gate. |
| `test-aws-cli.sh` standalone | ⚠️ 46/82 passed | Independent script; most failures are connection errors (server not started at test time). |

## Roadmap

Operations not yet semantically implemented are tracked in `PLAN.md` under the *S3 API Semantic Completion Plan* (S3-P0 through S3-P4).
