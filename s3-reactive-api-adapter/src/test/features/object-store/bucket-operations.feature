@webclient
Feature: S3-compatible Bucket Operations

  # ── Success scenarios ──

  @webclient
  Scenario: Create a bucket
    Given a bucket name "test-bucket"
    When the bucket is created via S3 API
    Then the response status is 200
    And the bucket appears in the bucket list

  @webclient
  Scenario: Head bucket (exists)
    Given bucket "test-bucket" exists
    When HEAD request is sent for bucket "test-bucket"
    Then the response status is 200

  @webclient
  Scenario: Get bucket location
    Given bucket "test-bucket" exists
    When bucket location is requested for "test-bucket"
    Then the response status is 200
    And the bucket location response contains "us-east-1"

  @webclient
  Scenario: Get bucket versioning
    Given bucket "test-bucket" exists
    When bucket versioning is requested for "test-bucket"
    Then the response status is 200
    And the bucket versioning response contains "Suspended"

  @webclient
  Scenario: Put bucket versioning
    Given bucket "test-bucket" exists
    When bucket versioning is enabled for "test-bucket"
    Then the response status is 200
    And bucket versioning for "test-bucket" is "Enabled"

  @webclient
  Scenario: Delete bucket
    Given bucket "test-bucket" exists
    When the bucket is deleted via S3 API
    Then the bucket no longer appears in the bucket list

  @webclient
  Scenario: List buckets
    Given bucket "list-bucket-test" exists
    When the buckets are listed
    Then the response status is 200
    And the bucket appears in the bucket list

  # ── Failure scenarios ──

  @webclient
  Scenario: Create duplicate bucket
    Given bucket "dup-bucket" exists
    And a bucket name "dup-bucket"
    When the bucket is created via S3 API
    Then the response status is 409

  @webclient
  Scenario: Head bucket (not found)
    Given bucket "nonexistent" does not exist
    When HEAD request is sent for bucket "nonexistent"
    Then the response status is 404

  @webclient
  Scenario: Get bucket location for nonexistent bucket
    Given bucket "ghost-bucket" does not exist
    When bucket location is requested for "ghost-bucket"
    Then the response status is 404

  @webclient
  Scenario: Get bucket versioning for nonexistent bucket
    Given bucket "ghost-bucket" does not exist
    When bucket versioning is requested for "ghost-bucket"
    Then the response status is 404

  @webclient
  Scenario: Put bucket versioning for nonexistent bucket
    Given bucket "ghost-bucket" does not exist
    When bucket versioning is enabled for "ghost-bucket"
    Then the response status is 404

  @webclient
  Scenario: Delete nonexistent bucket
    Given bucket "ghost-bucket" does not exist
    When the bucket is deleted via S3 API
    Then the response status is 404
