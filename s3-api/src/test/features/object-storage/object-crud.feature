Feature: S3-compatible Object CRUD

  Background:
    Given bucket "test-bucket" exists

  Scenario: Put an object
    Given an object with key "hello.txt" and content "Hello Magrathea!"
    When the object is stored via S3 API
    Then the response status is 200
    And the object appears in the object list

  Scenario: Get an object
    Given object "hello.txt" exists with content "Hello Magrathea!"
    When the object is retrieved via S3 API
    Then the response status is 200
    And the content is "Hello Magrathea!"

  Scenario: Head object
    Given object "hello.txt" exists
    When HEAD request is sent for object "hello.txt"
    Then the response status is 200

  Scenario: Head object (not found)
    Given object "nonexistent.txt" does not exist
    When HEAD request is sent for object "nonexistent.txt"
    Then the response status is 404

  Scenario: List objects V2
    Given object "hello.txt" exists
    When the objects are listed via S3 API V2
    Then the response status is 200
    And the object appears in the object list V2

  Scenario: Copy an object
    Given object "hello.txt" exists with content "Hello Magrathea!"
    When object "hello.txt" is copied to "copy.txt"
    Then the response status is 200
    And object "copy.txt" content is "Hello Magrathea!"

  Scenario: List object versions
    Given object "hello.txt" exists
    When object versions are listed via S3 API
    Then the response status is 200
    And the versions response contains object "hello.txt"

  Scenario: Delete multiple objects
    Given object "hello.txt" exists
    And object "copy.txt" exists
    When objects are deleted via S3 API multi-delete
      | hello.txt |
      | copy.txt  |
    Then the response status is 200
    And object "hello.txt" does not appear in the object list
    And object "copy.txt" does not appear in the object list

  Scenario: Delete an object
    Given object "hello.txt" exists
    When the object is deleted via S3 API
    Then the object no longer appears in the object list
