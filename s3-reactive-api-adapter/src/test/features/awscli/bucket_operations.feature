@awscli
Feature: AWS CLI S3 bucket operations parity

  @awscli
  Scenario: AWS CLI create-bucket and list-buckets show the bucket
    Given a bucket name "awscli-create-list-bucket"
    When the bucket is created via AWS CLI
    Then the AWS CLI exit code is 0
    When the buckets are listed via AWS CLI
    Then the response status is 200
    And the AWS CLI output contains bucket "awscli-create-list-bucket"

  @awscli
  Scenario: AWS CLI head-bucket finds an existing bucket
    Given bucket "awscli-head-bucket" exists
    When HEAD request is sent for bucket "awscli-head-bucket"
    Then the response status is 200

  @awscli
  Scenario: AWS CLI get-bucket-location returns the bucket region
    Given bucket "awscli-location-bucket" exists
    When bucket location is requested via AWS CLI for "awscli-location-bucket"
    Then the response status is 200
    And the AWS CLI bucket location contains "us-east-1"

  @awscli
  Scenario: AWS CLI get-bucket-versioning returns suspended by default
    Given bucket "awscli-versioning-default-bucket" exists
    When bucket versioning is requested via AWS CLI for "awscli-versioning-default-bucket"
    Then the response status is 200
    And the AWS CLI bucket versioning contains status "Suspended"

  @awscli
  Scenario: AWS CLI put-bucket-versioning enables versioning
    Given bucket "awscli-versioning-enabled-bucket" exists
    When bucket versioning is set via AWS CLI for "awscli-versioning-enabled-bucket" to "Enabled"
    Then the response status is 200
    When bucket versioning is requested via AWS CLI for "awscli-versioning-enabled-bucket"
    Then the response status is 200
    And the AWS CLI bucket versioning contains status "Enabled"

  @awscli
  Scenario: AWS CLI delete-bucket removes an existing bucket
    Given bucket "awscli-delete-bucket" exists
    When the bucket "awscli-delete-bucket" is deleted via AWS CLI
    Then the response status is 204
    And bucket "awscli-delete-bucket" does not appear in the bucket list

  @awscli
  Scenario: AWS CLI head-bucket fails for a nonexistent bucket
    Given bucket "awscli-ghost-head-bucket" does not exist
    When HEAD request is sent for bucket "awscli-ghost-head-bucket"
    Then the AWS CLI exit code is non-zero
    And the AWS CLI output contains "404"

  @awscli
  Scenario: AWS CLI delete-bucket fails for a nonexistent bucket
    Given bucket "awscli-ghost-delete-bucket" does not exist
    When the bucket "awscli-ghost-delete-bucket" is deleted via AWS CLI
    Then the AWS CLI exit code is non-zero
    And the AWS CLI output contains "404"

  @awscli
  Scenario: AWS CLI create-bucket fails for a duplicate bucket
    Given bucket "awscli-duplicate-bucket" exists
    When the bucket is created via AWS CLI
    Then the AWS CLI exit code is non-zero
