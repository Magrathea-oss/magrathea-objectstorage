@requirement @phase-5 @s3-compatibility
Business Need: Phase 5 S3 semantic compatibility — ETag integrity, byte-range retrieval, conditional requests, object tagging, and unsupported-feature classification
  As an S3-compatible client application, AWS CLI user, or developer integrating with Magrathea Object Storage,
  I want PutObject to compute and persist a correct RFC-compliant MD5 ETag, GetObject to honor Range and conditional
  request headers, CopyObject to return the destination ETag, object tagging to persist through the full tag
  lifecycle, and declared unsupported features to return documented not-implemented or config-only responses,
  So that existing S3 client libraries and AWS CLI tooling interoperate correctly without modification against
  the Magrathea S3-compatible API.

  This feature is the single source of truth for all Phase 5 S3 semantic compatibility requirements.
  It covers externally observable S3 API behaviors that go beyond basic CRUD: ETag computation and
  consistency, byte-range reads, conditional GET/HEAD semantics, multipart ETag format, object tagging
  persistence, and explicit classification of unimplemented S3 features.

  Scenario status tags reflect observed WebTestClient runner evidence. Keep every @REQ-S3-*
  requirement ID unchanged when implementation or validation status changes.

  Validation roles:
    - WebTestClient runner is the primary validation mode for all requirements. Step definitions in the
      requirements runner package drive HTTP requests against the S3 RouterFunction endpoints bound via
      WebTestClient.bindToRouterFunction and assert response status, headers, and body content.
    - AWS CLI runner validates S3 compatibility using the `aws s3api` CLI against a running server.
      AWS CLI validation is required for multipart, byte-range, and conditional-request requirements.
    - Filesystem inspection confirms durability of multipart parts after application restart for
      REQ-S3-002-C.
    - Application restart probe stops the Spring context, discards process memory, starts the context
      with the same in-memory or filesystem storage configuration, and verifies durable committed state
      is still accessible.

  Safe identifier conventions for Phase 5 requirement scenarios:
    - ETag values are quoted lowercase 32-character MD5 hex strings for single-part objects,
      for example: "a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6"
    - Multipart ETags use the format "{md5-of-part-etag-bytes}-{part-count}", for example:
      "b14a7b8ce56f49a5f9aeb8c10a393b26-2"
    - Content-Range values follow RFC 7233: "bytes {first}-{last}/{total}", for example:
      "bytes 0-4/11"

  Requirement summary by capability area:
    | requirement  | capability area   | validation modes                       | tags                                        |
    | REQ-S3-001   | etag              | webclient-required                     | @functional-requirement @s3-api @etag       |
    | REQ-S3-002   | multipart-etag    | webclient-required awscli-required     | @functional-requirement @s3-api @multipart  |
    | REQ-S3-003   | range-get         | webclient-required awscli-required     | @functional-requirement @s3-api @range      |
    | REQ-S3-004   | conditional       | webclient-required awscli-required     | @functional-requirement @s3-api @conditional|
    | REQ-S3-005   | copy-etag         | webclient-required                     | @functional-requirement @s3-api @etag       |
    | REQ-S3-006   | tagging           | webclient-required                     | @functional-requirement @s3-api @tagging    |
    | REQ-S3-007   | unsupported       | webclient-required                     | @not-implemented @config-only @s3-api       |

  Reusable fixture assignments per requirement:
    | requirement  | bucket                    | object key                | body content (ASCII)          | body length |
    | REQ-S3-001   | etag-bucket               | docs/report.pdf           | hello-etag-test               | 15 bytes    |
    | REQ-S3-002-A | multipart-etag-bucket     | uploads/video.mp4         | part-one-data                 | 13 bytes    |
    | REQ-S3-002-B | multipart-etag-bucket     | uploads/dataset.bin       | (two-part upload)             | 2 parts     |
    | REQ-S3-002-C | multipart-restart-bucket  | uploads/restart-check.mp4 | durable-part-content          | 20 bytes    |
    | REQ-S3-003   | range-bucket              | data/sample.txt           | hello world                   | 11 bytes    |
    | REQ-S3-004   | conditional-bucket        | data/config.json          | configuration-file-content    | 26 bytes    |
    | REQ-S3-005   | copy-bucket               | originals/photo.jpg       | photo-binary-content-fixture  | 29 bytes    |
    | REQ-S3-006   | tagging-bucket            | docs/report.pdf           | document-body-content         | 21 bytes    |
    | REQ-S3-007   | versioning-bucket         | (bucket-level operation)  | (none)                        | n/a         |


  Rule: PutObject must compute and return an RFC-compliant ETag as the MD5 hex digest of the object body, enclosed in double quotes

    @implemented-and-validated @REQ-S3-001-A @functional-requirement @s3-api @etag @webclient-required
    Scenario: PutObject of a known payload returns a structurally valid quoted MD5 hex ETag
      Given bucket "etag-bucket" exists
      And an object with key "docs/report.pdf" and content "hello-etag-test"
      When the object is stored via S3 API in bucket "etag-bucket"
      Then the response status is 200
      And the response ETag header is present and enclosed in double quotes
      And the response ETag header is 34 characters long representing 32 lowercase hex digits enclosed in double quotes

    @implemented-and-validated @REQ-S3-001-B @functional-requirement @s3-api @etag @webclient-required
    Scenario: HeadObject for a previously stored object returns the same ETag as the PutObject response
      Given bucket "etag-bucket" exists
      And an object with key "docs/report.pdf" and content "hello-etag-test"
      When the object is stored via S3 API in bucket "etag-bucket"
      Then the response status is 200
      And the response ETag header is saved as "put-object-etag"
      When HEAD request is sent for object "docs/report.pdf" in bucket "etag-bucket"
      Then the response status is 200
      And the response header "ETag" matches the saved ETag value "put-object-etag"


  Rule: Multipart upload parts must be persisted durably and CompleteMultipartUpload must return a multipart ETag in the format "{md5}-{part-count}"

    @implemented-and-validated @REQ-S3-002-A @functional-requirement @s3-api @multipart @etag @webclient-required @awscli-required
    Scenario: UploadPart returns a quoted 32-character MD5 hex ETag for the part body, not a placeholder
      Given bucket "multipart-etag-bucket" exists
      And an object key "uploads/video.mp4"
      And a multipart upload is initiated for the bucket
      When a part number 1 is uploaded with content "part-one-data"
      Then the response status is 200
      And the upload part response ETag is a quoted 32-character lowercase hex string

    @implemented-and-validated @REQ-S3-002-B @functional-requirement @s3-api @multipart @etag @webclient-required @awscli-required
    Scenario: CompleteMultipartUpload for a two-part upload returns a multipart ETag with the "-2" suffix
      Given bucket "multipart-etag-bucket" exists
      And an object key "uploads/dataset.bin"
      And a multipart upload is initiated for the bucket
      And part number 1 is uploaded with content "first-part-data-segment"
      And part number 2 is uploaded with content "second-part-data-segment"
      When the multipart upload is completed with all uploaded parts in order
      Then the response status is 200
      And the complete multipart upload ETag ends with "-2" indicating the two-part composition

    @implemented-not-e2e-validated @REQ-S3-002-C @functional-requirement @non-functional-requirement @s3-api @multipart @durability @restart-safety @webclient-required @filesystem-inspection-required
    Scenario: Uploaded parts remain accessible via ListParts after the application is restarted
      Given bucket "multipart-restart-bucket" exists
      And an object key "uploads/restart-check.mp4"
      And a multipart upload is initiated for the bucket
      And a part number 1 is uploaded with content "durable-part-content"
      When the application is restarted with the same storage configuration
      And the parts are listed
      Then the response status is 200
      And the list parts response contains 1 part
      And the persisted part has part number 1 and a valid quoted MD5 ETag


  Rule: GetObject with a Range header must return 206 Partial Content with the correct bytes and a Content-Range response header

    @implemented-and-validated @REQ-S3-003-A @functional-requirement @non-functional-requirement @s3-api @range @webclient-required @awscli-required
    Scenario: GetObject with Range bytes=0-4 on an 11-byte object returns 206 with the first 5 bytes and a Content-Range header
      Given bucket "range-bucket" exists
      And object "data/sample.txt" is stored in bucket "range-bucket" with body "hello world"
      When the S3 GetObject API fetches object "data/sample.txt" from bucket "range-bucket" with Range header "bytes=0-4"
      Then the response status is 206
      And the response body is "hello"
      And the Content-Range response header is "bytes 0-4/11"

    @implemented-and-validated @REQ-S3-003-B @functional-requirement @non-functional-requirement @s3-api @range @webclient-required @awscli-required
    Scenario: GetObject with Range bytes=6-10 on the same 11-byte object returns 206 with the last 5 bytes
      Given bucket "range-bucket" exists
      And object "data/sample.txt" is stored in bucket "range-bucket" with body "hello world"
      When the S3 GetObject API fetches object "data/sample.txt" from bucket "range-bucket" with Range header "bytes=6-10"
      Then the response status is 206
      And the response body is "world"
      And the Content-Range response header is "bytes 6-10/11"

    @implemented-and-validated @REQ-S3-003-C @functional-requirement @non-functional-requirement @s3-api @range @webclient-required @awscli-required
    Scenario: GetObject with an unsatisfiable range on an 11-byte object returns 416 Range Not Satisfiable
      Given bucket "range-bucket" exists
      And object "data/sample.txt" is stored in bucket "range-bucket" with body "hello world"
      When the S3 GetObject API fetches object "data/sample.txt" from bucket "range-bucket" with Range header "bytes=100-200"
      Then the response status is 416

    @implemented-and-validated @REQ-S3-003-D @functional-requirement @non-functional-requirement @s3-api @range @webclient-required @awscli-required
    Scenario: GetObject without a Range header returns 200 OK with the full object body
      Given bucket "range-bucket" exists
      And object "data/sample.txt" is stored in bucket "range-bucket" with body "hello world"
      When the S3 GetObject API fetches object "data/sample.txt" from bucket "range-bucket" without a Range header
      Then the response status is 200
      And the response body is "hello world"


  Rule: GetObject and HeadObject must honor If-Match, If-None-Match, If-Modified-Since, and If-Unmodified-Since headers

    @implemented-and-validated @REQ-S3-004-A @functional-requirement @non-functional-requirement @s3-api @conditional @webclient-required @awscli-required
    Scenario: GetObject with If-Match matching the stored object ETag returns 200 OK with the object body
      Given bucket "conditional-bucket" exists
      And object "data/config.json" is stored in bucket "conditional-bucket" with body "configuration-file-content"
      And the PutObject ETag for "data/config.json" is saved as "current-etag"
      When the S3 GetObject API fetches object "data/config.json" from bucket "conditional-bucket" with header "If-Match" set to the saved ETag "current-etag"
      Then the response status is 200
      And the response body is "configuration-file-content"

    @implemented-and-validated @REQ-S3-004-B @functional-requirement @non-functional-requirement @s3-api @conditional @webclient-required @awscli-required
    Scenario: GetObject with If-Match not matching the stored object ETag returns 412 Precondition Failed
      Given bucket "conditional-bucket" exists
      And object "data/config.json" is stored in bucket "conditional-bucket" with body "configuration-file-content"
      When the S3 GetObject API fetches object "data/config.json" from bucket "conditional-bucket" with header "If-Match" set to value "\"00000000000000000000000000000000\""
      Then the response status is 412

    @implemented-and-validated @REQ-S3-004-C @functional-requirement @non-functional-requirement @s3-api @conditional @webclient-required @awscli-required
    Scenario: GetObject with If-None-Match matching the stored object ETag returns 304 Not Modified with no body
      Given bucket "conditional-bucket" exists
      And object "data/config.json" is stored in bucket "conditional-bucket" with body "configuration-file-content"
      And the PutObject ETag for "data/config.json" is saved as "current-etag"
      When the S3 GetObject API fetches object "data/config.json" from bucket "conditional-bucket" with header "If-None-Match" set to the saved ETag "current-etag"
      Then the response status is 304

    @implemented-and-validated @REQ-S3-004-D @functional-requirement @non-functional-requirement @s3-api @conditional @webclient-required @awscli-required
    Scenario: GetObject with If-None-Match not matching the stored object ETag returns 200 OK with the object body
      Given bucket "conditional-bucket" exists
      And object "data/config.json" is stored in bucket "conditional-bucket" with body "configuration-file-content"
      When the S3 GetObject API fetches object "data/config.json" from bucket "conditional-bucket" with header "If-None-Match" set to value "\"00000000000000000000000000000000\""
      Then the response status is 200
      And the response body is "configuration-file-content"

    @implemented-and-validated @REQ-S3-004-E @functional-requirement @non-functional-requirement @s3-api @conditional @webclient-required @awscli-required
    Scenario: GetObject with If-Modified-Since set to a time after the last modification returns 304 Not Modified
      Given bucket "conditional-bucket" exists
      And object "data/config.json" is stored in bucket "conditional-bucket" with body "configuration-file-content"
      When the S3 GetObject API fetches object "data/config.json" from bucket "conditional-bucket" with header "If-Modified-Since" set to a timestamp after the object's last modification time
      Then the response status is 304

    @implemented-and-validated @REQ-S3-004-F @functional-requirement @non-functional-requirement @s3-api @conditional @webclient-required @awscli-required
    Scenario: GetObject with If-Unmodified-Since set to a time before the last modification returns 412 Precondition Failed
      Given bucket "conditional-bucket" exists
      And object "data/config.json" is stored in bucket "conditional-bucket" with body "configuration-file-content"
      When the S3 GetObject API fetches object "data/config.json" from bucket "conditional-bucket" with header "If-Unmodified-Since" set to a timestamp before the object's last modification time
      Then the response status is 412


  Rule: CopyObject must return the ETag of the newly written destination object, not a placeholder

    @implemented-and-validated @REQ-S3-005-A @functional-requirement @s3-api @etag @copy @webclient-required
    Scenario: CopyObject response ETag matches the source object ETag
      Given bucket "copy-bucket" exists
      And object "originals/photo.jpg" is stored in bucket "copy-bucket" with body "photo-binary-content-fixture"
      And the PutObject ETag for "originals/photo.jpg" is saved as "source-etag"
      When object "originals/photo.jpg" is copied to "copies/photo.jpg" in bucket "copy-bucket"
      Then the response status is 200
      And the CopyObject response ETag matches the saved ETag "source-etag"

    @implemented-and-validated @REQ-S3-005-B @functional-requirement @s3-api @etag @copy @webclient-required
    Scenario: HeadObject on the copy destination returns the same ETag as the CopyObject response
      Given bucket "copy-bucket" exists
      And object "originals/photo.jpg" is stored in bucket "copy-bucket" with body "photo-binary-content-fixture"
      And the PutObject ETag for "originals/photo.jpg" is saved as "source-etag"
      When object "originals/photo.jpg" is copied to "copies/photo.jpg" in bucket "copy-bucket"
      Then the response status is 200
      And the CopyObject response ETag is saved as "copy-etag"
      When HEAD request is sent for object "copies/photo.jpg" in bucket "copy-bucket"
      Then the response status is 200
      And the response header "ETag" matches the saved ETag value "copy-etag"


  Rule: PutObjectTagging must persist tags that are then returned by GetObjectTagging; DeleteObjectTagging must remove all tags

    @implemented-and-validated @REQ-S3-006-A @functional-requirement @s3-api @tagging @webclient-required
    Scenario: PutObjectTagging on an existing object persists a tag set of two tags
      Given bucket "tagging-bucket" exists
      And object "docs/report.pdf" is stored in bucket "tagging-bucket" with body "document-body-content"
      When the following tag set is applied to object "docs/report.pdf" in bucket "tagging-bucket" via PutObjectTagging
        | Key   | Value      |
        | env   | production |
        | owner | team-a     |
      Then the response status is 200

    @implemented-and-validated @REQ-S3-006-B @functional-requirement @s3-api @tagging @webclient-required
    Scenario: GetObjectTagging on a tagged object returns all persisted tags
      Given bucket "tagging-bucket" exists
      And object "docs/report.pdf" is stored in bucket "tagging-bucket" with body "document-body-content"
      When the following tag set is applied to object "docs/report.pdf" in bucket "tagging-bucket" via PutObjectTagging
        | Key   | Value      |
        | env   | production |
        | owner | team-a     |
      And object tags are requested for "docs/report.pdf" in bucket "tagging-bucket"
      Then the response status is 200
      And the response body contains tag key "env" with value "production"
      And the response body contains tag key "owner" with value "team-a"

    @implemented-and-validated @REQ-S3-006-C @functional-requirement @s3-api @tagging @webclient-required
    Scenario: DeleteObjectTagging removes all tags and subsequent GetObjectTagging returns an empty tag set
      Given bucket "tagging-bucket" exists
      And object "docs/report.pdf" is stored in bucket "tagging-bucket" with body "document-body-content"
      And the following tag set is applied to object "docs/report.pdf" in bucket "tagging-bucket" via PutObjectTagging
        | Key   | Value      |
        | env   | production |
        | owner | team-a     |
      When object tags are deleted for "docs/report.pdf" in bucket "tagging-bucket"
      Then the response status is 204
      When object tags are requested for "docs/report.pdf" in bucket "tagging-bucket"
      Then the response status is 200
      And the GetObjectTagging response contains an empty tag set

    @implemented-and-validated @REQ-S3-006-D @functional-requirement @s3-api @tagging @webclient-required
    Scenario: PutObject with x-amz-tagging header persists inline tags that are then returned by GetObjectTagging
      Given bucket "tagging-bucket" exists
      When the S3 PutObject API stores object "docs/inline-tagged.pdf" in bucket "tagging-bucket" with body "tagged-document-body" and x-amz-tagging header "env=staging&project=magrathea"
      Then the response status is 200
      When object tags are requested for "docs/inline-tagged.pdf" in bucket "tagging-bucket"
      Then the response status is 200
      And the response body contains tag key "env" with value "staging"
      And the response body contains tag key "project" with value "magrathea"


  Rule: Versioning, delete markers, object lock, lifecycle enforcement, encryption enforcement, restore, and replication must return documented not-implemented or config-only responses rather than silently succeeding or returning 500

    @implemented-and-validated @REQ-S3-007-A @functional-requirement @s3-api @versioning @not-implemented @webclient-required
    Scenario: GetBucketVersioning returns a versioning status indicating versioning is not enabled, not a 500 error
      Given bucket "versioning-bucket" exists
      When bucket versioning is requested for "versioning-bucket"
      Then the response status is 200
      And the bucket versioning response does not contain an "Enabled" versioning status

    @implemented-and-validated @REQ-S3-007-B @functional-requirement @s3-api @versioning @config-only @not-implemented @webclient-required
    Scenario: PutBucketVersioning accepts a versioning configuration and returns 200 without enforcing object versioning
      Given bucket "versioning-bucket" exists
      When bucket versioning is enabled for "versioning-bucket"
      Then the response status is 200
      # Note: actual object versioning enforcement (creation of version IDs, delete markers,
      # ListObjectVersions returning multiple versions per key) is not implemented.
      # This scenario validates config-only acceptance without runtime enforcement.

    @implemented-and-validated @REQ-S3-007-C @functional-requirement @s3-api @object-lock @not-implemented @webclient-required
    Scenario: GetObjectLockConfiguration on a bucket without object lock configured returns an appropriate S3 response and not a 500 error
      Given bucket "object-lock-bucket" exists
      When the S3 API retrieves object lock configuration for bucket "object-lock-bucket"
      Then the response status is 200
      And the object lock configuration response indicates object lock is not enabled

    @implemented-and-validated @REQ-S3-007-D @functional-requirement @s3-api @lifecycle @not-implemented @webclient-required
    Scenario: GetBucketLifecycleConfiguration on a bucket without lifecycle rules returns 404 NoSuchLifecycleConfiguration per S3 specification
      Given bucket "lifecycle-bucket" exists
      When the S3 API retrieves lifecycle configuration for bucket "lifecycle-bucket"
      Then the response status is 404
      And the response body contains "NoSuchLifecycleConfiguration"
