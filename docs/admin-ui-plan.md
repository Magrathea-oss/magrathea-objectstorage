# Admin UI Plan — Storage Policies, Devices, Disk Sets, and Backend Status

> **Status: planning only.** This document is the Phase 8 Admin UI screen plan required by
> `PLAN.md`. No `magrathea-ui` implementation work is authorized by this document. Frontend
> implementation requires an appropriate frontend workflow/agent to be assigned before any
> change to `magrathea-ui/**`, per the project workflow ownership rules.

## Scope and boundaries

- The Admin UI covers only non-S3 storage-engine concepts: storage policies, storage devices,
  disk sets/topology, backend status, and validation/reporting.
- The Admin UI must **not** duplicate S3 object semantics (upload, read, delete, list, multipart,
  metadata, tagging, ACL, bucket operations). The S3 REST API remains the only object API.
- All screens are backed exclusively by the existing `/admin/**` JSON API from
  `admin-api-adapter`. No new parallel external storage-engine object endpoints are planned.
- Runtime mutability decision (unchanged): storage policies, devices, and disk sets are YAML-backed
  configuration-as-code. The UI is therefore **read-only** for catalogs, plus a **non-persistent**
  policy validation screen. Mutation screens are explicitly out of scope until the runtime
  mutability decision changes.

## Planned screens

| # | Screen | Backing endpoint(s) | Behavior |
|---|---|---|---|
| 1 | Storage policy list | `GET /admin/storage-policies` | Read-only table: policy ID, storage class ID, dedup on/off, EC data/parity, replication factor, compression, encryption. |
| 2 | Storage policy detail | `GET /admin/storage-policies/{id}` | Read-only detail view of one policy, including `DedupConfig` chunk settings (chunk size, alignment, scope, fingerprint algorithm) and the fixed 6-step pipeline flags. Shows `MINIO_STANDARD` as the canonical example. |
| 3 | Policy validation / report | `POST /admin/storage-policies/validate` | Paste/upload a JSON policy payload; render structured validation results. Clearly labeled non-persistent: nothing is written to YAML or runtime state. |
| 4 | Storage device list | `GET /admin/storage-devices` | Read-only table: device ID, type (bucket/dedup), paths, capacity, health metadata, failure domain. |
| 5 | Storage device detail | `GET /admin/storage-devices/{id}` | Read-only detail of one device including topology references. |
| 6 | Disk set / topology overview | `GET /admin/disk-sets` (+ detail) | Read-only topology view: disk sets, member devices, failure domains, placement groups. |
| 7 | Backend / status dashboard | `GET /admin/health` today; planned richer backend-status contract (below) | Shows admin API health and, once implemented, the selected object-store backend and storage-engine runtime status. |

Every catalog screen must display a visible "configuration-as-code / read-only at runtime" banner
and document that changes require YAML edits plus redeploy/reload, matching the documented
`405 Method Not Allowed` behavior of mutation endpoints.

## Planned backend/status contract (beyond `/admin/health`)

Planned, not implemented. Implementation is gated on runtime backend evidence and stays owned by
the Java workflow (`admin-api-adapter` + storage-engine modules):

- `GET /admin/backend-status` returning:
  - selected backend (`single-node` in-memory vs `storage-engine`) and the profile/property that selected it;
  - storage-engine catalog summary (policies/devices/disk sets loaded, source directories);
  - filesystem storage root(s) and availability;
  - recovery scanner summary (last scan findings/quarantine counts) when the storage-engine backend is active.
- Read-only; no mutation semantics; tagged `@backend-status` in requirement features when specified.

## Requirement and validation plan

- Before implementation, shared Gherkin requirement features must be written/refreshed first
  (requirement-first policy): `Business Need` scenarios for user-visible admin product behavior,
  `Ability` scenarios for internal/operational support, tagged `@admin-api` plus `@storage-policy`,
  `@storage-device`, `@disk-set`, or `@backend-status` (never `@s3-api`).
- Catalog read-only behavior, validation-endpoint behavior, and mutation rejection are already
  covered by admin API tests; UI-level validation mode will be defined with the frontend workflow.
- Status tags must follow the evidence policy: screens are `@absent` until implemented; the
  backend-status contract is `@absent` (planned) today.

## Handoff prerequisites (blocking)

1. Assign a frontend workflow/agent with ownership of `magrathea-ui/**`.
2. Confirm the Docker-driven frontend/docs regeneration path remains the canonical build
   (no host-generated static assets committed).
3. Write/refresh the admin UI requirement features before implementation begins.
