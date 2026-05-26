# ADR 0007: Pluggable S3 API via Spring Boot Auto-Configuration

## Status
Accepted

## Context

The project must expose only AWS S3-compatible HTTP APIs. A previous internal JSON REST API was not standard S3 and was removed. The S3 API should be independently loadable so that the storage core can exist without an HTTP S3 adapter.

## Decision

Create a dedicated `s3-api` module containing:

- `S3ProxyRouter`
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
- No custom non-S3 HTTP API is allowed.
