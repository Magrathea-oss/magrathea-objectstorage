# ADR 0012 — Phase F: Advanced and Specialized S3 Operations

## Context

Phase F implements the remaining 27 S3 API operations that are advanced or specialized. These operations were deferred from earlier phases because they introduce new S3 concepts (legal hold, object lock, retention, directory buckets, ABAC, torrent, restore, select, rename, encryption update, Object Lambda, and bucket metadata configurations) or require complex request/response handling (SelectObjectContent, WriteGetObjectResponse).

The 27 operations are:

| Category | Operations |
|---|---|
| Session management | CreateSession, ListDirectoryBuckets |
| ABAC | GetBucketAbac, PutBucketAbac |
| Legal hold / lock | GetObjectLegalHold, PutObjectLegalHold, GetObjectLockConfiguration, PutObjectLockConfiguration, GetObjectRetention, PutObjectRetention |
| Torrent | GetObjectTorrent |
| Restore | RestoreObject |
| Select content | SelectObjectContent |
| Rename | RenameObject |
| Encryption update | UpdateObjectEncryption |
| Object Lambda | WriteGetObjectResponse |
| Bucket metadata configs | CreateBucketMetadataConfiguration, DeleteBucketMetadataConfiguration, GetBucketMetadataConfiguration, CreateBucketMetadataTableConfiguration, DeleteBucketMetadataTableConfiguration, GetBucketMetadataTableConfiguration, UpdateBucketMetadataInventoryTableConfiguration, UpdateBucketMetadataJournalTableConfiguration |

## Decision

### 1. Implementation Order — Batched by Complexity

Operations were implemented in five batches, ordered from simplest (reusing existing patterns with minimal new domain types) to most complex (requiring new request/response streaming or new S3 concepts):

**Batch 1 — Simple, existing patterns** (reuse existing DTO patterns, no new domain types):
- RenameObject — rename key, maps to existing copy+delete pattern
- UpdateObjectEncryption — update encryption metadata, maps to existing metadata update pattern
- GetObjectTorrent — returns torrent file, maps to existing GetObject response pattern
- RestoreObject — restore from Glacier, maps to existing PUT pattern

**Batch 2 — Configuration-like** (follow existing bucket config handler pattern):
- CreateSession — session management, maps to existing POST pattern
- ListDirectoryBuckets — directory bucket listing, maps to existing ListBuckets pattern
- GetBucketAbac — attribute-based access control config, maps to existing GET config pattern
- PutBucketAbac — attribute-based access control config, maps to existing PUT config pattern

**Batch 3 — Legal/Lock/Retention** (introduce new domain types):
- GetObjectLegalHold — new LegalHold domain type
- PutObjectLegalHold — new LegalHold domain type
- GetObjectLockConfiguration — new ObjectLockConfiguration domain type
- PutObjectLockConfiguration — new ObjectLockConfiguration domain type
- GetObjectRetention — new RetentionPeriod domain type
- PutObjectRetention — new RetentionPeriod domain type

**Batch 4 — Complex request/response handling**:
- SelectObjectContent — streaming SQL-like SELECT on object content
- WriteGetObjectResponse — Object Lambda response writing

**Batch 5 — Bucket metadata configurations** (eight operations, new domain types for metadata configuration):
- CreateBucketMetadataConfiguration
- DeleteBucketMetadataConfiguration
- GetBucketMetadataConfiguration
- CreateBucketMetadataTableConfiguration
- DeleteBucketMetadataTableConfiguration
- GetBucketMetadataTableConfiguration
- UpdateBucketMetadataInventoryTableConfiguration
- UpdateBucketMetadataJournalTableConfiguration

### 2. Pattern Reuse

All operations follow the established pattern from ADR 0005 (RouterFunction functional WebFlux pattern) and ADR 0006 (S3 API protocol endpoint mapping):

- **RouterFunction route** — registered in `S3PathRouter` with appropriate HTTP method, path, and query parameters
- **Handler method** — implemented in the appropriate handler class (`S3SessionHandler`, `S3BucketOperationsHandler`, `S3ObjectOperationsHandler`, `S3ObjectMetadataHandler`, or `S3BucketConfigHandler`)
- **DTOs** — command DTOs in `dto/command/` for request bodies, query DTOs in `dto/query/` for response bodies
- **Cucumber feature scenarios** — at least one success scenario and one failure scenario per operation, covering all error cases documented in the AWS S3 API spec
- **AWS CLI tests** — where AWS CLI exposes the operation, add CLI test variants in `test-aws-cli.sh`

### 3. Domain Modeling

New domain types should only be created when the operation introduces genuinely new S3 concepts. Specifically:

- **LegalHold** — new value object in `object-store-domain` for legal hold status (On/Off)
- **ObjectLockConfiguration** — new value object for lock configuration (enforcement mode, retention period)
- **RetentionPeriod** — new value object for retention period (duration, unit)
- **BucketMetadataConfiguration** — new value object for bucket metadata configuration (metadata key, type)
- **BucketMetadataTableConfiguration** — new value object for metadata table configuration (table name, keys, inventory/journal settings)

Operations that reuse existing concepts (RenameObject = copy+delete, UpdateObjectEncryption = metadata update) should not introduce new domain types.

### 4. Handler Organization

| Handler Class | Operations |
|---|---|
| `S3SessionHandler` | CreateSession |
| `S3BucketOperationsHandler` | ListDirectoryBuckets |
| `S3ObjectOperationsHandler` | RenameObject, GetObjectTorrent, RestoreObject, SelectObjectContent, WriteGetObjectResponse |
| `S3ObjectMetadataHandler` | UpdateObjectEncryption, GetObjectLegalHold, PutObjectLegalHold, GetObjectRetention, PutObjectRetention |
| `S3BucketConfigHandler` | GetBucketAbac, PutBucketAbac, GetObjectLockConfiguration, PutObjectLockConfiguration, CreateBucketMetadataConfiguration, DeleteBucketMetadataConfiguration, GetBucketMetadataConfiguration, CreateBucketMetadataTableConfiguration, DeleteBucketMetadataTableConfiguration, GetBucketMetadataTableConfiguration, UpdateBucketMetadataInventoryTableConfiguration, UpdateBucketMetadataJournalTableConfiguration |

## Consequences

- **Positive**: Batched approach allowed incremental implementation and testing. Each batch was verified with Cucumber before Phase F closure.
- **Positive**: Reusing established patterns minimized architectural drift. New domain types remained limited to genuinely new S3 concepts.
- **Positive**: Handler organization stayed within existing responsibilities: session creation in `S3SessionHandler`, object operations/metadata split between `S3ObjectOperationsHandler` and `S3ObjectMetadataHandler`, and bucket metadata configuration in `S3BucketConfigHandler`.
- **Negative**: Some operations (SelectObjectContent, WriteGetObjectResponse) require simplified compatibility behavior rather than full AWS feature parity.
- **Risk**: Bucket metadata configuration operations are available through the S3 adapter; deeper storage-engine integration remains future architecture work.

## Implementation Note

Phase F is implemented and Cucumber-tested across all five batches:

1. Batch 1 added simple advanced object operations: RenameObject, UpdateObjectEncryption, GetObjectTorrent, and RestoreObject.
2. Batch 2 added session/configuration-like operations: CreateSession, ListDirectoryBuckets, GetBucketAbac, and PutBucketAbac.
3. Batch 3 added legal hold, object lock, and retention operations.
4. Batch 4 added SelectObjectContent and WriteGetObjectResponse using existing object operation handler infrastructure.
5. Batch 5 added bucket metadata and metadata table configuration operations in `S3BucketConfigHandler`.

Verification result: `mvn test -pl s3-reactive-api-adapter -am -Dsurefire.failIfNoSpecifiedTests=false` => 227 tests, 0 failures, 0 errors.

## Status

Accepted

## Date

2026-05-26
