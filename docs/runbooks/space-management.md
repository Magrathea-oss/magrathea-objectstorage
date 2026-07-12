# Space Management and Data Hygiene

## Integrity scrubbing

Periodic scrubbing is disabled by default. Enable it for the storage-engine profile with:

```properties
storage.engine.integrity.scrub.enabled=true
storage.engine.integrity.scrub.initial-delay-ms=60000
storage.engine.integrity.scrub.interval-ms=86400000
storage.engine.integrity.scrub.repair-policy=REPORT_ONLY
```

`repair-policy` accepts `REPORT_ONLY` or `QUARANTINE`. Scrubbing hashes the complete final persisted representation incrementally. Compressed and encrypted artifacts are checked as stored bytes; the manifest transformation chain is included in findings without exposing plaintext or encryption keys.

Use `REPORT_ONLY` first. Before selecting `QUARANTINE`, ensure client-visible redundancy or a recovery copy exists: this release detects and isolates corruption but does not reconstruct missing EC shards.

## Garbage collection

Deleting or overwriting an S3 object prepares a durable reclamation marker before detaching its manifest reference. Reclamation is typed:

- plain uploads remove their `WHOLE_OBJECT` unit;
- deduplicated chunks remain until no live manifest references them;
- aborted or expired multipart uploads remove their uncommitted parts;
- EC deletion removes the owning data and parity shard set.

Pending markers are replayed after restart and repeated runs are idempotent. Do not remove files manually from `metadata/gc/pending`.

## Bucket quotas

Configure a logical-byte quota through the Admin API:

```http
PUT /admin/buckets/{bucket}/quota
Content-Type: application/json

{"quotaBytes": 10737418240}
```

Inspect accounting with:

```http
GET /admin/buckets/{bucket}/capacity
```

The response reports `usedBytes`, `reservedBytes`, `quotaBytes`, `rejectedReservations`, and `lastRejectedBytes`. Accounting uses logical committed object bytes; deduplication and compression do not reduce a tenant's logical usage.

Quota reservations are atomic for the declared single-process storage-engine deployment. Multi-node quota coordination belongs to EP-10.

## Capacity failures

A quota rejection returns S3 error `QuotaExceeded`. Filesystem exhaustion returns `InsufficientStorage`; both use HTTP 507 in the current contract. Failed writes publish no S3 object reference and clean unpublished artifacts and reservations.

Capacity failure events contain backend, configured storage root, requested bytes, and available bytes. They must not contain payload bytes, user metadata, or credentials.
