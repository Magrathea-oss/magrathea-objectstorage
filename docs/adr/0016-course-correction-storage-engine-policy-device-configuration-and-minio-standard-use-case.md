# ADR 0016 — Course correction: storage engine policy/device configuration and MINIO_STANDARD use case

## Context

Previous storage-engine work introduced the direction for a dedicated storage-engine bounded context and related configuration concepts. The current implementation and plan are mismatched with the required architecture: storage policies, storage devices, backend wiring, executable use cases, and verification paths are not yet aligned around a concrete, testable `MINIO_STANDARD` storage policy scenario.

## Derailment

Specific symptoms and gaps:

- The storage-engine backend is not wired into the runtime configuration/profile in a way that makes the storage engine executable end to end.
- `MINIO_STANDARD` is not testable as a concrete canonical storage policy use case.
- `StoragePolicyCatalog` is missing.
- Storage policy YAML configuration is absent.
- Storage device / disk-set YAML configuration is absent.
- The current storage engine configuration is not separated per entity and does not model one configuration file per policy or per device/disk set.
- AWS CLI Cucumber scenario parity is missing for the storage-engine use cases.
- Web UI and admin management for storage policies and storage devices are not planned yet.
- Clover should be considered optional/legacy, while JaCoCo is the current coverage tool and should drive current coverage planning.

## Required Change

The correction must change the plan and implementation direction as follows:

- Use separate YAML files, one per `StoragePolicy`.
- Use separate YAML files, one per `StorageDevice` or disk set.
- Add YAML-backed repositories/catalogs for storage policies and storage devices, including a `StoragePolicyCatalog`.
- Make `MINIO_STANDARD` the first tested storage policy use case.
- Keep chunking inside `DedupConfig`; chunking must be configurable by `StoragePolicy` instead of becoming an independent top-level storage-engine concern.
- Wire the storage-engine backend and runtime profile so the storage engine can be exercised end to end.
- Add a web interface/admin management path for storage policies and storage devices.
- Add parallel AWS CLI Cucumber scenarios that use the same canonical scenarios as the non-AWS-CLI Cucumber coverage.

## Consequences

- Domain planning must include storage policy and storage device concepts, catalogs, and `DedupConfig` chunking ownership.
- Application planning must include use cases for loading, validating, selecting, and applying storage policies and devices.
- Infrastructure planning must include YAML-backed repositories/catalogs and profile/backend wiring.
- Test planning must prioritize `MINIO_STANDARD` as the first executable storage policy scenario and maintain AWS CLI Cucumber parity through shared canonical scenarios.
- Documentation planning must describe policy/device YAML structure, runtime profile wiring, and the canonical `MINIO_STANDARD` path.
- UI planning must include administrative management for storage policies and storage devices.
- Quality planning should treat JaCoCo as the current coverage mechanism and Clover as optional/legacy unless a future decision revives it.

## Status

Accepted

## Date

2026-06-12
