Feature: S3-compatible Bucket Operations

  Scenario: Create a bucket
    Given a bucket name "test-bucket"
    When the bucket is created via S3 API
    Then the response status is 200
    And the bucket appears in the bucket list

  Scenario: Head bucket (exists)
    Given bucket "test-bucket" exists
    When HEAD request is sent for bucket "test-bucket"
    Then the response status is 200

  Scenario: Head bucket (not found)
    Given bucket "nonexistent" does not exist
    When HEAD request is sent for bucket "nonexistent"
    Then the response status is 404

  Scenario: Delete bucket
    Given bucket "test-bucket" exists
    When the bucket is deleted via S3 API
    Then the bucket no longer appears in the bucket list
