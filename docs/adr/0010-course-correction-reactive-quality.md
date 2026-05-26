# ADR 0010 — Course correction: Reactive refactoring quality, aggregate root integrity, documentation alignment

## Context

ADR 0009 was accepted and implementation started. java-planner delegated creation of reactive repository interfaces, services, infrastructure implementations, and s3-reactive-api-adapter updates. The implementation produced code that is incomplete (stub methods in repositories), uses simplistic reactive patterns, does not enforce aggregate root state transitions with domain events, and does not align C4/ARC42 documentation.

## Derailment

Specific symptoms, violated constraints:

1. **Stub methods**: InMemoryReactiveBucketRepository.saveConfiguration/deleteConfiguration are empty stubs. Other repository methods are simplistic. The api-coverage report shows coverage that doesn't match actual stub state.
2. **Aggregate root integrity**: Objects inside aggregate roots (Bucket, S3Object, MultipartUpload) are independent — state transitions don't pass through the main aggregate root object, domain events are not notified/tracked, aggregate root alteration is not recorded.
3. **Reactive repository interfaces too simplistic**: They don't leverage reactive capabilities (backpressure, error handling, operator fusion, etc.).
4. **C4 diagrams not aligned**: Not updated to reflect current architecture state.
5. **ARC42 not aligned**: Likely out of date with current implementation.

## Required Change

1. All repository implementations must have real (non-stub) method bodies with proper in-memory storage and reactive patterns.
2. Aggregate roots must enforce state transitions through the main aggregate object with domain event notification — objects inside the aggregate root must track alterations via the main root.
3. Reactive repository interfaces must be redesigned to fully leverage reactive capabilities (Flux/Mono operators, backpressure, error handling, etc.).
4. C4 diagrams must be updated to reflect actual current architecture.
5. ARC42 must be updated to align with current state.
6. Tests must be made more sophisticated to match real behavior.

## Consequences

- All ADR 0009 implementation code from Steps 3-5-6 needs review and rework.
- java-domain-coder needs to fix aggregate root patterns.
- java-infra-coder needs to rewrite repository implementations with proper reactive patterns and non-stub methods.
- documenter needs to update C4 and ARC42.
- java-tester needs to write sophisticated tests.
- Timeline impact: significant rework of the reactive migration.

## Status

Accepted

## Date

2026-05-24

## Implementation Note

The reactive migration was fully implemented. Repository implementations now have real (non-stub) method bodies. Aggregate roots enforce state transitions through the main aggregate object with domain event notification. Reactive repository interfaces leverage Flux/Mono operators. C4 diagrams and ARC42 have been aligned. Tests have been updated for real behavior. The remaining `Mono.fromCallable` wrappers in repository implementations are acceptable for in-memory storage where synchronous operations are the norm.
