Feature: S3-compatible Bucket Configuration APIs (CORS, Policy, Encryption, etc.)

  Background:
    Given bucket "test-bucket" exists

  # ── CORS Success ──

  Scenario: Put bucket CORS configuration
    When bucket CORS is configured with origin "http://example.com" and methods "GET,PUT"
    Then the response status is 200

  Scenario: Get bucket CORS configuration
    Given bucket CORS is preset with origin "http://example.com" and methods "GET,PUT"
    When bucket CORS configuration is requested
    Then the response status is 200
    And the metadata response contains "AllowedOrigin"

  Scenario: Delete bucket CORS configuration
    Given bucket CORS is preset with origin "http://example.com" and methods "GET,PUT"
    When bucket CORS configuration is deleted
    Then the response status is 204

  # ── CORS Failure ──

  Scenario: Get CORS for nonexistent bucket
    When bucket CORS configuration is requested for "ghost-bucket"
    Then the response status is 404

  Scenario: Get CORS when no configuration exists
    When bucket CORS configuration is requested
    Then the response status is 404

  Scenario: Put CORS for nonexistent bucket
    When bucket CORS is configured for "ghost-bucket" with origin "*" and methods "GET"
    Then the response status is 404

  Scenario: Delete CORS for nonexistent bucket
    When bucket CORS configuration is deleted for "ghost-bucket"
    Then the response status is 404
