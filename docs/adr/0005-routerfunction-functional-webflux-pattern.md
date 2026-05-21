# 5. RouterFunction functional WebFlux pattern

Date: 2026-05-21

## Status

Accepted

## Context

Spring WebFlux supports both `@Controller` annotation-based and `RouterFunction` functional programming styles. The S3 API requires precise content-negotiation routing (XML vs JSON for the same path) and fine-grained endpoint mapping.

## Decision

ALL HTTP endpoints use `RouterFunction<ServerResponse>`:
- NO @Controller, NO @RequestMapping, NO @GetMapping/@PostMapping
- `RouterFunctions.route()` builder for precise path/headers/accept matching
- Content-negotiation via predicate functions (`acceptXml(req)`, `acceptJson(req)`)
- S3-compatible error responses as XML records
- Bean wiring in `InfrastructureConfig` exposing `RouterFunction<ServerResponse>`

## Consequences

**Positive**: precise routing control, functional style matches S3 API complexity, better testability
**Negative**: more verbose than annotation-based, requires explicit bean configuration
