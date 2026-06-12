@awscli
Feature: AWS CLI S3 multipart upload parity

  @awscli
  Scenario: Complete multipart upload lifecycle via AWS CLI
    Given bucket "awscli-multipart-bucket" exists
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

  @awscli
  Scenario: Abort multipart upload via AWS CLI
    Given bucket "awscli-abort-bucket" exists
    And an object key "awscli-abort-key.txt"
    When a multipart upload is initiated via AWS CLI for "awscli-abort-key.txt"
    Then the AWS CLI exit code is 0
    When the multipart upload is aborted via AWS CLI
    Then the AWS CLI exit code is 0

  @awscli
  Scenario: List multipart uploads via AWS CLI
    Given bucket "awscli-list-uploads-bucket" exists
    And an object key "awscli-list-mp-key.txt"
    When a multipart upload is initiated via AWS CLI for "awscli-list-mp-key.txt"
    Then the AWS CLI exit code is 0
    When the multipart uploads are listed via AWS CLI
    Then the AWS CLI exit code is 0
    And the AWS CLI output contains "awscli-list-mp-key.txt"
