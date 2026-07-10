# ADR 0023: Reactive Spring Security for S3 Security

Date: 2026-07-09

## Status

Accepted — first implementation slice started on 2026-07-10.

## Context

EP-1 started with a narrow custom WebFlux `WebFilter` spike to make the first
SigV4 authentication requirements executable. That spike now proves several
observable requirements, but it must not become a parallel hand-rolled security
framework.

Magrathea is a Spring WebFlux application. The production security backbone
should therefore use Spring Security's reactive stack where possible instead of
expanding bespoke filter, authentication, authorization, exception, and audit
plumbing.

A repository check on 2026-07-09 found WebFlux already present
(`spring-boot-starter-webflux`), but no Spring Security dependency or
`SecurityWebFilterChain` configuration in the Maven modules. The first migration
slice on 2026-07-10 added the dependency and Spring Security Reactive backbone.

## Decision

EP-1 production security will be based on **Spring Security Reactive / WebFlux
Security**. The current custom EP-1 security classes are reclassified as a
prototype/spike used to shape requirements and tests, not as the final
architecture.

The target architecture is:

- Add `spring-boot-starter-security` to `s3-reactive-api-adapter` when the
  migration begins.
- Configure a reactive `SecurityWebFilterChain` with `ServerHttpSecurity`.
- Preserve Magrathea's current default trusted-environment behavior by explicitly
  permitting requests when `s3.security.enabled=false`; do not rely on Spring
  Security's default generated-login behavior.
- Implement S3 SigV4 authentication through Spring Security reactive extension
  points:
  - a `ServerAuthenticationConverter` extracts SigV4 data from
    `ServerWebExchange`;
  - a `ReactiveAuthenticationManager` validates access key, credential scope,
    clock skew, signed headers, signature, and exact payload hashes where
    required;
  - a custom `Authentication` principal carries the Magrathea principal/account
    identity with credentials erased after authentication.
- Implement S3 authorization through Spring Security reactive authorization
  points, for example `ReactiveAuthorizationManager<AuthorizationContext>`, backed
  by Magrathea bucket policy, ACL, PublicAccessBlock, expected-owner, and future
  IAM-style policy services.
- Return S3-compatible XML errors via Spring Security reactive entry points and
  access-denied handlers rather than ad-hoc response code paths.
- Publish audit decisions from the authentication/authorization result path into
  a durable audit service. In-memory audit remains test-only/spike evidence.
- Keep SSE-S3 encryption separate from Spring Security; it belongs in storage
  write/read services and key-management integration, while Spring Security
  supplies identity and authorization context.

## Treatment of the current custom EP-1 code

`S3SecurityWebFilter` remains prototype/component-spec support only and is no
longer registered as the secured-mode production filter. SigV4 verification,
authorization, audit, and SSE behavior now enter through Spring Security Reactive
components plus local durable backing services (`S3CredentialStore`,
`DurableS3SecurityPolicyStore`, `FileS3SecurityAuditSink`, and
`LocalS3KeyManagementService`).

Future security increments should keep authentication and authorization behind
Spring Security reactive abstractions. External identity federation adapters may
replace or complement the local backing services without creating a parallel S3
object API.

## Consequences

- EP-1 status is `@implemented-and-validated` for the declared local built-in
  scope: the current SigV4/authz/audit/SSE behavior has WebTestClient and AWS
  CLI/e2e evidence through the Spring Security Reactive backbone, and component
  specs validate durable encrypted credentials, policy reload, tamper-evident
  audit, and local key-management material.
- New security scenarios remain Cucumber-first in
  `phase-ep1-security-identity.feature` because they document externally
  observable S3 behavior.
- Completion claims for EP-1 require the Spring Security reactive migration plus
  semantic validation of the S3 behavior and local backing services, not merely
  more custom WebFilter behavior.
- AWS CLI/e2e validation is present for REQ-SEC-001..009; durable backing-service
  specs cover REQ-SEC-010..013. External federation remains future KA-4 scope.

## Migration checklist

1. [x] Add `spring-boot-starter-security` to `s3-reactive-api-adapter`.
2. [x] Add explicit permit-all `SecurityWebFilterChain` behavior for unsecured mode.
3. [x] Move SigV4 header parsing/signature validation into a reactive
   authentication converter/manager pair for the current REQ-SEC-001/002/003 slice.
4. [x] Move deny-by-default, explicit deny, PublicAccessBlock, ACL,
   expected-owner, and bucket-policy evaluation into a reactive authorization
   manager backed by durable local policy services.
5. [x] Move XML authentication/access-denied responses into Spring Security handlers.
6. [x] Move audit from in-memory spike sink to a durable redacted audit service.
   File-backed audit appends with fsync and hash-chain integrity; in-memory audit
   remains test fallback only.
7. [x] Re-run WebTestClient Cucumber scenarios and add AWS CLI/e2e EP-1 runners
   before upgrading scenario status tags. All current REQ-SEC-001..009
   WebTestClient and AWS CLI/e2e scenarios pass. REQ-SEC-010..013 component
   specs validate durable local credential, policy, audit, and key-management
   services.
