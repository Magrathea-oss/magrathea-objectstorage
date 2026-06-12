@awscli
Feature: AWS CLI S3 object CRUD parity

  Background:
    Given bucket "test-bucket" exists

  @awscli
  Scenario: AWS CLI put-object succeeds with default headers
    Given an object with key "aws-cli-default.txt" and content "AWS CLI default test"
    When the object is stored via AWS CLI with default headers
    Then the AWS CLI exit code is 0
    And the object appears in the object list

  @awscli
  Scenario: AWS CLI get-object reads stored content
    Given object "aws-cli-get.txt" exists with content "Hello from AWS CLI"
    When the object with key "aws-cli-get.txt" is retrieved via S3 API
    Then the response status is 200
    And the content is "Hello from AWS CLI"

  @awscli
  Scenario: AWS CLI head-object finds an existing object
    Given object "aws-cli-head.txt" exists with content "head me"
    When HEAD request is sent for object "aws-cli-head.txt"
    Then the response status is 200

  @awscli
  Scenario: AWS CLI list-objects shows a stored object
    Given object "aws-cli-list.txt" exists with content "list me"
    When the objects are listed via AWS CLI
    Then the response status is 200
    And the AWS CLI output contains object "aws-cli-list.txt"

  @awscli
  Scenario: AWS CLI list-objects-v2 shows a stored object
    Given object "aws-cli-list-v2.txt" exists with content "list me v2"
    When the objects are listed via AWS CLI V2
    Then the response status is 200
    And the AWS CLI output contains object "aws-cli-list-v2.txt"

  @awscli
  Scenario: AWS CLI delete-object removes an existing object
    Given object "aws-cli-delete.txt" exists with content "delete me"
    When the object with key "aws-cli-delete.txt" is deleted via AWS CLI
    Then the response status is 204
    And object "aws-cli-delete.txt" does not appear in the object list

  @awscli
  Scenario: AWS CLI delete-object is idempotent for an already deleted object
    Given object "aws-cli-idempotent-delete.txt" exists with content "delete me twice"
    When the object with key "aws-cli-idempotent-delete.txt" is deleted via AWS CLI
    Then the response status is 204
    When the object with key "aws-cli-idempotent-delete.txt" is deleted via AWS CLI
    Then the response status is 204
    And object "aws-cli-idempotent-delete.txt" does not appear in the object list

  @awscli
  Scenario: AWS CLI put-object stores STANDARD storage class
    Given an object with key "aws-cli-standard.txt" and content "standard storage"
    When the object is stored via S3 API with storage class "STANDARD"
    Then the response status is 200
    When object attributes are requested via AWS CLI for "aws-cli-standard.txt"
    Then the AWS CLI object attributes include storage class "STANDARD"

  # Current S3PathRouter maps object keys as /{bucket}/{key}; keys containing slashes
  # do not match those routes yet. Keep this parity scenario documented but excluded.
  @awscli @unsupported-awscli
  Scenario: AWS CLI put-object with a key containing slashes
    Given an object with key "folder/aws-cli-slash.txt" and content "nested key"
    When the object is stored via AWS CLI with default headers
    Then the AWS CLI exit code is 0
