# ADR 0008 — Remove All Custom XML Parsing, Use Spring Boot 4 Codec Infrastructure

## Status

Implemented

## Context

The `s3-api` module implements an AWS S3-compatible HTTP API that sends and receives XML for bucket configurations, object metadata, tagging, ACLs, multipart uploads, and error responses.

The project has `tools.jackson.dataformat:jackson-dataformat-xml` as a dependency (Jackson 3 XML), and `JacksonXmlCodecConfig` registers a `JacksonXmlEncoder` for XML response serialization. However, request body deserialization was handled via:

1. **Regex-based XML parsing** in `S3BucketConfigHandler`, `S3BucketMetadataHandler`, `S3ObjectMetadataHandler`, `S3ObjectOperationsHandler`, `S3BucketOperationsHandler`, and `S3BucketConfigListHandler` — using `java.util.regex.Pattern`, `extractXmlValue()`, `extractXmlList()`, and string `contains()` checks.
2. **A custom `S3XmlParser` class** wrapping `XmlMapper` directly, never imported by any handler.
3. **A monolithic `S3XmlResponses` file** with all XML response records and static `from()` factory methods, creating a parallel DTO hierarchy.

This violated the `java-infra-coder` agent constraint: **"FORBIDDEN: Regex XML Parsing"**.

### Root Cause

The `java-infra-coder` agent generated `S3XmlParser` (Jackson-based) in one pass and the handler classes in a later pass without cross-referencing. The handler pass opted for regex parsing because it appeared "faster" to implement inline extraction than to integrate the existing Jackson records. The agent did not enforce its own constraint during generation — no delegation check caught the violation because the agent treated regex parsing as "handler implementation detail" rather than recognizing it as an XML-parsing task that Jackson should handle.

## Decision

Remove ALL custom XML parsing and rely exclusively on Spring Boot 4's WebFlux codec infrastructure:

1. **Request deserialization**: Register `JacksonXmlDecoder` in `JacksonXmlCodecConfig` so that `request.bodyToMono(Dto.class)` deserializes XML request bodies directly into annotated records.
2. **Request DTOs**: Define in `S3Request.java` — annotated records only, no logic, no XmlMapper.
3. **Response DTOs**: Define in separate files per domain concept — `BucketListResponse.java`, `BucketConfigResponse.java`, `ErrorResponse.java`, `MultipartResponse.java`, `ObjectMetadataResponse.java`, `ObjectOperationResponse.java`.
4. **Remove**: `S3XmlParser.java`, `S3XmlResponses.java`, the `xml/` package directory, and ALL regex-based parsing methods from handlers.
5. **Handler refactor**: Every PUT/POST handler that receives XML body uses `request.bodyToMono(RequestDto.class)` directly.

## Consequences

- **Positive**: No fragile regex XML parsing. Single source of truth for XML binding (Jackson annotations on DTOs). Spring Boot 4 manages content negotiation — handlers declare `MediaType.APPLICATION_XML` and the codec handles serialization.
- **Positive**: Request DTOs are pure annotated records — no parsing logic leak. Response DTOs are separate files organized by domain concept.
- **Positive**: The codec decoder handles edge cases (whitespace, encoding, malformed XML) that regex cannot.
- **Negative**: Handler files change significantly — all PUT/POST handlers that previously read `String` body now read typed DTOs.
- **Negative**: The `xml/` package directory is removed; DTOs move to `dto/`.
- **Negative**: `S3BucketConfigListHandler` also loses its dependency on `S3BucketConfigHandler.extractXmlValue()` — must define its own DTOs or use `S3Request`.

## Compliance

The `java-infra-coder` agent definition is updated to:
- Strengthen the FORBIDDEN rule with explicit "BLOCK — do not generate" language
- Add a self-audit step: before writing any handler, check if XML body handling is needed and verify Jackson DTOs exist
- Add a cross-reference requirement: handlers MUST use `S3Request.*` DTOs, not regex

## Resolution

All 14 correction phases were implemented and verified:

1. **Phase 1–12**: `JacksonXmlDecoder` registered, 14 Command DTOs created in `dto/command/`, 29 Query DTOs created in `dto/query/`, all handlers refactored to use `request.bodyToMono(Dto.class)` — no regex XML parsing remains.
2. **Phase 13**: 141 Cucumber tests pass, 3 CORS scenarios pass, 10 pre-existing unrelated failures remain unchanged.
3. **Phase 14**: ADR 0008 updated and ARC42 documentation aligned.

**Result**: The `xml/` package is deleted, `S3XmlParser.java` and `S3XmlResponses.java` are removed, and ALL XML parsing now goes through the Spring Boot 4 Jackson XML codec.

## Status Panel Note

The multi-agent TUI status panel highlights `java-infra-coder` as the agent responsible for handler generation. This ADR documents why the generated code was incorrect and what corrective action was taken.
