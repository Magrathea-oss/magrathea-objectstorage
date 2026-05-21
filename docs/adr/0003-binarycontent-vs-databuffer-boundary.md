# 3. BinaryContent vs DataBuffer boundary

Date: 2026-05-21

## Status

Accepted

## Context

Binary content exists in two representations: domain semantics (content-type, filename, size, compression, checksum) and infrastructure bytes (byte[], DataBuffer, Flux<DataBuffer>). Mixing byte[] in domain violates zero-framework purity and creates an uncontrollable dependency on binary data representation.

## Decision

- **Domain**: `BinaryContent` value object holds ONLY metadata descriptors — NO byte[]
  - content-type, filename, size, metadata map, compression type, checksum
- **Infrastructure**: `BinaryContentAdapter` converts between domain metadata and DataBuffer/byte[]
- **Content store**: uses `ConcurrentHashMap<String, byte[]>` for simplicity (infrastructure only)
- **Streaming**: `Flux<DataBuffer>` for HTTP request/response body

## Consequences

**Positive**: domain remains pure, binary handling is infrastructure concern, streaming is reactive-native
**Negative**: adapter overhead, two representations to maintain
