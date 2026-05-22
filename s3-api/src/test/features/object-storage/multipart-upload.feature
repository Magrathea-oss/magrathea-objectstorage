Feature: S3-compatible Multipart Upload Operations

  # ── Success scenarios ──

  Scenario: Initiate multipart upload
    Given bucket "test-multipart-bucket" exists
    And an object key "test-key.txt"
    When a multipart upload is initiated via API
    Then the response status is 200
    And the initiate response contains an upload ID

  Scenario: Upload a part
    Given bucket "test-multipart-bucket" exists
    And an object key "test-key.txt"
    And a multipart upload is initiated for the bucket
    When a part number 1 is uploaded with content "part1 content"
    Then the response status is 200
    And the upload part response contains an ETag

  Scenario: List parts
    Given bucket "test-multipart-bucket" exists
    And an object key "test-key.txt"
    And a multipart upload is initiated for the bucket
    And part number 1 is uploaded
    When the parts are listed
    Then the response status is 200
    And the list parts response contains 1 part

  Scenario: Complete multipart upload
    Given bucket "test-multipart-bucket" exists
    And an object key "test-key.txt"
    And a multipart upload is initiated for the bucket
    And part number 1 is uploaded
    When the multipart upload is completed
    Then the response status is 200
    And the complete response contains an ETag

  Scenario: List multipart uploads
    Given bucket "test-multipart-bucket" exists
    And an object key "test-key.txt"
    And a multipart upload is initiated for the bucket
    When the multipart uploads are listed
    Then the response status is 200
    And the list uploads response contains 1 upload

  Scenario: Abort multipart upload
    Given bucket "test-multipart-bucket" exists
    And an object key "test-key.txt"
    And a multipart upload is initiated for the bucket
    When the multipart upload is aborted
    Then the response status is 204

  # ── Failure scenarios ──

  Scenario: Initiate multipart upload to nonexistent bucket
    Given a bucket name "nonexistent-bucket"
    And an object key "test-key.txt"
    When a multipart upload is initiated for nonexistent bucket
    Then the response status is 404

  Scenario: Upload part to nonexistent upload
    Given bucket "test-multipart-bucket" exists
    And an object key "test-key.txt"
    When an invalid part is uploaded with uploadId "INVALID"
    Then the response status is 404

  Scenario: Complete nonexistent multipart upload
    Given bucket "test-multipart-bucket" exists
    And an object key "test-key.txt"
    When a multipart upload is completed with uploadId "INVALID"
    Then the response status is 404
