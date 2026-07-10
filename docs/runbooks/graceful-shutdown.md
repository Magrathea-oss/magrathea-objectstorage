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

## Recovery verification

After restart:

1. Require `/admin/live` to return HTTP 200.
2. Require `/admin/ready` to return HTTP 200 with status `ready`.
3. Read the last committed or drained object through the S3 API.
4. Compare its expected length and checksum where available.

## Open gaps

- Concurrent draining of multiple uploads and reads has not yet been load-tested.
- Multipart completion and cancellation races during shutdown need dedicated evidence.
- Multi-node traffic shifting and rolling shutdown remain future distributed-operability work.
