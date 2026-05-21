Feature: S3-compatible ACL, tagging, and object attributes

  Scenario: Bucket ACL can be updated and read
    Given bucket "test-bucket" exists
    When bucket ACL "public-read" is applied to "test-bucket"
    Then the response status is 200
    When bucket ACL is requested for "test-bucket"
    Then the response status is 200
    And the metadata response contains "READ"

  Scenario: Bucket tags can be added, read, and deleted
    Given bucket "test-bucket" exists
    When bucket tag "environment" = "test" is applied to "test-bucket"
    Then the response status is 200
    When bucket tags are requested for "test-bucket"
    Then the response status is 200
    And the metadata response contains "environment"
    When bucket tags are deleted for "test-bucket"
    Then the response status is 204

  Scenario: Object ACL can be updated and read
    Given bucket "test-bucket" exists
    And object "hello.txt" exists
    When object ACL "public-read" is applied to "hello.txt"
    Then the response status is 200
    When object ACL is requested for "hello.txt"
    Then the response status is 200
    And the object metadata response contains "READ"

  Scenario: Object tags can be added, read, and deleted
    Given bucket "test-bucket" exists
    And object "hello.txt" exists
    When object tag "kind" = "demo" is applied to "hello.txt"
    Then the response status is 200
    When object tags are requested for "hello.txt"
    Then the response status is 200
    And the object metadata response contains "kind"
    When object tags are deleted for "hello.txt"
    Then the response status is 204

  Scenario: Object attributes can be read
    Given bucket "test-bucket" exists
    And object "hello.txt" exists with content "Hello Magrathea!"
    When object attributes are requested for "hello.txt"
    Then the response status is 200
    And the object metadata response contains "ObjectSize"
