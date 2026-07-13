# C4 model workflow

The canonical C4 workspace for this project is:

```text
docs/c4/workspace.dsl
```

## Current frontend modeling boundary

The C2 model includes the independently built **Object Storage Admin Browser Application** as a browser-runtime container. Its C3 view models the deployable product application, product-neutral Product Shell library, Object Storage Product Extension library, application-owned browser adapters, separate typed Admin API and optional S3 HeadObject clients, and application-owned documentation viewer.

The model does not represent Product Shell or a product extension as separately running C4 containers: they are build-time package boundaries that execute as components in the browser application. It also does not invent operational report providers. The current Admin Control Plane returns explicit provider-not-configured responses when no real recovery, garbage-collection, scrub, audit, metrics, or trace provider is present.

## EP-10 bounded first-cluster implementation baseline

ADRs 0027 and 0028 define the original **implemented-and-validated bounded first slice**. Accepted ADR 0029 records the current **implementation-informed bounded repair** of current-generation replicas, with the conservative requirement statuses below. This remains fixed-A/B/C evidence, not EP-10 completion or a distributed production-readiness claim.

C2 models one Cluster Node Runtime instantiated as fixed co-located voter/storage nodes A, B, and C. Each node JVM owns one Ratis voter, one independent replica data server, one request-facing repair coordinator, one process-local bounded scheduler, and one fenced worker. `ClusterNodeRuntime` in `bootstrap-application` is the SmartLifecycle composition authority for those process-local parts; Ratis, not the lifecycle or wake signal, is the durable repair authority. The model preserves these implementation-informed constraints:

- The existing object-store-to-storage-engine adapter remains the only S3 integration boundary; no cluster module exposes a second object API.
- The cluster slice is limited to unconditional `CreateBucket`, single-part whole-object `PUT`, and whole-object `GET`.
- Consensus orders bucket and object-reference generations; object bytes stay out of the Ratis log.
- Direct whole-object replication uses fixed `N=3, W=2`; insufficient data acknowledgements or control quorum fails the write, with no degraded mode.
- Stable node UUIDs are recovered from per-node identity roots. Identity, Ratis, object, temporary, and runtime roots remain independent and survive complete process restart.
- PA-6 placement remains pure inside `storage-engine-domain`, without Ratis, gRPC, protobuf, Spring, filesystem, certificate, or lifecycle types.
- Fixed A/B/C bootstrap is a first-slice constraint, not dynamic membership.
- Internal Ratis control and replica-data connections use mutual TLS bound to stable node identities. Ephemeral test-local CA/certificate/trust fixtures exercise that path only and are not production PKI evidence.
- The failover runtime stops coordinator A after a successful write, then requires exact-byte `GET` through B while B and C retain quorum.
- The restart runtime stops all A/B/C JVMs, restarts them from non-empty roots, recovers identities and committed generations, and verifies the exact object through B.

The implemented baseline diagrams are:

- `ClusterNodeComponents` — complete bounded runtime decomposition, including lifecycle composition and repair components;
- `ClusterCreateBucketRuntime` — consensus bucket-generation publication;
- `ClusterWriteRuntime` — direct whole-object replication followed by consensus reference publication;
- `ClusterFailoverReadRuntime` — exact-byte read after coordinator A stops;
- `ClusterCompleteRestartRuntime` — stable-identity and persisted-state recovery;
- `FixedThreeNodeRuntimeDeployment` — co-located voter, data server, repair coordinator, scheduler, and worker in each JVM with separate roots; and
- `FixedThreeNodeAcceptanceDeployment` — validated real-child-process topology with test-local mTLS fixtures.

## EP-10 bounded current-generation repair slice

The implemented repair slice applies only to a missing or corrupt `WHOLE_OBJECT` replica already named by the current consensus-committed reference on fixed A/B/C. Ratis snapshot version 2 persists canonical repair jobs, lifecycle, attempts, process-session claims, monotonic claim generations, retry state, histories, and command-deduplication results; loading version 1 initializes no invented jobs. Payload bytes and temporary files remain outside Ratis.

The implementation-informed repair diagrams are:

- `RepairComponents` — the Storage Engine ACL, `ClusterRepairCoordinator`, `ClusterRepairScheduler`, `ClusterRepairWorker`, Ratis control infrastructure, direct replica infrastructure, and `ClusterNodeRuntime` lifecycle authority;
- `MissingReplicaRepairRuntime` — known local absence permits durable ensure/deduplication and an inline fenced claim, followed by a completely verified direct transfer, durable publication, and one local filesystem open for the response;
- `CorruptReplicaRepairRuntime` — a present local artifact is opened once and checked incrementally during the response pass. The reader retains only the pending final frame, not a complete preflight copy. Corruption fails that request without a second full read or transparent alternate-replica retry; a committed repair job may make only a later GET succeed;
- `RepairRestartRetryRuntime` — versioned snapshot/log recovery, periodic committed-state rediscovery through leader changes, expired-claim reclaim with a newer generation, already-valid-target idempotence, and stale-token rejection; and
- `FixedThreeNodeRuntimeDeployment` — the implemented scheduler and worker remain co-located in each existing JVM while Ratis carries job metadata and direct mTLS gRPC carries whole-object bytes.

`ClusterNodeRuntime` starts the scheduler only after its voter and replica server and closes the scheduler before transport shutdown. The scheduler's immediate wake contains no job data, and its 250 ms scan queries only committed locally targeted `READY`, due `RETRY_WAIT`, and expired `CLAIMED` work, at most 16 jobs sequentially. It is repair-job reconciliation, not broad periodic anti-entropy discovery.

Status remains intentionally bounded:

- `REQ-CLUSTER-019`, `REQ-CLUSTER-020`, `REQ-CLUSTER-021`, `REQ-CLUSTER-022`, `REQ-CLUSTER-023`, `REQ-CLUSTER-025`, and `REQ-CLUSTER-026` are modeled as implemented and semantically validated within their declared scopes.
- `REQ-CLUSTER-024` remains **partial** because the complete seven interruption-point matrix did not execute real filesystem and gRPC repair side effects at every crash point.
- Broad `REQ-CLUSTER-017` remains **partial** because broad or periodic anti-entropy discovery, rebalance execution, and automated orphan cleanup remain absent.

A separate `FutureClusterCapabilities` diagram keeps excluded scope visibly distinct: prepared-artifact intents, automated orphan cleanup, rebalance, broad or periodic anti-entropy, clustered multipart, conditional/versioned and chunked writes, erasure-coded transfer/reconstruction, dynamic membership and certificate lifecycle, and broader partition handling. Existing single-node features, PA-6 planning models, and the bounded repair scheduler are not evidence for those capabilities.

None of these diagrams claims production PKI, dynamic membership, rolling upgrades, the full crash matrix, general partition tolerance, two-node-loss tolerance, broader S3 cluster support, or distributed production readiness.

This project uses **Structurizr local** through the official `docker.io/structurizr/structurizr` container image, following the current Structurizr local workflow. The helper scripts are compatible with both **Podman** and **Docker**; Podman is preferred when both are available.

<https://docs.structurizr.com/local/quickstart>

## Start Structurizr local

Preferred command:

```bash
~/.pi/agent/skills/multi-agent/tools/c4model-local.sh
```

The script auto-detects the container runtime in this order:

1. `CONTAINER_RUNTIME`, if explicitly set
2. `podman`
3. `docker`

The default image is `docker.io/structurizr/structurizr` so Podman does not depend on short-name registry resolution. Override it with `STRUCTURIZR_IMAGE` if needed.

Examples:

```bash
# auto-detect podman/docker
~/.pi/agent/skills/multi-agent/tools/c4model-local.sh

# force Podman
CONTAINER_RUNTIME=podman ~/.pi/agent/skills/multi-agent/tools/c4model-local.sh

# force Docker
CONTAINER_RUNTIME=docker ~/.pi/agent/skills/multi-agent/tools/c4model-local.sh
```

The scripts run the container as the current host user. With Podman they also add `--userns=keep-id`, so Structurizr can write `workspace.json` and static exports without changing ownership of files under `docs/c4`.

Equivalent Docker Compose command, for Docker-based environments:

```bash
docker compose up structurizr-local
```

If you use Podman Compose externally, the same `structurizr-local` service definition can be used.

Open:

```text
http://localhost:9090
```

Port `9090` is used intentionally because the application uses port `8080`.

## Edit workflow

1. Edit `docs/c4/workspace.dsl`.
2. Keep Structurizr local running.
3. Refresh/open the browser at `http://localhost:9090`.
4. Use the Structurizr diagram editor for manual layout changes.
5. Structurizr local auto-saves layout into `docs/c4/workspace.json`.

When layout changes are intentional, commit both:

```text
docs/c4/workspace.dsl
docs/c4/workspace.json
```

If `workspace.json` does not exist yet, Structurizr local can still render directly from `workspace.dsl`; layout will be automatic until a manual layout is saved.

## Validation

Use the Structurizr vNext command syntax through the `docker.io/structurizr/structurizr` container image. The script works with Podman and Docker using the same runtime auto-detection described above:

```bash
~/.pi/agent/skills/multi-agent/tools/c4model-validate.sh
```

Do **not** use the old local `structurizr-cli` syntax (`-w`, DOT export, Graphviz PNG rendering) as the project workflow.

## Export workflow

The project uses the official Structurizr `export` command semantics:

<https://docs.structurizr.com/export>

Generic export:

```bash
~/.pi/agent/skills/multi-agent/tools/c4model-export.sh static
~/.pi/agent/skills/multi-agent/tools/c4model-export.sh mermaid
~/.pi/agent/skills/multi-agent/tools/c4model-export.sh plantuml/c4plantuml      # reference only — NOT for PNG generation
~/.pi/agent/skills/multi-agent/tools/c4model-export.sh json
```

> ⚠️ **Warning:** PlantUML export (`.puml`) is for reference/documentation only.
> PNGs MUST be generated via the Structurizr browser-based renderer
> (`c4model-export-images.sh png workspace`), NOT by rendering PlantUML to PNG.

Convenience static-site command:

```bash
~/.pi/agent/skills/multi-agent/tools/c4model-export-static.sh
```

The export script uses `workspace.json` when present, because it contains curated diagram layout. Otherwise it falls back to `workspace.dsl`.

Default outputs:

```text
static                  -> docs/c4/export/static/
plantuml/c4plantuml    -> docs/c4/export/plantuml-c4plantuml/  (reference only)
mermaid                 -> docs/c4/export/mermaid/
json                    -> docs/c4/export/json/
png/svg                 -> docs/c4/images/  (canonical — Structurizr browser renderer)
```

Override the output with:

```bash
STRUCTURIZR_EXPORT_OUTPUT=some/path ~/.pi/agent/skills/multi-agent/tools/c4model-export.sh mermaid
```

## Build Structurizr from source for PNG/SVG export

PNG/SVG export requires the Structurizr browser-based renderer. If you don't already have a compatible preview/source WAR, build Structurizr from source:

```bash
~/.pi/agent/skills/multi-agent/tools/c4model-build-structurizr.sh
```

This clones `https://github.com/structurizr/structurizr.git` under `.cache/structurizr-source`, runs the Maven build, and copies the generated WAR to:

```text
.cache/structurizr/structurizr.war
```

`.cache/` is ignored by Git. `~/.pi/agent/skills/multi-agent/tools/c4model-export-images.sh` automatically uses this WAR when present. You can override it with `STRUCTURIZR_WAR=/path/to/structurizr.war`.

## PNG/SVG export policy

PNG/SVG must be generated directly by Structurizr's browser-based renderer, not through PlantUML, DOT, Graphviz, or Mermaid:

```bash
# preferred: export from the running Structurizr local diagrams page
~/.pi/agent/skills/multi-agent/tools/c4model-export-images.sh png url
~/.pi/agent/skills/multi-agent/tools/c4model-export-images.sh svg url

# alternative: export directly from workspace.dsl/workspace.json
~/.pi/agent/skills/multi-agent/tools/c4model-export-images.sh png workspace
~/.pi/agent/skills/multi-agent/tools/c4model-export-images.sh svg workspace
```

This follows the official PNG/SVG documentation:

<https://docs.structurizr.com/export/png-and-svg>

The script invokes one of these supported forms:

```text
export -format png|svg -url http://localhost:9090/workspace/1/diagrams -output docs/c4/images
export -format png|svg -workspace docs/c4/workspace.dsl -output docs/c4/images
```

Use `url` mode when Structurizr local is running and you want to export the rendered diagrams from the local instance. Use `workspace` mode for direct file-based export. If `workspace.json` exists, it is preferred over `workspace.dsl` to preserve manual layout.

The official documentation notes that PNG/SVG export currently requires building Structurizr from source or using the preview Java WAR; it is not available in every distribution. The prebuilt `docker.io/structurizr/structurizr` image currently returns `Exporting to PNG/SVG is not supported in this build`.

Therefore:

- the approved PNG/SVG path is still Structurizr `export -format png|svg`;
- agents must not regenerate PNGs via DOT/Graphviz, PlantUML, Mermaid, or other substitute renderers;
- if the installed Structurizr distribution does not support PNG/SVG, stop and report that a supported preview/source build is required.

## Agent rules for C4 model changes

When an agent is asked to work on the C4 model:

1. Treat `docs/c4/workspace.dsl` as the source of truth.
2. Use `~/.pi/agent/skills/multi-agent/tools/c4model-validate.sh` for validation.
3. Use `~/.pi/agent/skills/multi-agent/tools/c4model-local.sh` for visual review; it supports Podman and Docker. Docker users may alternatively use `docker compose up structurizr-local`.
4. Preserve `workspace.json` if present; it contains manual layout.
5. Do not introduce alternate diagram generation workflows unless explicitly requested.
6. Do not overwrite PNG documentation artifacts unless using the approved Structurizr PNG/SVG export command.
