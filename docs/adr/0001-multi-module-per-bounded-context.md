# 1. Multi-Module per Bounded Context con DDD + Spring Reactive

Date: 2026-05-20

## Status

Accepted

## Context

Il progetto Magrathea ObjectStore implementa un AWS S3-compatible object storage con Spring Boot 4 WebFlux. La struttura modulare deve separare rigorosamente il dominio puro (DDD) dall'infrastruttura reattiva, seguendo i pattern definiti dagli agenti `domain-coder` e `app-infra-coder`.

## Decision

Ogni bounded context e' suddiviso in tre moduli Maven flat sibling:

### 1. `<boundedN>-domain` — Dominio puro (DDD)
- Java 17+ records per aggregate root (Bucket, S3Object)
- Value objects come record immutabili (ObjectKey, Region, StorageClass)
- Domain events con sealed interface
- Repository interfaces con `CompletableFuture` (standard Java async)
- Domain services con logica business pura
- `BinaryContent` value object: SOLO metadati (content-type, filename, size, compression, checksum)
- **NO byte[]**, NO DataBuffer, NO Spring imports, NO JPA, NO reactive types
- Test: JUnit puro, zero Spring context

### 2. `<boundedN>-application` — Orchestrazione applicativa
- Application services con `@Service`
- DTO come Java records (CreateBucketCommand, PutObjectCommand, BucketResponse, ObjectResponse)
- Bridge tra CompletableFuture (dominio) e Mono/Flux (infrastruttura)
- CAN dipendere da Spring

### 3. `<boundedN>-infrastructure` — Adapter reattivi
- **RouterFunction<ServerResponse>** per tutti gli endpoint HTTP (NO @Controller, NO @RequestMapping)
- XML serializzato con `@JacksonXmlRootElement` record (NO StringBuilder)
- `BinaryContentAdapter`: BinaryContent (metadati) ↔ DataBuffer/Flux<DataBuffer> (bytes)
- Content store per binary data (ConcurrentHashMap + file persistence)
- Persistenza su file single-node con DataBuffer (NO R2DBC)
- Test: Cucumber BDD con WebTestClient + AWS CLI verification

### 4. Moduli aggiuntivi
- `shared-domain`: BinaryContent (cross-cutting)
- `bootstrap-application`: Spring Boot entry point, compone i moduli attivi
- `persistence-context-*`: placeholder vuoti (non implementati in v1)

## Conseguenze

**Positive**:
- Dominio puramente business, testabile senza Spring
- Framework e reactive types confinati all'infrastruttura
- RouterFunction permette routing S3-compatible preciso (XML vs JSON content negotiation)
- DDD garantisce che il modello riflette le specifiche AWS S3 reali
- Spring Reactive (WebFlux, DataBuffer, Flux/Mono) e' gestito interamente in infrastruttura

**Negative**:
- 9 moduli da gestire (6 attivi + 3 placeholder)
- Mapping layer tra dominio e persistence
- No Spring DI nel dominio

## Related ADRs

- (Additional ADRs dopo la prima build funzionante: zero-dependency domain, BinaryContent vs DataBuffer, reactive boundary, RouterFunction pattern)
