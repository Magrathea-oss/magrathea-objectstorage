@requirement @single-node-backend @object-crud @extended @webclient-required
Business Need: Extended object operations against the single-node in-memory backend
  As an S3-compatible client using WebTestClient against a single-node Magrathea
  deployment,
  I want CopyObject, ListObjectVersions, multi-object DeleteObjects, RenameObject,
  UpdateObjectEncryption, GetObjectTorrent, and RestoreObject to behave correctly, or
  to have their real limitations documented rather than hidden behind a passing
  status-code check,
  So that client integrations relying on these operations know precisely which ones
  are genuinely implemented, which are config-only, and which are placeholders.

  This feature shares the single-node-backend validation scope described in
  single-node-backend-bucket-operations.feature (no "storage-engine" profile active,
  InMemoryReactiveS3ObjectRepository backs the object repository). It was extracted
  from the legacy features/object-store/object-crud.feature, which mixed genuine
  extended-operation coverage with basic CRUD duplicates (now paired under existing
  REQ-SINGLENODE-CRUD-* IDs in single-node-backend-object-crud.feature) and two
  scenarios that duplicated an existing 404 scenario in the same legacy file under a
  different name ("... returns error from service" variants of the copy-nonexistent-
  source and restore-nonexistent-object checks) — these duplicates were dropped, not
  migrated.

  Two production-code findings from this review upgraded scenario classifications
  beyond what Phase 1 tagging alone would have concluded from Gherkin text:

  - GetObjectTorrent (`ReactiveObjectService.getObjectTorrent` →
    `InMemoryReactiveS3ObjectRepository.findTorrent`) returns a hardcoded string,
    "Placeholder torrent file for %s/%s ... mock torrent response for S3 API
    compatibility," regardless of the object's real content. Tagged `@placeholder`,
    not `@protocol-smoke`.
  - RestoreObject (`S3ObjectOperationsHandler.restoreObject`) parses the
    RestoreObjectCommand request body (tier, days) but never uses it: it re-saves
    the existing object unchanged and returns 200. No restore-in-progress or
    restore-completed state is ever tracked. Tagged `@placeholder`, not
    `@protocol-smoke`.

  Rule: GetObject echoes SSE and checksum headers that were set at PutObject time

    @REQ-SINGLENODE-EXT-016 @functional-requirement @webclient-required
    Scenario: GetObject echoes SSE and checksum headers
      Given bucket "test-bucket" exists
      And an object with key "sse-echo-test.txt" and content "data"
      When the object is stored via S3 API with multiple headers
        | x-amz-server-side-encryption | AES256 |
        | x-amz-checksum-sha256 | deadbeef |
      Then the response status is 200
      And GET response contains SSE header "x-amz-server-side-encryption" with value "AES256"
      And GET response contains checksum header "x-amz-checksum-sha256" with value "deadbeef"

  Rule: CopyObject must copy content to a new key and reject a nonexistent source or target bucket

    @REQ-SINGLENODE-EXT-001 @functional-requirement @webclient-required
    Scenario: Copy an object
      Given bucket "test-bucket" exists
      And object "hello.txt" exists with content "Hello Magrathea!"
      When object "hello.txt" is copied to "copy.txt"
      Then the response status is 200
      And object "copy.txt" content is "Hello Magrathea!"

    @REQ-SINGLENODE-EXT-002 @protocol-smoke @webclient-required
    Scenario: Copy object with nonexistent source
      Given bucket "test-bucket" exists
      When object "ghost.txt" is copied to "still-ghost.txt"
      Then the response status is 404

    @REQ-SINGLENODE-EXT-003 @absent @webclient-required
    Scenario: CopyObject does not validate that the destination bucket exists
      Given bucket "test-bucket" exists
      And object "hello.txt" exists with content "Hello Magrathea!"
      When object "hello.txt" is copied to "copy.txt" in bucket "no-such-bucket"
      Then the response status is 200
      # Planner-verified finding (2026-07-07): S3ObjectOperationsHandler.copyObjectWithContent
      # never checks whether the destination bucket exists before calling
      # objectService.saveObjectWithContent(). The copy silently succeeds and creates
      # an object keyed to a bucket that was never created. The legacy scenario this
      # was migrated from asserted 404 here, but it passed only because it omitted
      # the "object hello.txt exists" precondition: the *source* object did not
      # exist either, so the 404 came from a missing source, not destination-bucket
      # validation — the destination-bucket check this scenario claimed to cover was
      # never actually exercised. This scenario now documents the real, current
      # behavior instead of an assertion that happened to pass for the wrong reason.

  Rule: ListObjectVersions must reflect current objects, without implying real multi-version history

    @REQ-SINGLENODE-EXT-004 @functional-requirement @webclient-required
    Scenario: List object versions
      Given bucket "test-bucket" exists
      And object "hello.txt" exists
      When object versions are listed via S3 API
      Then the response status is 200
      And the versions response contains object "hello.txt"
      # Each stored object is listed as its own single "version" entry; true
      # multi-version history is not tracked (versioning enforcement is not
      # implemented — see REQ-S3-007-A/B in phase-5-s3-semantic-compatibility.feature).

  Rule: Multi-object DeleteObjects must remove every listed key

    @REQ-SINGLENODE-EXT-005 @functional-requirement @webclient-required
    Scenario: Delete multiple objects
      Given bucket "test-bucket" exists
      And object "hello.txt" exists
      And object "copy.txt" exists
      When objects are deleted via S3 API multi-delete
        | hello.txt |
        | copy.txt  |
      Then the response status is 200
      And object "hello.txt" does not appear in the object list
      And object "copy.txt" does not appear in the object list

  Rule: RenameObject must move an object to a new key, including onto a nonexistent destination

    @REQ-SINGLENODE-EXT-006 @functional-requirement @webclient-required
    Scenario: Rename an object
      Given bucket "test-bucket" exists
      And object "rename-me.txt" exists with content "Rename me!"
      When object "rename-me.txt" is renamed to "renamed.txt"
      Then the response status is 200
      And object "rename-me.txt" does not appear in the object list
      And object "renamed.txt" content is "Rename me!"

    @REQ-SINGLENODE-EXT-007 @functional-requirement @webclient-required
    Scenario: Rename object to nonexistent destination
      Given bucket "test-bucket" exists
      And object "rename-me-2.txt" exists with content "Still here"
      When object "rename-me-2.txt" is renamed to "renamed.txt"
      Then the response status is 200
      And object "renamed.txt" content is "Still here"

    @REQ-SINGLENODE-EXT-008 @protocol-smoke @webclient-required
    Scenario: Rename nonexistent object
      Given bucket "test-bucket" exists
      And object "ghost-rename.txt" does not exist
      When object "ghost-rename.txt" is renamed to "still-ghost.txt"
      Then the response status is 404

  Rule: UpdateObjectEncryption persists an encryption configuration without enforcing encryption at rest

    @REQ-SINGLENODE-EXT-009 @config-only @webclient-required
    Scenario: Update object encryption
      Given bucket "test-bucket" exists
      And object "encrypted-file.txt" exists with content "Encrypt me"
      When encryption is updated for object "encrypted-file.txt" with SSE algorithm "AES256"
      Then the response status is 200

    @REQ-SINGLENODE-EXT-010 @config-only @webclient-required
    Scenario: Update object encryption with KMS key
      Given bucket "test-bucket" exists
      And object "kms-file.txt" exists with content "KMS encrypt"
      When encryption is updated for object "kms-file.txt" with SSE algorithm "aws:kms" and KMS key "arn:aws:kms:us-east-1:123:key/test"
      Then the response status is 200

    @REQ-SINGLENODE-EXT-011 @protocol-smoke @webclient-required
    Scenario: Update encryption for nonexistent object
      Given bucket "test-bucket" exists
      And object "ghost-encrypt.txt" does not exist
      When encryption is updated for object "ghost-encrypt.txt" with SSE algorithm "AES256"
      Then the response status is 404

  Rule: GetObjectTorrent returns a fixed placeholder response, not a real torrent derived from object content

    @REQ-SINGLENODE-EXT-012 @placeholder @webclient-required
    Scenario: Get object torrent
      Given bucket "test-bucket" exists
      And object "torrent-file.txt" exists with content "Torrent data"
      When the torrent is retrieved for object "torrent-file.txt"
      Then the response status is 200
      # Response body is a hardcoded placeholder string, not a real BitTorrent file
      # derived from "Torrent data". See feature-level note above.

    @REQ-SINGLENODE-EXT-013 @protocol-smoke @webclient-required
    Scenario: Get torrent for nonexistent object
      Given bucket "test-bucket" exists
      When the torrent is retrieved for object "ghost-torrent.txt"
      Then the response status is 404

  Rule: RestoreObject accepts a restore request but does not track restore state or honor tier/duration

    @REQ-SINGLENODE-EXT-014 @placeholder @webclient-required
    Scenario: Restore an object
      Given bucket "test-bucket" exists
      And object "restore-me.txt" exists with content "Restore me"
      When the object is restored via S3 API with tier "Standard" and days 30
      Then the response status is 200
      # The request body (tier, days) is parsed but discarded; the handler re-saves
      # the object unchanged. No restore-in-progress or restore-completed state is
      # ever tracked or observable. See feature-level note above.

    @REQ-SINGLENODE-EXT-014 @placeholder @webclient-required
    Scenario: Restore object from Glacier with tier
      Given bucket "test-bucket" exists
      And object "glacier-restore.txt" exists with content "Glacier data"
      When the object is restored via S3 API with Glacier tier "Bulk" and days 60
      Then the response status is 200

    @REQ-SINGLENODE-EXT-015 @protocol-smoke @webclient-required
    Scenario: Restore nonexistent object
      Given bucket "test-bucket" exists
      When the object with key "ghost-restore.txt" is restored via S3 API
      Then the response status is 404
