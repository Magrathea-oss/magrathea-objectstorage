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

  Scenario: Delete an object
    Given object "hello.txt" exists
    When the object is deleted via S3 API
    Then the object no longer appears in the object list
