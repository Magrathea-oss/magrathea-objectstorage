# 4. Reactive boundary CompletableFuture to Mono Flux

Date: 2026-05-21

## Status

Accepted

## Context

Domain repositories return `CompletableFuture` (Java SE standard async), but Spring Boot 4 WebFlux uses `Mono`/`Flux` for reactive composition. The boundary between these two async models must be explicit and manageable.

## Decision

- **Domain**: all repository interfaces return `CompletableFuture<Void>` or `CompletableFuture<Optional<T>>`
- **Application**: services bridge CompletableFuture → Mono/Flux using `Mono.fromFuture()` / `Flux.fromIterable().flatMap()`
- **Infrastructure**: repository implementations use `CompletableFuture.supplyAsync()` or direct return
- **No reactive types in domain**: Mono, Flux, DataBuffer are infrastructure-only

## Consequences

**Positive**: domain stays framework-free, application layer handles the async bridge explicitly
**Negative**: `.join()` in application services blocks virtual threads — acceptable for single-node
