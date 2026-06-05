Feature: AWS CLI PutObject — Header Compatibility

  Background:
    Given bucket "test-bucket" exists

  Scenario: AWS CLI put-object succeeds with default headers
    Given an object with key "aws-cli-default.txt" and content "AWS CLI default test"
    When the object is stored via AWS CLI with default headers
    Then the AWS CLI exit code is 0
    And the object appears in the object list


