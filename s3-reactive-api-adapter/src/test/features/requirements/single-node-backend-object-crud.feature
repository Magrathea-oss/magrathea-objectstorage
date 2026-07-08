@requirement @single-node-backend @object-crud
Business Need: Object CRUD parity against the single-node in-memory backend
  As an S3-compatible client using WebTestClient or the AWS CLI against a single-node
  Magrathea deployment,
  I want PutObject, GetObject, HeadObject, ListObjects/ListObjectsV2, and
  DeleteObject to behave correctly, including nested keys with slashes, object
  overwrite, and idempotent delete, against the single-node in-memory backend,
  So that basic object CRUD is correct in single-node/development mode, without
  requiring the storage-engine profile to be active.

  This feature shares the single-node-backend validation scope described in
  single-node-backend-bucket-operations.feature: per ADR-0014, "single-node" is the
  Spring-default profile intended for development and single-node/test deployments,
  not durable production storage; no "storage-engine" profile is active here, so the
  S3 object repository resolves to InMemoryReactiveS3ObjectRepository rather than the
  filesystem-backed storage engine. Durability, restart-safety, and integrity
  requirements for the storage-engine backend are covered separately by
  phase-1-upload-storage-engine.feature; ETag correctness, byte-range, and
  conditional-request semantics are covered separately by
  phase-5-s3-semantic-compatibility.feature. This feature intentionally does not
  duplicate those deeper semantic assertions; it validates that the same operations
  succeed against the single-node/development configuration.

  Header/metadata/checksum handling edge cases (SSE, SigV4 non-validation, storage
  class value acceptance beyond a fixed enum) are covered separately by
  single-node-backend-put-object-header-handling.feature.

  Rule: PutObject and GetObject must round-trip object content correctly

    @REQ-SINGLENODE-CRUD-001 @functional-requirement @awscli-required
    Scenario: AWS CLI put-object succeeds with default headers
      Given the S3 API is running with the single-node in-memory backend and no storage-engine profile active
      And bucket "test-bucket" exists
      And an object with key "aws-cli-default.txt" and content "AWS CLI default test"
      When the object is stored via AWS CLI with default headers
      Then the AWS CLI exit code is 0
      And the object appears in the object list

    @REQ-SINGLENODE-CRUD-001 @functional-requirement @webclient-required
    Scenario: Put an object
      Given bucket "test-bucket" exists
      And an object with key "hello.txt" and content "Hello Magrathea!"
      When the object is stored via S3 API
      Then the response status is 200
      And the object appears in the object list

    @REQ-SINGLENODE-CRUD-002 @functional-requirement @awscli-required
    Scenario: AWS CLI get-object reads stored content
      Given the S3 API is running with the single-node in-memory backend and no storage-engine profile active
      And bucket "test-bucket" exists
      And object "aws-cli-get.txt" exists with content "Hello from AWS CLI"
      When the object with key "aws-cli-get.txt" is retrieved via S3 API
      Then the response status is 200
      And the content is "Hello from AWS CLI"

    @REQ-SINGLENODE-CRUD-002 @functional-requirement @webclient-required
    Scenario: Get an object
      Given bucket "test-bucket" exists
      And object "hello.txt" exists with content "Hello Magrathea!"
      When the object is retrieved via S3 API
      Then the response status is 200
      And the content is "Hello Magrathea!"

    @REQ-SINGLENODE-CRUD-011 @protocol-smoke @webclient-required
    Scenario: Get nonexistent object
      When the object with key "ghost.txt" is retrieved via S3 API
      Then the response status is 404

    @REQ-SINGLENODE-CRUD-003 @protocol-smoke @awscli-required
    Scenario: AWS CLI head-object finds an existing object
      Given the S3 API is running with the single-node in-memory backend and no storage-engine profile active
      And bucket "test-bucket" exists
      And object "aws-cli-head.txt" exists with content "head me"
      When HEAD request is sent for object "aws-cli-head.txt"
      Then the response status is 200

    @REQ-SINGLENODE-CRUD-003 @protocol-smoke @webclient-required
    Scenario: Head object
      Given bucket "test-bucket" exists
      And object "hello.txt" exists
      When HEAD request is sent for object "hello.txt"
      Then the response status is 200

    @REQ-SINGLENODE-CRUD-012 @protocol-smoke @webclient-required
    Scenario: Head object (not found)
      Given object "nonexistent.txt" does not exist
      When HEAD request is sent for object "nonexistent.txt"
      Then the response status is 404

    @REQ-SINGLENODE-CRUD-004 @functional-requirement @awscli-required
    Scenario: AWS CLI put-object stores STANDARD storage class
      Given the S3 API is running with the single-node in-memory backend and no storage-engine profile active
      And bucket "test-bucket" exists
      And an object with key "aws-cli-standard.txt" and content "standard storage"
      When the object is stored via S3 API with storage class "STANDARD"
      Then the response status is 200
      When object attributes are requested via AWS CLI for "aws-cli-standard.txt"
      Then the AWS CLI object attributes include storage class "STANDARD"

    @REQ-SINGLENODE-CRUD-004 @functional-requirement @webclient-required
    Scenario: PutObject with storage class STANDARD
      Given bucket "test-bucket" exists
      And an object with key "standard-test.txt" and content "data"
      When the object is stored via S3 API with storage class "STANDARD"
      Then the response status is 200
      And the object attributes returned include storage class "STANDARD"

    @REQ-SINGLENODE-CRUD-005 @functional-requirement @awscli-required
    Scenario: AWS CLI put-object with a key containing slashes
      Given the S3 API is running with the single-node in-memory backend and no storage-engine profile active
      And bucket "test-bucket" exists
      And an object with key "folder/aws-cli-slash.txt" and content "nested key"
      When the object is stored via AWS CLI with default headers
      Then the AWS CLI exit code is 0
      When the object with key "folder/aws-cli-slash.txt" is retrieved via S3 API
      Then the response status is 200
      And the content is "nested key"
      When the objects are listed via AWS CLI
      Then the response status is 200
      And the AWS CLI output contains object "folder/aws-cli-slash.txt"
      When the object with key "folder/aws-cli-slash.txt" is deleted via AWS CLI
      Then the response status is 204
      And object "folder/aws-cli-slash.txt" does not appear in the object list

    @REQ-SINGLENODE-CRUD-005 @functional-requirement @webclient-required
    Scenario: Put, read, list, and delete an object with a key containing slashes
      Given bucket "test-bucket" exists
      And an object with key "folder/webclient-slash.txt" and content "nested WebTestClient key"
      When the object is stored via S3 API using an explicit slash-preserving URI
      Then the response status is 200
      When the object with key "folder/webclient-slash.txt" is retrieved via S3 API using an explicit slash-preserving URI
      Then the response status is 200
      And the content is "nested WebTestClient key"
      When the objects are listed via S3 API V2
      Then the response status is 200
      And the object appears in the object list V2
      When the object with key "folder/webclient-slash.txt" is deleted via S3 API using an explicit slash-preserving URI
      Then the response status is 204
      And object "folder/webclient-slash.txt" does not appear in the object list

  Rule: ListObjects and ListObjectsV2 must reflect stored objects

    @REQ-SINGLENODE-CRUD-006 @functional-requirement @awscli-required
    Scenario: AWS CLI list-objects shows a stored object
      Given the S3 API is running with the single-node in-memory backend and no storage-engine profile active
      And bucket "test-bucket" exists
      And object "aws-cli-list.txt" exists with content "list me"
      When the objects are listed via AWS CLI
      Then the response status is 200
      And the AWS CLI output contains object "aws-cli-list.txt"

    @REQ-SINGLENODE-CRUD-007 @functional-requirement @awscli-required
    Scenario: AWS CLI list-objects-v2 shows a stored object
      Given the S3 API is running with the single-node in-memory backend and no storage-engine profile active
      And bucket "test-bucket" exists
      And object "aws-cli-list-v2.txt" exists with content "list me v2"
      When the objects are listed via AWS CLI V2
      Then the response status is 200
      And the AWS CLI output contains object "aws-cli-list-v2.txt"

    @REQ-SINGLENODE-CRUD-007 @functional-requirement @webclient-required
    Scenario: List objects V2
      Given bucket "test-bucket" exists
      And object "hello.txt" exists
      When the objects are listed via S3 API V2
      Then the response status is 200
      And the object appears in the object list V2

  Rule: DeleteObject must remove the object and be idempotent when repeated

    @REQ-SINGLENODE-CRUD-008 @functional-requirement @awscli-required
    Scenario: AWS CLI delete-object removes an existing object
      Given the S3 API is running with the single-node in-memory backend and no storage-engine profile active
      And bucket "test-bucket" exists
      And object "aws-cli-delete.txt" exists with content "delete me"
      When the object with key "aws-cli-delete.txt" is deleted via AWS CLI
      Then the response status is 204
      And object "aws-cli-delete.txt" does not appear in the object list

    @REQ-SINGLENODE-CRUD-008 @functional-requirement @webclient-required
    Scenario: Delete an object
      Given bucket "test-bucket" exists
      And object "hello.txt" exists
      When the object is deleted via S3 API
      Then the object no longer appears in the object list

    @REQ-SINGLENODE-CRUD-009 @functional-requirement @non-functional-requirement @awscli-required
    Scenario: AWS CLI delete-object is idempotent for an already deleted object
      Given the S3 API is running with the single-node in-memory backend and no storage-engine profile active
      And bucket "test-bucket" exists
      And object "aws-cli-idempotent-delete.txt" exists with content "delete me twice"
      When the object with key "aws-cli-idempotent-delete.txt" is deleted via AWS CLI
      Then the response status is 204
      When the object with key "aws-cli-idempotent-delete.txt" is deleted via AWS CLI
      Then the response status is 204
      And object "aws-cli-idempotent-delete.txt" does not appear in the object list

    @REQ-SINGLENODE-CRUD-009 @functional-requirement @non-functional-requirement @webclient-required
    Scenario: Delete object returns 204 even when already deleted (idempotent)
      Given bucket "test-bucket" exists
      And object "already-deleted.txt" exists
      When the object is deleted via S3 API
      Then the object no longer appears in the object list
      And the response status is 204
      When the object is deleted via S3 API
      Then the response status is 204

  Rule: Overwriting an existing object must replace its content and headers, and the object must become unreachable after delete

    @REQ-SINGLENODE-CRUD-010 @functional-requirement @webclient-required
    Scenario: Object lifecycle — create, read, overwrite, delete
      Given bucket "test-bucket" exists
      And an object with key "sm-lifecycle.txt" and content "First version"
      When the object is stored via S3 API
      Then the response status is 200
      When HEAD request is sent for object "sm-lifecycle.txt"
      Then the response status is 200
      When the object with key "sm-lifecycle.txt" is retrieved via S3 API
      Then the response status is 200
      And the content is "First version"
      When the object is stored via S3 API with header "x-amz-checksum-sha256" value "overwrite-hash"
      Then the response status is 200
      When HEAD request is sent for object "sm-lifecycle.txt"
      Then the response status is 200
      When the object is deleted via S3 API
      Then the response status is 204
      When HEAD request is sent for object "sm-lifecycle.txt"
      Then the response status is 404
