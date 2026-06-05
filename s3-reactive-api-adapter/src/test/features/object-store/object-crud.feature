Feature: S3-compatible Object CRUD

  Background:
    Given bucket "test-bucket" exists

  # ── Success scenarios ──

  Scenario: Get an object
    Given object "hello.txt" exists with content "Hello Magrathea!"
    When the object is retrieved via S3 API
    Then the response status is 200
    And the content is "Hello Magrathea!"

  # ── G13: GetObject with SSE/checksum header echo ──

  Scenario: GetObject echoes SSE and checksum headers
    Given an object with key "sse-echo-test.txt" and content "data"
    When the object is stored via S3 API with multiple headers
      | x-amz-server-side-encryption | AES256 |
      | x-amz-checksum-sha256 | deadbeef |
    Then the response status is 200
    And GET response contains SSE header "x-amz-server-side-encryption" with value "AES256"
    And GET response contains checksum header "x-amz-checksum-sha256" with value "deadbeef"

  Scenario: Head object
    Given object "hello.txt" exists
    When HEAD request is sent for object "hello.txt"
    Then the response status is 200

  Scenario: List objects V2
    Given object "hello.txt" exists
    When the objects are listed via S3 API V2
    Then the response status is 200
    And the object appears in the object list V2

  Scenario: Copy an object
    Given object "hello.txt" exists with content "Hello Magrathea!"
    When object "hello.txt" is copied to "copy.txt"
    Then the response status is 200
    And object "copy.txt" content is "Hello Magrathea!"

  Scenario: List object versions
    Given object "hello.txt" exists
    When object versions are listed via S3 API
    Then the response status is 200
    And the versions response contains object "hello.txt"

  Scenario: Delete multiple objects
    Given object "hello.txt" exists
    And object "copy.txt" exists
    When objects are deleted via S3 API multi-delete
      | hello.txt |
      | copy.txt  |
    Then the response status is 200
    And object "hello.txt" does not appear in the object list
    And object "copy.txt" does not appear in the object list

  Scenario: Delete an object
    Given object "hello.txt" exists
    When the object is deleted via S3 API
    Then the object no longer appears in the object list

  # ── Batch 1 Phase F operations ──

  Scenario: Rename an object
    Given object "rename-me.txt" exists with content "Rename me!"
    When object "rename-me.txt" is renamed to "renamed.txt"
    Then the response status is 200
    And object "rename-me.txt" does not appear in the object list
    And object "renamed.txt" content is "Rename me!"

  Scenario: Rename object to nonexistent destination
    Given object "rename-me-2.txt" exists with content "Still here"
    When object "rename-me-2.txt" is renamed to "renamed.txt"
    Then the response status is 200
    And object "renamed.txt" content is "Still here"

  Scenario: Update object encryption
    Given object "encrypted-file.txt" exists with content "Encrypt me"
    When encryption is updated for object "encrypted-file.txt" with SSE algorithm "AES256"
    Then the response status is 200

  Scenario: Update object encryption with KMS key
    Given object "kms-file.txt" exists with content "KMS encrypt"
    When encryption is updated for object "kms-file.txt" with SSE algorithm "aws:kms" and KMS key "arn:aws:kms:us-east-1:123:key/test"
    Then the response status is 200

  Scenario: Get object torrent
    Given object "torrent-file.txt" exists with content "Torrent data"
    When the torrent is retrieved for object "torrent-file.txt"
    Then the response status is 200

  Scenario: Restore an object
    Given object "restore-me.txt" exists with content "Restore me"
    When the object is restored via S3 API with tier "Standard" and days 30
    Then the response status is 200

  Scenario: Restore object from Glacier with tier
    Given object "glacier-restore.txt" exists with content "Glacier data"
    When the object is restored via S3 API with Glacier tier "Bulk" and days 60
    Then the response status is 200

  # ── Failure scenarios ──

  Scenario: Rename nonexistent object
    Given object "ghost-rename.txt" does not exist
    When object "ghost-rename.txt" is renamed to "still-ghost.txt"
    Then the response status is 404

  Scenario: Update encryption for nonexistent object
    Given object "ghost-encrypt.txt" does not exist
    When encryption is updated for object "ghost-encrypt.txt" with SSE algorithm "AES256"
    Then the response status is 404

  Scenario: Get torrent for nonexistent object
    When the torrent is retrieved for object "ghost-torrent.txt"
    Then the response status is 404

  Scenario: Restore nonexistent object
    When the object with key "ghost-restore.txt" is restored via S3 API
    Then the response status is 404

  Scenario: Get nonexistent object
    When the object with key "ghost.txt" is retrieved via S3 API
    Then the response status is 404

  Scenario: Head object (not found)
    Given object "nonexistent.txt" does not exist
    When HEAD request is sent for object "nonexistent.txt"
    Then the response status is 404

  Scenario: Copy object with nonexistent source
    When object "ghost.txt" is copied to "still-ghost.txt"
    Then the response status is 404

  Scenario: Copy object to nonexistent target bucket
    When object "hello.txt" is copied to "copy.txt" in bucket "no-such-bucket"
    Then the response status is 404

  Scenario: Delete nonexistent object
    Given object "ghost.txt" does not exist
    When the object is deleted via S3 API
    Then the response status is 204

  # ── Service/repository error scenarios ──

  Scenario: Copy object with nonexistent source returns error from service
    Given object "ghost-source.txt" does not exist
    When object "ghost-source.txt" is copied to "still-ghost.txt"
    Then the response status is 404

  Scenario: Restore nonexistent object returns error from service
    Given object "ghost-restore-svc.txt" does not exist
    When the object with key "ghost-restore-svc.txt" is restored via S3 API
    Then the response status is 404

  Scenario: Delete object returns 204 even when already deleted (idempotent)
    Given object "already-deleted.txt" exists
    When the object is deleted via S3 API
    Then the object no longer appears in the object list
    And the response status is 204
    When the object is deleted via S3 API
    Then the response status is 204
