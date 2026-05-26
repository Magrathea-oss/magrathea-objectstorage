Feature: Bucket lifecycle
  The application service enforces aggregate root integrity,
  domain event notification, and CQRS separation.

  Scenario: Create a new bucket
    Given bucket "my-bucket" does not exist
    When I create bucket "my-bucket"
    Then the result is a Created<Bucket> with version 1
    And the event BucketCreated is recorded

  Scenario: Create duplicate bucket
    Given bucket "my-bucket" exists
    When I create bucket "my-bucket"
    Then the result is Mono.error with BucketAlreadyExistsException

  Scenario: Find bucket by name
    Given bucket "find-me" exists
    When I find bucket by name "find-me"
    Then the result is a Bucket named "find-me"

  Scenario: Find bucket by name not found
    Given bucket "find-me" does not exist
    When I find bucket by name "find-me"
    Then the result is Mono.empty (not found)

  Scenario: Delete a bucket
    Given bucket "delete-me" exists
    When I delete bucket "delete-me"
    Then the result is a Deleted<Bucket> with version 2
    And the event BucketDeleted is recorded

  Scenario: Delete nonexistent bucket
    Given bucket "delete-me" does not exist
    When I delete bucket "delete-me"
    Then the result is Mono.error with BucketNotFoundException

  Scenario: Update bucket configuration
    Given bucket "config-me" exists
    When I set CORS configuration on bucket "config-me" with origins ["*"]
    Then the result is an Updated<Bucket> with version 2
    And the event BucketConfigurationChanged is recorded
    And the bucket has CORS configuration with origins ["*"]
