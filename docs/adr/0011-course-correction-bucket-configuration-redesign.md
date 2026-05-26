# ADR 0011 — Course correction: Bucket.Configuration redesign

## Context

What was being done: The Bucket aggregate's Configuration record was extended with 15 nullable fields for all S3 bucket configuration types (Accelerate, Analytics, Encryption, Lifecycle, Logging, Website, Notification, Replication, Policy, PublicAccessBlock, OwnershipControls, RequestPayment, Metrics, IntelligentTiering, Inventory) as a flat record. The handler code (S3BucketConfigHandler) creates these value objects but stores them via `new Bucket.Configuration(null)` — effectively throwing them away.

## Derailment

Specific symptoms:
- Bucket.Configuration became a flat "dump" of 15+ nullable fields — no domain semantics, no invariants, no relationships between config types
- Domain events (BucketConfigurationChanged) became too generic — a single event type for ALL config changes loses the specific meaning of each change
- The value objects exist in the domain but were never properly integrated with the aggregate root — they float as disconnected concepts
- Handler code is copypasta: creates value objects, stores null config, GET handlers always return Optional.empty()
- The AWS S3 API has specific semantics for each config type (lifecycle rules have transitions, replication has source/destination, etc.) that are lost in a flat Configuration record

## Required Change

Instead of a flat Configuration record:
1. Revert Bucket.Configuration to only hold CORS (its original design)
2. Each S3 bucket configuration feature should be modeled as a separate aggregate or value object attached to Bucket via dedicated methods (withAccelerateConfig(), withLifecycleConfig(), etc.) and specific domain events per type
3. Study the official AWS S3 API documentation for each config type to understand:
   - What constraints exist between config types?
   - What lifecycle/state transitions exist?
   - What are the proper domain events?
4. Re-implement each config feature properly with:
   - Dedicated with* methods on Bucket (e.g., withLifecycleConfiguration, withEncryptionConfiguration)
   - Specific domain events per config type
   - Handler code that actually stores and retrieves config data properly
5. Refactor S3BucketConfigHandler to eliminate copypasta once the domain model is correct

## Consequences

- Impact: The naive Configuration extension must be reverted, which means the handler integration work done so far needs to be re-done with the correct domain model
- Benefit: Proper domain model will support real S3 API semantics, correct domain events, and maintainable handler code
- Timing: ADR created BEFORE any correction code or plan update — this is the non-negotiable rule

## Status

Accepted

## Date

2026-05-25

## Implementation Note

Bucket.Configuration was replaced by BucketConfig with dedicated with* methods per config type. Each config feature now has a specific value object (CorsConfiguration, BucketLifecycleConfiguration, etc.). Domain events use BucketConfigChanged with the full BucketConfig payload. Handler code properly stores and retrieves config data through the aggregate root. C4 diagrams and ARC42 have been aligned.
