Feature: AWS CLI PutObject — Header Compatibility

  Background:
    Given bucket "test-bucket" exists

  Scenario: AWS CLI put-object succeeds with default headers
    Given an object with key "aws-cli-default.txt" and content "AWS CLI default test"
    When the object is stored via AWS CLI with default headers
    Then the AWS CLI exit code is 0
    And the object appears in the object list

  Scenario: AWS CLI put-object to nonexistent bucket returns 404
    Given an object with key "orphan.txt" and content "data"
    When the object is stored via AWS CLI in bucket "no-such-bucket"
    Then the AWS CLI exit code is non-zero

  # ── Checksum header tests ──

  Scenario: AWS CLI put-object with --checksum-algorithm sha256 echoes header in HEAD
    Given an object with key "checksum-alg-test.txt" and content "data"
    When the object is stored via AWS CLI with checksum algorithm "sha256"
    Then the AWS CLI exit code is 0
    And HEAD response contains header "x-amz-checksum-sha256"

  Scenario: AWS CLI put-object with --content-md5 echoes ETag as quoted md5
    Given an object with key "content-md5-cli.txt" and content "data"
    When the object is stored via AWS CLI with content-md5 "dGVzdA=="
    Then the AWS CLI exit code is 0
    And HEAD response contains header "Content-MD5" with value "dGVzdA=="
