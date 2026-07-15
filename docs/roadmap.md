# Magrathea ObjectStore — Public Roadmap

> **Status:** Derived from [`PLAN.md`](../PLAN.md) and reconciled on 2026-07-14. Completion claims are limited to the declared and validated scope; this roadmap is not a promise of delivery dates. **EP-10 remains the active implementation phase.** ARC42 owns architecture risks and deferred quality-review items; those items do not replace the EP sequence.

---

## Phase 1 — Foundation (Prototype Quality)

| Area | Status | Key deliverables |
|---|---|---|
| S3 API surface | ⚠️ 108/111 operations mapped; 21 carry explicit validated evidence | Generated semantic coverage distinguishes mapped routes from implemented behavior; route presence does not imply compatibility |
| Object CRUD (Put/Get/Head/Delete) | ✅ Stateful + WebTestClient-validated | Read-after-write, slash keys, metadata, ETag, CopyObject |
| Bucket lifecycle | ✅ Stateful + WebTestClient-validated | Create/Head/Delete/List, ListObjects V1/V2 |
| Multipart upload lifecycle | ✅ Stateful and restart-safe for the declared scope | Initiate, UploadPart, UploadPartCopy, ListParts, completion/abort, persisted part bodies, ordered assembly, ETags, XML errors, and full-process restart completion |
| Upload durability & restart safety | ✅ `@implemented-and-validated` | REQ-UPLOAD-001/002/004/005/006 — object bytes, manifests, and object references survive restart |
| Filesystem reliability | ✅ `@implemented-and-validated` | Atomic publication, SHA-256 checksums, recovery scanner, SHA-256 sidecar/trailer checksums |
| Storage-engine backend | ✅ Wired with declared metadata durability | Object bytes, manifests, references, bucket registry/configuration, multipart state, object tags/ACLs, and per-object configuration survive the validated restart modes |
| Admin API (read-only catalogs) | ✅ Delivered | Storage policies, devices, disk sets — YAML-backed, read-only, non-persistent validation |
| MIT License | ✅ Delivered | [`LICENSE`](../LICENSE), ADR 0019 |

---

## Phase 2 — Enterprise Production Readiness (EP-0 .. EP-11)

| Phase | Priority | Status | Description |
|---|---|---|---|
| **EP-2** Complete Metadata Durability | Blocker | `@implemented-and-validated` | Declared storage-engine metadata families survive restart; handler-local configuration state moved behind durable repositories |
| **EP-3** Reactive Streaming Completion | Blocker | `@implemented-and-validated` for its declared local storage-engine pipeline scope | Bounded single-pass upload/read, multipart assembly, conditional chunking, whole-object units, physical EC shards, schema-3 reconstruction facts, and output-only bounded EC decoding are validated. Focused `REQ-PIPELINE-017` passes **5 scenarios / 46 steps**, including all **15 four-of-six** survivor combinations. No repair publication, daemon, distributed EC, rebalance, cleanup, or chaos claim follows. |
| **EP-1** Security & Identity | Blocker | `@implemented-and-validated` for built-in scope | SigV4, deny-by-default authorization, durable audit, SSE-S3, credentials, policy and key services |
| **EP-4** Space Management & Data Hygiene | High | `@implemented-and-validated` for single-node scope | Typed GC, dedup reachability, quotas, ENOSPC behavior, and transform-aware periodic scrubbing |
| **EP-5** Operability & Delivery | High | `@implemented-and-validated` for `0.1.0` preview | Full CI gate, SemVer/OCI release workflow, persistent-volume replacement, backup/DR, schema compatibility, SLOs, probes, and graceful shutdown. JVM/native Docker build contexts now mirror all 17 reactor modules, including the EP-10 cluster modules; the JVM image build and runtime smoke pass, while full native-image recompilation was not rerun for the 2026-07-14 context-only correction. Bare and packaged single-node launches now share Storage Engine as the validated default (`REQ-PKG-005`). |
| **EP-6** Performance & Capacity Validation | High | `@implemented-and-validated` for single-node envelope | Enforced object/admission/timeout/connection limits, deterministic 45-second CI load, 15-minute soak, bounded-memory and reproducible result manifests |
| **EP-7** Complete Admin Panel | High | `@partial` overall; `REQ-ADMIN-001..037` complete | Product Shell, Object Storage extension/application, task-grouped navigation, priority dashboard, progressive disclosure, operation feedback, appearance support, truthful unavailable states, accessibility, visual regression, and packaging are validated. Credential/tenant administration and real recovery/GC/scrub/audit/metrics/traces providers remain absent; unavailable providers truthfully return 503 not-configured. |
| **EP-8** Cluster Architecture ADR & Supply Chain | Medium | `@implemented-and-validated` for architecture/evidence wiring only | ADR 0027 accepted target architecture; clean-revision CycloneDX application/image SBOMs, truthful license inventory, fail-closed OWASP monitoring evidence, hardened single-node runtime proof, and CI artifact retention validated. No publication or clustered-deployment hardening claim. |
| **EP-10** S3 Cluster (Multi-Node) | High | `@partial`; fixed baseline + bounded repair/discovery + architecture contract validated | Fixed A/B/C implements whole-object `N=3/W=2` publication/failover/restart, consensus-owned current-generation repair, and bounded current-reference discovery (`001..005`, `008..014`, `019..027`, with `014` architecture-only). Local `REQ-PIPELINE-017` decoding is complete but is not cluster evidence. `REQ-CLUSTER-015` distributed shard placement/transfer remains not implemented, broad cluster `017` remains partial, and `006/007/016/018` also remain not implemented. Next: distributed EC transfer, bounded committed-shard fault tooling, EC self-healing, shard rebalance, then fenced cleanup; generalized chaos remains final. Whole-object replication remains valid below the arranged EC minimum; inter-cluster/NAS-backed replication is later and NAS-dependent. |
| **EP-9** WebDAV API Adapter | Future | `@absent` | Optional WebDAV protocol adapter; delegates to S3 services (INV-4) |
| **EP-11** SMB Gateway (Samba VFS) | Future | `@absent` | Optional Samba VFS C module; maps file ops to S3 semantics (INV-4) |

**Enterprise readiness exit criteria:**

- [x] EP-1: SigV4 authentication, deny-by-default authorization, audit logging, and real SSE validated for built-in scope
- [x] EP-2: Declared metadata families survive restart in storage-engine mode
- [x] EP-3: Declared storage-engine GetObject and multipart paths stream with bounded memory; later static review items do not interrupt active EP-10 work or revoke the validated scope
- [x] EP-4: Typed GC, dedup reachability, quotas, ENOSPC, and scrubbing validated for single-node scope
- [x] EP-5: `0.1.0` preview CI/delivery gates, backup/restore, probes, shutdown, and persistent-volume replacement validated
- [x] EP-6: Single-node load/soak limits documented and reproducibly validated under `-Xmx256m` (non-benchmark scope)
- [ ] EP-7: Complete authoritative scope delivered and validated — `REQ-ADMIN-001..037` is complete, but credential/tenant administration and real operational report providers remain
- [x] EP-8: ADR 0027 accepted as target architecture; supply-chain evidence/CI wiring validated without publication. OWASP remains unknown/error.
- [ ] EP-10: Overall cluster capability complete — the Java 21 shared real-process gate passes `REQ-CLUSTER-001..005/019/020` (14 scenarios / 188 steps; repair-only `019/020`: 4 / 80), focused `008..013` passes, focused `024` passes 7 / 168, focused `027` passes 2 / 36, and `014` passes 1 / 17 only in its internal source/build mode. Local prerequisite `ReqPipeline017EcReconstructionCucumberTest` separately passes **5 / 46** with all **15 four-of-six** combinations; it does not upgrade cluster status. Exact current status: `008..014` and `019..027` are implemented-and-validated within their bounded modes; broad cluster `017` remains partial; `006/007/015/016/018` remain not implemented. CI wiring for focused evidence has not yet run. The wider cluster scope remains open.
- [ ] EP-9 (optional): WebDAV adapter, if built, meets binding constraints
- [ ] EP-11 (optional): SMB gateway, if built, meets binding constraints

### Active Execution Focus — EP-10 Completion

With local bounded reconstruction complete under ADR 0032, the active EP-10 sequence is:

| Order | EP-10 continuation | Current status / next gate |
|---|---|---|
| **1** | Distributed shard placement and direct transfer (`REQ-CLUSTER-015`) | Next requirement-first slice; remains `@not-implemented`. |
| **2** | Early bounded ADR 0030 kernel + committed-shard fault subset | Planned no-op-default deterministic test tooling with dual non-production activation, exact committed targets, finite blast radius, quarantine/restoration, and redacted evidence; no implementation claim. |
| **3** | EC monitoring and self-healing | Planned process-local detection plus consensus-owned repair jobs/claims/retries/fencing/results and verified atomic shard publication. |
| **4** | Shard rebalance | Separate placement-changing requirement-first slice; broad `REQ-CLUSTER-017` remains partial. |
| **5** | Fenced orphan cleanup | Separate destructive requirement-first slice with reachability, retention, restart, and deletion fences. |
| **Final** | Generalized ADR 0030 chaos | Broader deterministic and realistic chaos remains final after normal-path semantic/resource closure; the early subset is not generalized chaos. |

---

## Phase 3 — Killer-App Track (KA-1 .. KA-6)

| Phase | Start condition | Status | Description |
|---|---|---|---|
| **KA-1** Positioning & Licensing | Immediate | `@implemented-and-validated` for the declared documentation/governance scope |
| **KA-2** Ecosystem Conformance | After minimal EP-1 (SigV4) | `@absent` | s3-tests gate, SDK/tool matrix, full checksum matrix |
| **KA-3** Data-Lake Readiness & S3 Tables | After EP-1 + EP-3 | `@absent` | Conditional PUT, presigned URLs, Iceberg/Spark workloads; separate S3 Tables adapter + table-store BC |
| **KA-4** Eventing, Tiering, Multi-Site, Federation | After EP-1/EP-2/EP-4; multi-site after EP-10 and suitable NAS infrastructure | `@absent` | Notification delivery, lifecycle execution, cold-tier, and later inter-cluster/NAS-backed replication; OIDC + LDAP + Kerberos federation |
| **KA-5** Distribution | After EP-5 (single-binary ADR may start earlier) | `@partial`; native/Alpine and JVM container slices validated | JVM-free GraalVM native image and canonical JVM container paths exist; broader air-gap bundle, dashboards, own CLI, and Helm/operator work remain backlog |
| **KA-6** Public Proof | Last; after EP-6 + KA-2 | `@absent` | Reproducible benchmarks vs MinIO/Garage/SeaweedFS; published s3-tests pass-rate matrix |

**Killer-app exit criteria:**

- [x] KA-1: MIT LICENSE, licensing ADR, factual positioning, and public roadmap published
- [ ] KA-2: s3-tests pass-rate published with honest known-failures
- [ ] KA-3: Iceberg/Spark validated (conditional PUT + presigned URLs working)
- [ ] KA-3: S3 Tables adapter + table-store BC delivering namespaces/tables
- [ ] KA-4: Notification delivery (built-in backend) + lifecycle execution + multi-site validated
- [ ] KA-4: OIDC + LDAP + Kerberos federation validated incl. at least one legacy-path e2e
- [ ] KA-5: Single-binary distribution shipped and air-gap installable
- [ ] KA-6: Public reproducible benchmarks published

---

## How to Read This Roadmap

- **Architecture authority:** ARC42 is the central integrated architecture and mutable risk/status source. This public roadmap schedules/summarizes work and must not broaden a status beyond the linked ARC42 scope.
- **Status tags** follow the AGENTS.md convention: `@implemented-and-validated`, `@implemented-not-e2e-validated`, `@partial`, `@config-only`, `@placeholder`, `@absent`, `@not-implemented`.
- No capability is marked done until the production code implements promised behavior and semantic tests validate observable outcomes.
- Route counts, handler names, and README claims are not completion evidence — only passing requirement scenarios count.
- The authoritative evidence table lives in [`PLAN.md`](../PLAN.md) (Baseline Evidence section). This roadmap is a planning artifact, not a status report.
- EP phases must reach `@implemented-and-validated` before the corresponding enterprise readiness exit criteria are considered met.
- KA phases depend on EP prerequisites and never bypass INV-1..INV-5 or the evidence discipline.
