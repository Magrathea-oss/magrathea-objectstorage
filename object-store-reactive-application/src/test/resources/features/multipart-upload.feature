Feature: Multipart upload lifecycle
  Multipart upload follows initiate → add parts → complete/abort.

  Scenario: Initiate multipart upload
    Given multipart bucket "mp-bucket" exists
    When I initiate multipart upload with key "large-file.zip"
    Then the result is a Created<MultipartUpload> with version 1
    And the upload has 0 parts
    And the event MultipartUploadCreated is recorded

  Scenario: Add parts to upload
    Given multipart bucket "mp-bucket" exists
    And multipart upload "upload1" exists with 0 parts
    When I add part number 1 to upload "upload1"
    Then the result is an Updated<MultipartUpload> with version 2
    And the upload has 1 part
    And the event PartUploaded is recorded

  Scenario: Complete multipart upload
    Given multipart bucket "mp-bucket" exists
    And multipart upload "upload1" exists with 3 parts
    When I complete upload "upload1"
    Then the result is an Updated<MultipartUpload> with version 2
    And the event MultipartUploadCompleted is recorded

  Scenario: Abort multipart upload
    Given multipart bucket "mp-bucket" exists
    And multipart upload "upload1" exists with 1 part
    When I abort upload "upload1"
    Then the result is an Updated<MultipartUpload> with version 2
    And the event MultipartUploadAborted is recorded

  Scenario: Find upload by ID
    Given multipart bucket "mp-bucket" exists
    And multipart upload "findme" exists with 2 parts
    When I find upload by ID "findme"
    Then the result is a MultipartUpload with 2 parts

  Scenario: Find upload not found
    Given multipart upload "ghost" does not exist
    When I find upload by ID "ghost"
    Then the multipart result is Mono.empty not found
