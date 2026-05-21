# 2. Zero-dependency domain purity

Date: 2026-05-21

## Status

Accepted

## Context

Domain modules must remain purely business logic with no framework coupling. Spring annotations, JPA, and reactive types would create an uncontrollable dependency on the framework, making domain logic untestable without Spring context and violating DDD principles.

## Decision

Domain modules (`*-domain`, `shared-domain`) have ZERO framework dependencies:
- NO Spring imports (no @Service, @Component, @Repository, @Autowired)
- NO JPA annotations (no @Entity, @Table, @Column)
- NO reactive types (no Mono, Flux, DataBuffer)
- NO byte[] in BinaryContent — metadata only
- Repository interfaces return `CompletableFuture` (Java SE standard)
- Only allowed dependency: `jakarta.validation-api` (spec only, provided scope)

## Consequences

**Positive**: domain testable with pure JUnit (no Spring context), domain code is portable, framework-swapping possible
**Negative**: mapping layer needed between domain CompletableFuture and application Mono/Flux
