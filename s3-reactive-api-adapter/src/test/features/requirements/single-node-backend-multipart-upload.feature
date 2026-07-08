@requirement @single-node-backend @multipart
Business Need: Multipart upload lifecycle against the single-node in-memory backend
  As an S3-compatible client using WebTestClient or the AWS CLI against a single-node
  Magrathea deployment,
  I want CreateMultipartUpload, UploadPart, ListParts, CompleteMultipartUpload,
  AbortMultipartUpload, and ListMultipartUploads to complete a full multipart
  lifecycle against the single-node in-memory backend,
  So that multipart uploads work correctly in single-node/development mode, without
  requiring the storage-engine profile to be active.

  This feature shares the single-node-backend validation scope described in
  single-node-backend-bucket-operations.feature: per ADR-0014, "single-node" is the
  Spring-default profile intended for development and single-node/test deployments,
  not durable production storage; no "storage-engine" profile is active here, so the
  in-memory reactive multipart upload repository is used
  (InMemoryReactiveMultipartUploadRepository). The durability and restart-safety
  of multipart parts under the storage-engine backend is covered separately by
  REQ-S3-002-C in phase-5-s3-semantic-compatibility.feature.

  The AWS CLI runner exercises the full lifecycle (initiate, upload part, list parts,
  complete) as a single end-to-end scenario; the WebTestClient runner validates the
  same lifecycle as finer-grained scenarios per operation. Both cover the same
  requirement (REQ-SINGLENODE-MULTIPART-001).

  Rule: A multipart upload must be initiated, receive uploaded parts, and complete successfully

    @REQ-SINGLENODE-MULTIPART-001 @functional-requirement @awscli-required
    Scenario: Complete multipart upload lifecycle via AWS CLI
      Given the S3 API is running with the single-node in-memory backend and no storage-engine profile active
      And bucket "awscli-multipart-bucket" exists
      And an object key "awscli-multipart-key.txt"
      When a multipart upload is initiated via AWS CLI for "awscli-multipart-key.txt"
      Then the AWS CLI exit code is 0
      And the AWS CLI output contains an upload ID
      When part 1 is uploaded via AWS CLI with content "part one body"
      Then the AWS CLI exit code is 0
      And the AWS CLI output contains an ETag
      When the parts are listed via AWS CLI
      Then the AWS CLI exit code is 0
      And the AWS CLI output contains "PartNumber"
      When the multipart upload is completed via AWS CLI
      Then the AWS CLI exit code is 0

    @REQ-SINGLENODE-MULTIPART-001 @functional-requirement @webclient-required
    Scenario: Initiate multipart upload
      Given bucket "test-multipart-bucket" exists
      And an object key "test-key.txt"
      When a multipart upload is initiated via API
      Then the response status is 200
      And the initiate response contains an upload ID

    @REQ-SINGLENODE-MULTIPART-001 @functional-requirement @webclient-required
    Scenario: Upload a part
      Given bucket "test-multipart-bucket" exists
      And an object key "test-key.txt"
      And a multipart upload is initiated for the bucket
      When a part number 1 is uploaded with content "part1 content"
      Then the response status is 200
      And the upload part response contains an ETag

    @REQ-SINGLENODE-MULTIPART-001 @functional-requirement @webclient-required
    Scenario: List parts
      Given bucket "test-multipart-bucket" exists
      And an object key "test-key.txt"
      And a multipart upload is initiated for the bucket
      And part number 1 is uploaded
      When the parts are listed
      Then the response status is 200
      And the list parts response contains 1 part

    @REQ-SINGLENODE-MULTIPART-001 @functional-requirement @webclient-required
    Scenario: Complete multipart upload
      Given bucket "test-multipart-bucket" exists
      And an object key "test-key.txt"
      And a multipart upload is initiated for the bucket
      And part number 1 is uploaded
      When the multipart upload is completed
      Then the response status is 200
      And the complete response contains an ETag

    @REQ-SINGLENODE-MULTIPART-004 @protocol-smoke @webclient-required
    Scenario: Initiate multipart upload to nonexistent bucket
      Given a bucket name "nonexistent-bucket"
      And an object key "test-key.txt"
      When a multipart upload is initiated for nonexistent bucket
      Then the response status is 404

    @REQ-SINGLENODE-MULTIPART-005 @protocol-smoke @webclient-required
    Scenario: Upload part to nonexistent upload
      Given bucket "test-multipart-bucket" exists
      And an object key "test-key.txt"
      When an invalid part is uploaded with uploadId "INVALID"
      Then the response status is 404

    @REQ-SINGLENODE-MULTIPART-006 @protocol-smoke @webclient-required
    Scenario: Complete nonexistent multipart upload
      Given bucket "test-multipart-bucket" exists
      And an object key "test-key.txt"
      When a multipart upload is completed with uploadId "INVALID"
      Then the response status is 404

  Rule: An in-progress multipart upload must be abortable and listable before completion

    @REQ-SINGLENODE-MULTIPART-002 @functional-requirement @awscli-required
    Scenario: Abort multipart upload via AWS CLI
      Given the S3 API is running with the single-node in-memory backend and no storage-engine profile active
      And bucket "awscli-abort-bucket" exists
      And an object key "awscli-abort-key.txt"
      When a multipart upload is initiated via AWS CLI for "awscli-abort-key.txt"
      Then the AWS CLI exit code is 0
      When the multipart upload is aborted via AWS CLI
      Then the AWS CLI exit code is 0

    @REQ-SINGLENODE-MULTIPART-002 @functional-requirement @webclient-required
    Scenario: Abort multipart upload
      Given bucket "test-multipart-bucket" exists
      And an object key "test-key.txt"
      And a multipart upload is initiated for the bucket
      When the multipart upload is aborted
      Then the response status is 204

    @REQ-SINGLENODE-MULTIPART-003 @functional-requirement @awscli-required
    Scenario: List multipart uploads via AWS CLI
      Given the S3 API is running with the single-node in-memory backend and no storage-engine profile active
      And bucket "awscli-list-uploads-bucket" exists
      And an object key "awscli-list-mp-key.txt"
      When a multipart upload is initiated via AWS CLI for "awscli-list-mp-key.txt"
      Then the AWS CLI exit code is 0
      When the multipart uploads are listed via AWS CLI
      Then the AWS CLI exit code is 0
      And the AWS CLI output contains "awscli-list-mp-key.txt"

    @REQ-SINGLENODE-MULTIPART-003 @functional-requirement @webclient-required
    Scenario: List multipart uploads
      Given bucket "test-multipart-bucket" exists
      And an object key "list-uploads-key.txt"
      And a multipart upload is initiated for the bucket
      When the multipart uploads are listed
      Then the response status is 200
      And the list uploads response contains 1 upload
