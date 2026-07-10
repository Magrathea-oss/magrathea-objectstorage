# Graceful shutdown runbook

This runbook documents the validated EP-5 shutdown procedure for a single-node storage-engine deployment.

## Runtime configuration

The bootstrap application uses:

```properties
server.shutdown=graceful
spring.lifecycle.timeout-per-shutdown-phase=20s
```

Packaged single-node containers must run with the `storage-engine` profile and a persistent
`STORAGE_ENGINE_FILESYSTEM_ROOT`.

## Normal shutdown

1. Stop routing new S3 requests to the instance.
2. Confirm replacement capacity is ready when availability is required.
3. Send `SIGTERM` to the application process or stop the container through the orchestrator.
4. Allow the configured shutdown timeout to expire naturally; do not immediately send `SIGKILL`.
5. Check logs for graceful-shutdown start and completion messages.
6. Treat any Spring Boot generated-password banner as a security configuration regression.

## Validated behavior

`REQ-OPS-004` proves that a process receiving `SIGTERM` exits within 10 seconds without forced termination
and preserves an object committed before shutdown.

`REQ-OPS-009` starts a 524,288-byte streaming `PutObject`, waits until request body delivery is active,
sends `SIGTERM`, and proves that:

- the request was still in flight when the signal was sent;
- the `PutObject` completed with HTTP 200;
- the process exited within 10 seconds without forced termination;
- after restart with the same filesystem root, `GetObject` returned 524,288 bytes with the original SHA-256 checksum.

`REQ-OPS-010` applies the same active-request shutdown sequence to multipart upload state. It starts a
524,288-byte streaming `UploadPart`, sends `SIGTERM` while the part body is still in flight, verifies HTTP 200
and captures the part ETag, restarts with the same root, completes the multipart upload using that ETag, and
verifies the final object's byte count and SHA-256 checksum.

`REQ-OPS-011` expands the drain evidence to concurrent requests. Two clients stream separate 262,144-byte
PutObject bodies at the same time; the test waits until both bodies are active, sends one `SIGTERM`, requires both
requests to return HTTP 200, restarts with the same root, and verifies both objects by byte count and SHA-256.

`REQ-OPS-012` validates multipart completion itself as an active request. The test uploads a deterministic 524,288-byte
part, starts `CompleteMultipartUpload` with a throttled XML body, sends `SIGTERM` after body delivery begins, requires the
completion response to return HTTP 200, then restarts and verifies the assembled object by byte count and SHA-256.

`REQ-OPS-013` covers the non-success path. A client cancels an active UploadPart after body delivery starts,
operators abort its multipart upload, and then send `SIGTERM`. After restart, the test requires no committed object,
no active upload entry, and no multipart part directory or temporary part artifact for that upload ID.

`REQ-OPS-014` overlaps `AbortMultipartUpload` with an active, throttled `CompleteMultipartUpload`. Abort returns HTTP 204
before the completion body finishes; `SIGTERM` is then sent while completion remains active. Graceful draining returns
S3 `NoSuchUpload` for completion, and restart verification proves that abort published no object or part artifacts.

`REQ-OPS-015` adds a bounded mixed-load rehearsal: three concurrent 262,144-byte PutObjects and two concurrent
GetObjects of a 2,097,152-byte fixture. Read consumers pause on their first response chunk so all five streams are
provably active before `SIGTERM`; all responses must return HTTP 200, and restart verification checks every object by
byte count and SHA-256. This is a deterministic shutdown gate, not a production-scale capacity benchmark.

## Recovery verification

After restart:

1. Require `/admin/live` to return HTTP 200.
2. Require `/admin/ready` to return HTTP 200 with status `ready`.
3. Read the last committed or drained object through the S3 API.
4. Compare its expected length and checksum where available.

## Open gaps

- A bounded five-request mixed read/write drain is validated, but larger request sets, sustained arrival rates, and production-scale capacity have not been load-tested.
- Multipart UploadPart and CompleteMultipartUpload draining, cancellation followed by abort, and the abort-wins completion overlap are validated; completion-wins races under repeated contention remain outside the current scope.
- Multi-node traffic shifting and rolling shutdown remain future distributed-operability work.
