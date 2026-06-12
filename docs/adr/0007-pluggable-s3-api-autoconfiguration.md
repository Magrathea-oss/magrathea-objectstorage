# ADR 0007: Pluggable S3 API via Spring Boot Auto-Configuration

## Status
Accepted

> Current boundary note (2026-06-12): later admin/configuration endpoints are exposed separately by `admin-api-adapter` under `/admin/**`. They are internal/administrative APIs and are not part of S3 route or semantic coverage. The S3 object API remains pluggable through this ADR.

## Context

The project must expose its public object-storage API through AWS S3-compatible HTTP endpoints. A previous non-S3 object API was removed. The S3 API should be independently loadable so that the storage core can exist without an HTTP S3 adapter. Internal/admin endpoints, when present, must stay separate from S3 API coverage.

## Decision

Create a dedicated `s3-api` module containing:

- `S3PathRouter`
- `S3XmlResponses`
- `JacksonXmlCodecConfig`
- `S3ApiConfig`
- Cucumber S3 API integration tests

The module is loaded through Spring Boot 4 auto-configuration using:

```text
META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

`S3ApiConfig` is guarded by:

- `@ConditionalOnClass({ BucketService.class, ObjectService.class })`
- `@ConditionalOnProperty(name = "s3.api.enabled", havingValue = "true", matchIfMissing = true)`

## Consequences

- Including `s3-api` on the classpath activates S3 routes by default.
- Setting `s3.api.enabled=false` disables S3 routes at runtime.
- Removing the `s3-api` dependency removes the web API completely.
- `object-store-infrastructure` contains repository implementations only.
- No custom non-S3 object API is allowed in the S3 adapter. Internal/admin APIs must remain in a separate adapter and must not be counted as S3 API coverage.
