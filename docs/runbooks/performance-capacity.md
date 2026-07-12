# EP-6 Performance and Capacity Validation

## Scope

EP-6 validates a conservative operating envelope for the Magrathea `0.1.x` single-node JVM/storage-engine preview. It does not provide production sizing, hardware extrapolation, distributed-capacity evidence, or comparisons with other object stores.

## Enforced defaults

```properties
s3.capacity.enabled=true
s3.capacity.max-single-put-bytes=268435456
s3.capacity.max-multipart-part-bytes=67108864
s3.capacity.max-assembled-multipart-bytes=268435456
s3.capacity.max-concurrent-requests=16
s3.capacity.max-tcp-connections=64
s3.capacity.request-timeout=300s
s3.capacity.rate-limit-per-second=100
s3.capacity.rate-limit-burst=200
```

Known and streamed over-limit uploads fail with `EntityTooLarge`. Requests that exceed concurrency or rate limits fail immediately with `SlowDown`; rate rejection includes `Retry-After`. Stalled request bodies fail with `RequestTimeout`. Admission permits and TCP capacity are released after success, error, timeout, or cancellation.

These are preview safety limits, not AWS S3 maxima. Raising them creates an unvalidated operating envelope and requires a new EP-6 run.

## Validation profiles

### Capacity and protocol limits

```bash
mvn -B --no-transfer-progress \
  -Pep6-performance-capacity-tests \
  -pl s3-reactive-api-adapter -am test
```

Runs the 256 MiB single-object and multipart envelope, rejection, timeout, concurrency, and token-bucket scenarios under `-Xmx256m`.

### Real TCP connection cap

```bash
mvn -B --no-transfer-progress \
  -Pep6-connection-cap-tests \
  -pl s3-reactive-api-adapter -am test
```

Uses a real child JVM and sockets. The low test cap is four: the fifth connection is closed, established connections remain serviceable, and closing one restores capacity.

### Mandatory CI load

```bash
mvn -B --no-transfer-progress \
  -Pep6-ci \
  -pl s3-reactive-api-adapter -am test
```

The deterministic seed `ep6-ci-0.1.x` drives eight workers for 45 seconds with PUT, GET, HEAD, ranged GET, multipart, and delete operations. The child process runs with `-Xmx256m`. The gate requires at least 90 completed operations, zero corruption/unexpected responses, p99 below 10 seconds, heap within 256 MiB, post-GC heap within 192 MiB, and idle request/connection/temp-artifact counts of zero.

### Scheduled/manual soak

```bash
mvn -B --no-transfer-progress \
  -Pep6-soak \
  -pl s3-reactive-api-adapter -am test
```

The soak runs the same deterministic family for 15 minutes and requires at least 1,800 operations, zero corruption/unexpected responses, bounded retained heap, and zero idle leaks. It is scheduled weekly in CI and can be started manually.

## Evidence format

Each load run writes under:

```text
s3-reactive-api-adapter/target/ep6/results/<mode>/
├── child.log
├── result.json
└── summary.md
```

`result.json` records revision, dirty-tree state, seed, runtime, operating system, processor count, configured bounds, operation/error counts, latency summary, heap samples, resource counts, redaction vocabulary, and artifact checksums. CI uploads this directory even when a gate fails.

## Local validation recorded on 2026-07-12

| Mode | Duration | Completed operations | p99 | Peak heap | Post-GC heap | Corruption/unexpected errors |
|---|---:|---:|---:|---:|---:|---:|
| CI | 45 s | 184 | 204 ms | 179,890,176 B | 32,521,216 B | 0 |
| Soak | 900 s | 3,600 | 187 ms | 138,494,976 B | 34,362,368 B | 0 |

The run used Java 25.0.3 on the recorded local runner. CI validates the same requirements on its declared Java 21 release environment.

**Capacity validation only; not a production sizing, comparative, or competitive benchmark.**

Competitive benchmark methodology and published cross-product results belong exclusively to KA-6.
