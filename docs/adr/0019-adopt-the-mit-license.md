# ADR 0019 — Adopt the MIT license

## Context

KA-1 in `PLAN.md` (Positioning & Licensing) requires a license decision as the first, lowest-cost step of the Killer-App Track. The project's killer-app goal is adoption-focused: Magrathea targets enterprise, single-user, and government/military audiences as an S3-compatible object store.

Two factual market observations shaped the decision:

- Some prominent S3-compatible alternatives are licensed under the AGPL, which creates adoption friction for some organizations whose legal or procurement processes restrict or slow the use of strong-copyleft software. This is stated as a factual market observation, not as a judgment of those licenses or projects.
- Government and military audiences in particular need permissive, easily reviewable licensing to support self-contained, air-gapped, and legacy-integration deployments without license-compliance overhead.

Section G of `PLAN.md` had recorded the repository-root `LICENSE` file as missing (verified 2026-07-02), so the licensing decision also closes a documentation-integrity gap.

## Decision

Magrathea Object Storage adopts the MIT License. This is an owner decision made on 2026-07-02.

- The standard MIT License text is placed at the repository root as `LICENSE`, with the copyright line "Copyright (c) 2026 Magrathea ObjectStore contributors".
- The MIT license applies to the whole repository unless a file explicitly states otherwise.

## Consequences

- **Maximal adoption and embedding freedom.** Users may use, modify, embed, and redistribute Magrathea, including in proprietary and air-gapped environments, with minimal obligations. This directly supports the adoption-focused killer-app goal and the government/military positioning.
- **No copyleft protection.** Third parties may build closed-source derivatives without contributing changes back. This trade-off is explicitly accepted.
- **Dependency license compatibility must be tracked.** Third-party dependency licenses must remain compatible with MIT distribution; this will be tracked by the EP-8 license compliance report.
- **All future contributions are made under the MIT license.**

## Status

Accepted (owner decision, 2026-07-02).
