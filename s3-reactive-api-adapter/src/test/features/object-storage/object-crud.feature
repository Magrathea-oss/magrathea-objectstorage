Feature: S3-compatible Object CRUD

  Background:
    Given bucket "test-bucket" exists

  # ── Success scenarios ──

  Scenario: Put an object
    Given an object with key "hello.txt" and content "Hello Magrathea!"
    When the object is stored via S3 API
    Then the response status is 200
    And the object appears in the object list

  Scenario: Put an object with PARANOIC_MODE storage class
    Given an object with key "paranoid.txt" and content "Top secret"
    When the object is stored via S3 API with storage class "PARANOIC_MODE"
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

  # ── Failure scenarios ──

  Scenario: Put object to nonexistent bucket
    Given an object with key "orphan.txt" and content "data"
    When the object is stored via S3 API in bucket "no-such-bucket"
    Then the response status is 404

  Scenario: Get nonexistent object
    When the object with key "ghost.txt" is retrieved via S3 API
    Then the response status is 404

  Scenario: Head object (not found)
    Given object "nonexistent.txt" does not exist
    When HEAD request is sent for object "nonexistent.txt"
    Then the response status is 404

  Scenario: Copy object with nonexistent source
    When object "ghost.txt" is copied to "still-ghost.txt"
    Then the response status is 404

  Scenario: Copy object to nonexistent target bucket
    When object "hello.txt" is copied to "copy.txt" in bucket "no-such-bucket"
    Then the response status is 404

  Scenario: Delete nonexistent object
    Given object "ghost.txt" does not exist
    When the object is deleted via S3 API
    Then the response status is 204
