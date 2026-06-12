# ADR 0017 — Course correction: broaden project-wide correction plan beyond storage-engine notes

## Context

`PLAN.md` was updated after ADR 0016, but the user observed that it still mostly reflects their notes instead of the complete project audit. The plan therefore does not yet provide a comprehensive project-wide correction roadmap.

## Derailment

Broader findings from the complete audit were not sufficiently promoted into the plan, including:

- Build and module issues.
- Layering inversion.
- Generated artifacts tracked in source.
- Admin API and documentation contradiction.
- Stale coverage and test documentation.
- Storage-engine test gaps.
- Application/infrastructure dependency inversion.
- Incomplete S3 runtime semantics.
- AWS CLI Cucumber parity gaps.
- C4 and ARC42 staleness.
- Frontend and build packaging risks.
- Docker and runtime risks.
- Missing links and files.
- JaCoCo-vs-Clover documentation mismatch.

## Required Change

`PLAN.md` must be revised to include a comprehensive project-wide correction roadmap covering all audit categories, not only storage policy, storage device, and `MINIO_STANDARD` work. The next planning update must explicitly address scaffolding/build, domain, infrastructure/application boundaries, testing, documentation, UI/frontend packaging, runtime/Docker, and documentation-link consistency.

## Consequences

- Planning scope expands beyond the storage-engine correction into scaffolding/build, domain, infrastructure/application, tests, documentation, UI/frontend packaging, and runtime concerns.
- The correction roadmap must promote complete-audit findings into first-class plan items instead of leaving them as implicit background notes.
- Implementation must remain delegated according to agent ownership; this ADR authorizes planning correction only and does not authorize source-code changes.
- Future plan updates must reconcile coverage documentation around JaCoCo as the current coverage mechanism and Clover as optional or legacy unless separately revived.

## Status

Accepted

## Date

2026-06-12
