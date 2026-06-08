@webclient
Feature: S3-compatible ACL, tagging, and object attributes

  # ── Success scenarios ──

  @webclient
  Scenario: Bucket ACL can be updated and read
    Given bucket "test-bucket" exists
    When bucket ACL "public-read" is applied to "test-bucket"
    Then the response status is 200
    When bucket ACL is requested for "test-bucket"
    Then the response status is 200
    And the metadata response contains "READ"

  @webclient
  Scenario: Object ACL can be updated and read
    Given bucket "test-bucket" exists
    And object "hello.txt" exists
    When object ACL "public-read" is applied to "hello.txt"
    Then the response status is 200
    When object ACL is requested for "hello.txt"
    Then the response status is 200
    And the object metadata response contains "READ"

  @webclient
  Scenario: Object attributes can be read
    Given bucket "test-bucket" exists
    And object "hello.txt" exists with content "Hello Magrathea!"
    When object attributes are requested for "hello.txt"
    Then the response status is 200
    And the object metadata response contains "ObjectSize"

  # ── Failure scenarios ──

  @webclient
  Scenario: Get bucket ACL for nonexistent bucket
    When bucket ACL is requested for "ghost-bucket"
    Then the response status is 404

  @webclient
  Scenario: Put bucket ACL for nonexistent bucket
    When bucket ACL "private" is applied to "ghost-bucket"
    Then the response status is 404

  @webclient
  Scenario: Get bucket tagging for nonexistent bucket
    When bucket tags are requested for "ghost-bucket"
    Then the response status is 404

  @webclient
  Scenario: Put bucket tagging for nonexistent bucket
    When bucket tag "x" = "y" is applied to "ghost-bucket"
    Then the response status is 404

  @webclient
  Scenario: Delete bucket tagging for nonexistent bucket
    When bucket tags are deleted for "ghost-bucket"
    Then the response status is 404

  @webclient
  Scenario: Get object ACL for nonexistent object
    Given bucket "test-bucket" exists
    When object ACL is requested for "ghost.txt"
    Then the response status is 404

  @webclient
  Scenario: Put object ACL for nonexistent object
    Given bucket "test-bucket" exists
    When object ACL "private" is applied to "ghost.txt"
    Then the response status is 404

  @webclient
  Scenario: Get object tagging for nonexistent object
    Given bucket "test-bucket" exists
    When object tags are requested for "ghost.txt"
    Then the response status is 404

  @webclient
  Scenario: Put object tagging for nonexistent object
    Given bucket "test-bucket" exists
    When object tag "x" = "y" is applied to "ghost.txt"
    Then the response status is 404

  @webclient
  Scenario: Delete object tagging for nonexistent object
    Given bucket "test-bucket" exists
    When object tags are deleted for "ghost.txt"
    Then the response status is 404
