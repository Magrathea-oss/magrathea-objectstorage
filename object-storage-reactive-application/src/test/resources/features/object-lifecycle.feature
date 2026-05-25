Feature: Object lifecycle with ContentDescriptor
  Objects use ContentDescriptor instead of raw byte[] in the domain.

  Scenario: Create object with content descriptor
    Given bucket "obj-bucket" exists
    And object "doc.txt" does not exist
    When I create object "doc.txt" with content descriptor of size 1024
    Then the result is a Created<S3Object> with version 1
    And the event ContentDescriptorCreated is recorded
    And the object has content descriptor with size 1024

  Scenario: Find object by bucket and key
    Given bucket "obj-bucket" exists
    And object "doc.txt" exists
    When I find object "doc.txt" in bucket "obj-bucket"
    Then the result is an S3Object with key "doc.txt"

  Scenario: Find object not found
    Given bucket "obj-bucket" exists
    And object "ghost.txt" does not exist
    When I find object "ghost.txt" in bucket "obj-bucket"
    Then the result is Mono.empty (not found)

  Scenario: Delete object
    Given bucket "obj-bucket" exists
    And object "del-me.txt" exists
    When I delete object "del-me.txt"
    Then the result is a Deleted<S3Object> with version 2
    And the event ObjectDeleted is recorded

  Scenario: Delete nonexistent object
    Given bucket "obj-bucket" exists
    And object "ghost.txt" does not exist
    When I delete object "ghost.txt"
    Then the result is Mono.error with S3ObjectNotFoundException
