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

  Scenario: Get bucket location
    Given bucket "test-bucket" exists
    When bucket location is requested for "test-bucket"
    Then the response status is 200
    And the bucket location response contains "us-east-1"

  Scenario: Get bucket versioning
    Given bucket "test-bucket" exists
    When bucket versioning is requested for "test-bucket"
    Then the response status is 200
    And the bucket versioning response contains "Suspended"

  Scenario: Put bucket versioning
    Given bucket "test-bucket" exists
    When bucket versioning is enabled for "test-bucket"
    Then the response status is 200
    And bucket versioning for "test-bucket" is "Enabled"

  Scenario: Delete bucket
    Given bucket "test-bucket" exists
    When the bucket is deleted via S3 API
    Then the bucket no longer appears in the bucket list
