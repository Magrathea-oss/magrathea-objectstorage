@requirement @single-node-backend @metadata @acl @tagging
Business Need: Bucket and object ACL, tagging, and attributes against the single-node in-memory backend
  As an S3-compatible client using WebTestClient or the AWS CLI against a single-node
  Magrathea deployment,
  I want PutBucketAcl/GetBucketAcl, PutObjectAcl/GetObjectAcl, bucket and object
  tagging, and GetObjectAttributes to be reachable and return well-formed responses
  against the single-node in-memory backend,
  So that ACL and tagging tooling does not fail outright, even where full ACL/tagging
  enforcement is not yet implemented.

  This feature shares the single-node-backend validation scope described in
  single-node-backend-bucket-operations.feature: per ADR-0014, "single-node" is the
  Spring-default profile intended for development and single-node/test deployments,
  not durable production storage; no "storage-engine" profile is active here, so the
  in-memory reactive repositories are used.

  Several AWS CLI scenarios in this feature are intentionally tagged @placeholder:
  they confirm the AWS CLI round-trip succeeds (exit code 0) but do not yet assert
  that the applied ACL or tag value is actually persisted and returned unchanged.
  That deeper persistence assertion is tracked as a follow-up scope expansion.

  Known validation-mode gap: bucket and object tagging currently have a successful
  round-trip scenario only in the AWS CLI runner (REQ-SINGLENODE-TAGGING-001/002).
  No WebTestClient success-path scenario for tagging exists yet; only the tagging
  404-for-nonexistent-resource error paths are covered by WebTestClient
  (REQ-SINGLENODE-TAGGING-003..007). Adding a WebTestClient tagging success scenario
  is tracked as a follow-up scope expansion, not implied by current coverage.

  Rule: Bucket and object ACL round-trip without error

    @REQ-SINGLENODE-ACL-001 @placeholder @awscli-required
    Scenario: Bucket ACL can be set and retrieved via AWS CLI
      Given the S3 API is running with the single-node in-memory backend and no storage-engine profile active
      And bucket "awscli-acl-bucket" exists
      When bucket ACL "public-read" is applied via AWS CLI to "awscli-acl-bucket"
      Then the AWS CLI exit code is 0
      When bucket ACL is requested via AWS CLI for "awscli-acl-bucket"
      Then the AWS CLI exit code is 0

    @REQ-SINGLENODE-ACL-001 @functional-requirement @webclient-required
    Scenario: Bucket ACL can be updated and read
      Given bucket "test-bucket" exists
      When bucket ACL "public-read" is applied to "test-bucket"
      Then the response status is 200
      When bucket ACL is requested for "test-bucket"
      Then the response status is 200
      And the metadata response contains "READ"

    @REQ-SINGLENODE-ACL-002 @placeholder @awscli-required
    Scenario: Object ACL can be set and retrieved via AWS CLI
      Given the S3 API is running with the single-node in-memory backend and no storage-engine profile active
      And bucket "awscli-obj-acl-bucket" exists
      And object "awscli-acl-object.txt" exists with content "acl test body"
      When object ACL "public-read" is applied via AWS CLI to "awscli-acl-object.txt"
      Then the AWS CLI exit code is 0
      When object ACL is requested via AWS CLI for "awscli-acl-object.txt"
      Then the AWS CLI exit code is 0

    @REQ-SINGLENODE-ACL-002 @functional-requirement @webclient-required
    Scenario: Object ACL can be updated and read
      Given bucket "test-bucket" exists
      And object "hello.txt" exists
      When object ACL "public-read" is applied to "hello.txt"
      Then the response status is 200
      When object ACL is requested for "hello.txt"
      Then the response status is 200
      And the object metadata response contains "READ"

    @REQ-SINGLENODE-ACL-003 @protocol-smoke @webclient-required
    Scenario: Get bucket ACL for nonexistent bucket
      When bucket ACL is requested for "ghost-bucket"
      Then the response status is 404

    @REQ-SINGLENODE-ACL-004 @protocol-smoke @webclient-required
    Scenario: Put bucket ACL for nonexistent bucket
      When bucket ACL "private" is applied to "ghost-bucket"
      Then the response status is 404

    @REQ-SINGLENODE-ACL-005 @protocol-smoke @webclient-required
    Scenario: Get object ACL for nonexistent object
      Given bucket "test-bucket" exists
      When object ACL is requested for "ghost.txt"
      Then the response status is 404

    @REQ-SINGLENODE-ACL-006 @protocol-smoke @webclient-required
    Scenario: Put object ACL for nonexistent object
      Given bucket "test-bucket" exists
      When object ACL "private" is applied to "ghost.txt"
      Then the response status is 404

  Rule: Bucket and object tagging round-trip without error

    @REQ-SINGLENODE-TAGGING-001 @placeholder @awscli-required
    Scenario: Bucket tagging operations via AWS CLI
      Given the S3 API is running with the single-node in-memory backend and no storage-engine profile active
      And bucket "awscli-tag-bucket" exists
      When bucket tag "env" = "test" is applied via AWS CLI to "awscli-tag-bucket"
      Then the AWS CLI exit code is 0
      When bucket tags are requested via AWS CLI for "awscli-tag-bucket"
      Then the AWS CLI exit code is 0
      When bucket tags are deleted via AWS CLI for "awscli-tag-bucket"
      Then the AWS CLI exit code is 0

    @REQ-SINGLENODE-TAGGING-002 @placeholder @awscli-required
    Scenario: Object tagging operations via AWS CLI
      Given the S3 API is running with the single-node in-memory backend and no storage-engine profile active
      And bucket "awscli-obj-tag-bucket" exists
      And object "awscli-tag-object.txt" exists with content "tag test body"
      When object tag "env" = "test" is applied via AWS CLI to "awscli-tag-object.txt"
      Then the AWS CLI exit code is 0
      When object tags are requested via AWS CLI for "awscli-tag-object.txt"
      Then the AWS CLI exit code is 0
      When object tags are deleted via AWS CLI for "awscli-tag-object.txt"
      Then the AWS CLI exit code is 0

    @REQ-SINGLENODE-TAGGING-003 @protocol-smoke @webclient-required
    Scenario: Get bucket tagging for nonexistent bucket
      When bucket tags are requested for "ghost-bucket"
      Then the response status is 404

    @REQ-SINGLENODE-TAGGING-004 @protocol-smoke @webclient-required
    Scenario: Put bucket tagging for nonexistent bucket
      When bucket tag "x" = "y" is applied to "ghost-bucket"
      Then the response status is 404

    @REQ-SINGLENODE-TAGGING-005 @protocol-smoke @webclient-required
    Scenario: Delete bucket tagging for nonexistent bucket
      When bucket tags are deleted for "ghost-bucket"
      Then the response status is 404

    @REQ-SINGLENODE-TAGGING-006 @protocol-smoke @webclient-required
    Scenario: Get object tagging for nonexistent object
      Given bucket "test-bucket" exists
      When object tags are requested for "ghost.txt"
      Then the response status is 404

    @REQ-SINGLENODE-TAGGING-007 @protocol-smoke @webclient-required
    Scenario: Put object tagging for nonexistent object
      Given bucket "test-bucket" exists
      When object tag "x" = "y" is applied to "ghost.txt"
      Then the response status is 404

    @REQ-SINGLENODE-TAGGING-008 @protocol-smoke @webclient-required
    Scenario: Delete object tagging for nonexistent object
      Given bucket "test-bucket" exists
      When object tags are deleted for "ghost.txt"
      Then the response status is 404

  Rule: GetObjectAttributes must include ETag and ObjectSize fields

    @REQ-SINGLENODE-ATTRS-001 @protocol-smoke @awscli-required
    Scenario: Object attributes include ETag and ObjectSize via AWS CLI
      Given the S3 API is running with the single-node in-memory backend and no storage-engine profile active
      And bucket "awscli-attrs-bucket" exists
      And object "awscli-attrs-object.txt" exists with content "attributes test body"
      When object attributes are requested via AWS CLI for "awscli-attrs-object.txt" including ETag and ObjectSize
      Then the AWS CLI exit code is 0
      And the AWS CLI output contains "ETag"
      And the AWS CLI output contains "ObjectSize"

    @REQ-SINGLENODE-ATTRS-001 @functional-requirement @webclient-required
    Scenario: Object attributes can be read
      Given bucket "test-bucket" exists
      And object "hello.txt" exists with content "Hello Magrathea!"
      When object attributes are requested for "hello.txt"
      Then the response status is 200
      And the object metadata response contains "ObjectSize"
