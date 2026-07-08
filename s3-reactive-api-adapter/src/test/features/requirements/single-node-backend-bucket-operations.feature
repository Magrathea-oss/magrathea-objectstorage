@requirement @single-node-backend @bucket-operations
Business Need: Bucket lifecycle operations against the single-node in-memory backend
  As an S3-compatible client using WebTestClient or the AWS CLI against a single-node
  Magrathea deployment,
  I want CreateBucket, HeadBucket, GetBucketLocation, GetBucketVersioning,
  PutBucketVersioning, DeleteBucket, and ListBuckets to behave correctly
  against the single-node in-memory backend,
  So that basic bucket lifecycle operations are correct in single-node/development
  mode, without requiring the storage-engine profile to be active.

  This feature is the single source of truth for single-node-backend bucket lifecycle
  requirements, validated through both WebTestClient and the AWS CLI. Per ADR-0014 and
  README.md, the "single-node" profile is the Spring-default profile
  (spring.profiles.default=single-node in the bootstrap application; Spring's own
  implicit "default" profile in test harnesses that set no application.properties),
  and it wires the in-memory reactive adapters (InMemoryReactiveS3ObjectRepository,
  InMemoryReactiveBucketRepository). This is explicitly documented as suitable for
  development and single-node/test deployments, not as a durable production backend —
  the durable, production-grade path is the storage-engine backend covered by
  phase-1-upload-storage-engine.feature and phase-5-s3-semantic-compatibility.feature.
  This feature intentionally validates the single-node/development configuration on
  its own terms, as a distinct backend-selection scope per AGENTS.md §B.4, not as
  "the production default."

  Validation roles:
    - WebTestClient runner (SingleNodeBackendWebTestClientRequirementsCucumberTest)
      binds WebTestClient directly to the S3 RouterFunction (ObjectStoreTestApp,
      the same no-profile-active single-node configuration).
    - AWS CLI runner (SingleNodeBackendAwsCliRequirementsCucumberTest) drives the aws
      CLI binary against a real Netty HTTP server (AwsCliTestApp, RANDOM_PORT).
    - Both runners select this same shared feature; runner-specific step wording is
      isolated to each runner's own glue package, per AGENTS.md §A.6.

  Rule: CreateBucket, HeadBucket, and ListBuckets must reflect bucket existence consistently

    @REQ-SINGLENODE-BUCKET-001 @functional-requirement @awscli-required
    Scenario: AWS CLI create-bucket and list-buckets show the bucket
      Given the S3 API is running with the single-node in-memory backend and no storage-engine profile active
      And a bucket name "awscli-create-list-bucket"
      When the bucket is created via AWS CLI
      Then the AWS CLI exit code is 0
      When the buckets are listed via AWS CLI
      Then the response status is 200
      And the AWS CLI output contains bucket "awscli-create-list-bucket"

    @REQ-SINGLENODE-BUCKET-001 @functional-requirement @webclient-required
    Scenario: Create a bucket
      Given a bucket name "test-bucket"
      When the bucket is created via S3 API
      Then the response status is 200
      And the bucket appears in the bucket list

    @REQ-SINGLENODE-BUCKET-001 @functional-requirement @webclient-required
    Scenario: List buckets
      Given bucket "list-bucket-test" exists
      When the buckets are listed
      Then the response status is 200
      And the bucket appears in the bucket list

    @REQ-SINGLENODE-BUCKET-002 @protocol-smoke @awscli-required
    Scenario: AWS CLI head-bucket finds an existing bucket
      Given the S3 API is running with the single-node in-memory backend and no storage-engine profile active
      And bucket "awscli-head-bucket" exists
      When HEAD request is sent for bucket "awscli-head-bucket"
      Then the response status is 200

    @REQ-SINGLENODE-BUCKET-002 @protocol-smoke @webclient-required
    Scenario: Head bucket (exists)
      Given bucket "test-bucket" exists
      When HEAD request is sent for bucket "test-bucket"
      Then the response status is 200

    @REQ-SINGLENODE-BUCKET-003 @protocol-smoke @awscli-required
    Scenario: AWS CLI head-bucket fails for a nonexistent bucket
      Given the S3 API is running with the single-node in-memory backend and no storage-engine profile active
      And bucket "awscli-ghost-head-bucket" does not exist
      When HEAD request is sent for bucket "awscli-ghost-head-bucket"
      Then the AWS CLI exit code is non-zero
      And the AWS CLI output contains "404"

    @REQ-SINGLENODE-BUCKET-003 @protocol-smoke @webclient-required
    Scenario: Head bucket (not found)
      Given bucket "nonexistent" does not exist
      When HEAD request is sent for bucket "nonexistent"
      Then the response status is 404

    @REQ-SINGLENODE-BUCKET-004 @protocol-smoke @awscli-required
    Scenario: AWS CLI create-bucket fails for a duplicate bucket
      Given the S3 API is running with the single-node in-memory backend and no storage-engine profile active
      And bucket "awscli-duplicate-bucket" exists
      When the bucket is created via AWS CLI
      Then the AWS CLI exit code is non-zero

    @REQ-SINGLENODE-BUCKET-004 @protocol-smoke @webclient-required
    Scenario: Create duplicate bucket
      Given bucket "dup-bucket" exists
      And a bucket name "dup-bucket"
      When the bucket is created via S3 API
      Then the response status is 409

  Rule: GetBucketLocation must return the configured region for an existing bucket, and reject a nonexistent bucket

    @REQ-SINGLENODE-BUCKET-005 @functional-requirement @awscli-required
    Scenario: AWS CLI get-bucket-location returns the bucket region
      Given the S3 API is running with the single-node in-memory backend and no storage-engine profile active
      And bucket "awscli-location-bucket" exists
      When bucket location is requested via AWS CLI for "awscli-location-bucket"
      Then the response status is 200
      And the AWS CLI bucket location contains "us-east-1"

    @REQ-SINGLENODE-BUCKET-005 @functional-requirement @webclient-required
    Scenario: Get bucket location
      Given bucket "test-bucket" exists
      When bucket location is requested for "test-bucket"
      Then the response status is 200
      And the bucket location response contains "us-east-1"

    @REQ-SINGLENODE-BUCKET-010 @protocol-smoke @webclient-required
    Scenario: Get bucket location for nonexistent bucket
      Given bucket "ghost-bucket" does not exist
      When bucket location is requested for "ghost-bucket"
      Then the response status is 404

  Rule: GetBucketVersioning and PutBucketVersioning must report configuration state without enforcing versioning semantics

    @REQ-SINGLENODE-BUCKET-006 @functional-requirement @awscli-required
    Scenario: AWS CLI get-bucket-versioning returns suspended by default
      Given the S3 API is running with the single-node in-memory backend and no storage-engine profile active
      And bucket "awscli-versioning-default-bucket" exists
      When bucket versioning is requested via AWS CLI for "awscli-versioning-default-bucket"
      Then the response status is 200
      And the AWS CLI bucket versioning contains status "Suspended"

    @REQ-SINGLENODE-BUCKET-006 @functional-requirement @webclient-required
    Scenario: Get bucket versioning
      Given bucket "test-bucket" exists
      When bucket versioning is requested for "test-bucket"
      Then the response status is 200
      And the bucket versioning response contains "Suspended"

    @REQ-SINGLENODE-BUCKET-011 @protocol-smoke @webclient-required
    Scenario: Get bucket versioning for nonexistent bucket
      Given bucket "ghost-bucket" does not exist
      When bucket versioning is requested for "ghost-bucket"
      Then the response status is 404

    @REQ-SINGLENODE-BUCKET-007 @config-only @awscli-required
    Scenario: AWS CLI put-bucket-versioning enables versioning
      Given the S3 API is running with the single-node in-memory backend and no storage-engine profile active
      And bucket "awscli-versioning-enabled-bucket" exists
      When bucket versioning is set via AWS CLI for "awscli-versioning-enabled-bucket" to "Enabled"
      Then the response status is 200
      When bucket versioning is requested via AWS CLI for "awscli-versioning-enabled-bucket"
      Then the response status is 200
      And the AWS CLI bucket versioning contains status "Enabled"
      # Note: this validates config-only acceptance and readback. Object versioning
      # enforcement (version IDs, delete markers) is not implemented; see REQ-S3-007-B.

    @REQ-SINGLENODE-BUCKET-007 @config-only @webclient-required
    Scenario: Put bucket versioning
      Given bucket "test-bucket" exists
      When bucket versioning is enabled for "test-bucket"
      Then the response status is 200
      And bucket versioning for "test-bucket" is "Enabled"

    @REQ-SINGLENODE-BUCKET-012 @protocol-smoke @webclient-required
    Scenario: Put bucket versioning for nonexistent bucket
      Given bucket "ghost-bucket" does not exist
      When bucket versioning is enabled for "ghost-bucket"
      Then the response status is 404

  Rule: DeleteBucket must remove existing buckets and reject nonexistent ones

    @REQ-SINGLENODE-BUCKET-008 @functional-requirement @awscli-required
    Scenario: AWS CLI delete-bucket removes an existing bucket
      Given the S3 API is running with the single-node in-memory backend and no storage-engine profile active
      And bucket "awscli-delete-bucket" exists
      When the bucket "awscli-delete-bucket" is deleted via AWS CLI
      Then the response status is 204
      And bucket "awscli-delete-bucket" does not appear in the bucket list

    @REQ-SINGLENODE-BUCKET-008 @functional-requirement @webclient-required
    Scenario: Delete bucket
      Given bucket "test-bucket" exists
      When the bucket is deleted via S3 API
      Then the bucket no longer appears in the bucket list

    @REQ-SINGLENODE-BUCKET-009 @protocol-smoke @awscli-required
    Scenario: AWS CLI delete-bucket fails for a nonexistent bucket
      Given the S3 API is running with the single-node in-memory backend and no storage-engine profile active
      And bucket "awscli-ghost-delete-bucket" does not exist
      When the bucket "awscli-ghost-delete-bucket" is deleted via AWS CLI
      Then the AWS CLI exit code is non-zero
      And the AWS CLI output contains "404"

    @REQ-SINGLENODE-BUCKET-009 @protocol-smoke @webclient-required
    Scenario: Delete nonexistent bucket
      Given bucket "ghost-bucket" does not exist
      When the bucket is deleted via S3 API
      Then the response status is 404
