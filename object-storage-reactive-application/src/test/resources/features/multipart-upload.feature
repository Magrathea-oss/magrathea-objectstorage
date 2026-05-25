Feature: Multipart upload lifecycle
  Multipart upload follows initiate → add parts → complete/abort.

  Scenario: Initiate multipart upload
    Given bucket "mp-bucket" exists
    When I initiate multipart upload with key "large-file.zip"
    Then the result is a Created<MultipartUpload> with version 1
    And the upload has 0 parts
    And the event MultipartUploadCreated is recorded

  Scenario: Add parts to upload
    Given bucket "mp-bucket" exists
    And multipart upload "upload-1" exists with 0 parts
    When I add part number 1 to upload "upload-1"
    Then the result is an Updated<MultipartUpload> with version 2
    And the upload has 1 part
    And the event PartUploaded is recorded

  Scenario: Complete multipart upload
    Given bucket "mp-bucket" exists
    And multipart upload "upload-1" exists with 3 parts
    When I complete upload "upload-1"
    Then the result is an Updated<MultipartUpload> with version 4
    And the event MultipartUploadCompleted is recorded

  Scenario: Abort multipart upload
    Given bucket "mp-bucket" exists
    And multipart upload "upload-1" exists with 1 part
    When I abort upload "upload-1"
    Then the result is an Updated<MultipartUpload> with version 1
    And the event MultipartUploadAborted is recorded

  Scenario: Find upload by ID
    Given multipart upload "find-me" exists with 2 parts
    When I find upload by ID "find-me"
    Then the result is a MultipartUpload with 2 parts

  Scenario: Find upload not found
    Given multipart upload "ghost" does not exist
    When I find upload by ID "ghost"
    Then the result is Mono.empty (not found)
