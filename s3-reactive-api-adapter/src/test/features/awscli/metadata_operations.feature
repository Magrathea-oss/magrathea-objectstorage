@awscli
Feature: AWS CLI S3 metadata, ACL, and tagging parity

  @awscli
  Scenario: Bucket ACL can be set and retrieved via AWS CLI
    Given bucket "awscli-acl-bucket" exists
    When bucket ACL "public-read" is applied via AWS CLI to "awscli-acl-bucket"
    Then the AWS CLI exit code is 0
    When bucket ACL is requested via AWS CLI for "awscli-acl-bucket"
    Then the AWS CLI exit code is 0

  @awscli
  Scenario: Object ACL can be set and retrieved via AWS CLI
    Given bucket "awscli-obj-acl-bucket" exists
    And object "awscli-acl-object.txt" exists with content "acl test body"
    When object ACL "public-read" is applied via AWS CLI to "awscli-acl-object.txt"
    Then the AWS CLI exit code is 0
    When object ACL is requested via AWS CLI for "awscli-acl-object.txt"
    Then the AWS CLI exit code is 0

  @awscli
  Scenario: Bucket tagging operations via AWS CLI
    Given bucket "awscli-tag-bucket" exists
    When bucket tag "env" = "test" is applied via AWS CLI to "awscli-tag-bucket"
    Then the AWS CLI exit code is 0
    When bucket tags are requested via AWS CLI for "awscli-tag-bucket"
    Then the AWS CLI exit code is 0
    When bucket tags are deleted via AWS CLI for "awscli-tag-bucket"
    Then the AWS CLI exit code is 0

  @awscli
  Scenario: Object tagging operations via AWS CLI
    Given bucket "awscli-obj-tag-bucket" exists
    And object "awscli-tag-object.txt" exists with content "tag test body"
    When object tag "env" = "test" is applied via AWS CLI to "awscli-tag-object.txt"
    Then the AWS CLI exit code is 0
    When object tags are requested via AWS CLI for "awscli-tag-object.txt"
    Then the AWS CLI exit code is 0
    When object tags are deleted via AWS CLI for "awscli-tag-object.txt"
    Then the AWS CLI exit code is 0

  @awscli
  Scenario: Object attributes include ETag and ObjectSize via AWS CLI
    Given bucket "awscli-attrs-bucket" exists
    And object "awscli-attrs-object.txt" exists with content "attributes test body"
    When object attributes are requested via AWS CLI for "awscli-attrs-object.txt" including ETag and ObjectSize
    Then the AWS CLI exit code is 0
    And the AWS CLI output contains "ETag"
    And the AWS CLI output contains "ObjectSize"
