# ADR 0020 — Requirements vs Specs folder separation in Cucumber features

## Context

The project uses Cucumber scenarios as the single source of truth for both externally visible S3 API behavior and internal implementation guarantees. Currently, all `.feature` files live under `s3-reactive-api-adapter/src/test/features/requirements/`, mixing two fundamentally different stakeholder audiences:

- **Business Need / external requirements**: read by product owners, S3 API consumers, and documentation readers. They describe observable behavior through S3-compatible endpoints.
- **Ability / internal implementation specs**: read by developers, maintainers, and code reviewers. They describe mechanisms such as atomic writes, lock-striped concurrency, crash-safety, YAML catalog parsing, observability contracts, and structural coding standards.

Mixing these in a single folder creates three problems:

1. A product owner asked to review "requirements" sees scenarios about `java.util.concurrent.locks.ReentrantLock` stripes and YAML property parsing — noise that doesn't belong in their stakeholder view.
2. A developer looking for internal mechanism documentation has to filter through S3 API scenarios to find the specs they care about.
3. The generated ARC42 appendix (Gherkin requirements appendix) cannot easily group scenarios by stakeholder audience, reducing its value as a navigation tool.

AGENTS.md §B.2 already distinguishes `Business Need` and `Ability` as Gherkin classification keywords, but does not prescribe a folder structure for that separation.

## Decision

We adopt a two-folder layout under `s3-reactive-api-adapter/src/test/features/`:

```
features/
├── requirements/          # Business Need scenarios — stakeholder: product owner, S3 API consumer
│   ├── phase-1-upload-storage-engine.feature
│   ├── phase-ep2-metadata-durability.feature
│   └── ...
└── specs/                 # Ability scenarios — stakeholder: developer, maintainer, code reviewer
    ├── fs-concurrency.feature
    ├── fs-crash-safety.feature
    ├── fs-recovery-scanner.feature
    ├── obs-scanner.feature
    ├── admin-routes.feature
    ├── admin-policy-yaml.feature
    ├── streaming-architecture.feature
    └── pipeline-orchestrator.feature
```

Rules:

1. **`requirements/`** contains only `Business Need` scenarios. Each scenario must describe externally observable behavior through an S3-compatible API or Admin API endpoint.
2. **`specs/`** contains only `Ability` scenarios. Each scenario describes an internal mechanism, structural coding standard, or operational capability that has no external S3 API equivalent.
3. A scenario may carry both `@business-need` and `@spec` tags only when the same Gherkin text is shared between two runners with different validation modes; in that case the file lives in `requirements/` and the `@spec` runner filters by tag.
4. The Gherkin requirements appendix (`docs/arc42/generated/gherkin-requirements.adoc`) must group `requirements/` and `specs/` scenarios under separate subsections so readers can navigate by stakeholder audience.
5. Existing JUnit tests that validate `Ability` scenarios remain as fast mechanism tests alongside the Cucumber specs; they are not replaced but complemented.

## Consequences

- **Positive**: Clear stakeholder navigation. A product owner reads only `requirements/`. A developer reads `requirements/` + `specs/`. The ARC42 appendix can group by audience.
- **Positive**: New contributors can find internal mechanism documentation in `specs/` without guessing which feature file contains it.
- **Positive**: Runner configuration becomes simpler — two base runner classes (`RequirementsCucumberTest` for `requirements/`, `SpecificationsCucumberTest` for `specs/`) with different tag profiles.
- **Negative**: Two folders to maintain instead of one. Some scenarios may need relocation, which requires updating step definitions and runner configurations.
- **Negative**: The `specs/` folder adds a new source-of-truth path that must be kept in sync with the code it describes. This is accepted as a net improvement in documentation quality.
- **Negative**: AGENTS.md must be updated to define the folder convention and its relationship to `Business Need` / `Ability` classification.

## Status

**Accepted** (2026-07-07). AGENTS.md §B.8 documents this convention. The two-folder
layout is in active use:

- `requirements/` contains 11 `Business Need` feature files, including the phase-1
  through phase-6/EP2 shared requirement features and the single-node-backend-*
  features migrated from the legacy `awscli/` folder under ADR 0021 Phase 2.
- `specs/` contains `fs-concurrency.feature` (`Ability`), with further specs planned
  under ADR 0021 Phase 3 (e.g. `runtime-effects.feature` candidate).

ADR 0021 tracks the ongoing migration of the remaining legacy `features/object-store/`
directory (239 scenarios) into this structure. Rule 3 (shared `@business-need`/`@spec`
tagging for dual-runner scenarios) has not yet been exercised by any migrated feature;
it remains valid guidance for future dual-validation-mode specs.
