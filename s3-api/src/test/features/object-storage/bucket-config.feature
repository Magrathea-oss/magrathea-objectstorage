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

  # ── Lifecycle Success ──

  Scenario: Put bucket lifecycle configuration
    When bucket lifecycle is configured with rule "expire-30" and status "Enabled"
    Then the response status is 200

  Scenario: Get bucket lifecycle configuration
    Given bucket lifecycle is preset with rule "expire-30" and status "Enabled"
    When bucket lifecycle configuration is requested
    Then the response status is 200
    And the metadata response contains "Status"

  Scenario: Delete bucket lifecycle configuration
    Given bucket lifecycle is preset with rule "expire-30" and status "Enabled"
    When bucket lifecycle configuration is deleted
    Then the response status is 204

  # ── Lifecycle Failure ──

  Scenario: Get lifecycle for nonexistent bucket
    When bucket lifecycle configuration is requested for "ghost-bucket"
    Then the response status is 404

  Scenario: Get lifecycle when no configuration exists
    When bucket lifecycle configuration is requested
    Then the response status is 404

  Scenario: Put lifecycle for nonexistent bucket
    When bucket lifecycle is configured for "ghost-bucket" with rule "expire-30" and status "Enabled"
    Then the response status is 404

  Scenario: Delete lifecycle for nonexistent bucket
    When bucket lifecycle configuration is deleted for "ghost-bucket"
    Then the response status is 404

  # ── Policy Success ──

  Scenario: Put bucket policy
    When bucket policy is set to '{"Version":"2012-10-17","Statement":[{"Effect":"Allow","Principal":"*","Action":"s3:GetObject"}]}'
    Then the response status is 200

  Scenario: Get bucket policy
    Given bucket policy is preset with '{"Version":"2012-10-17","Statement":[{"Effect":"Allow","Principal":"*","Action":"s3:GetObject"}]}'
    When bucket policy is requested
    Then the response status is 200
    And the metadata response contains "s3:GetObject"

  Scenario: Delete bucket policy
    Given bucket policy is preset with '{"Version":"2012-10-17","Statement":[{"Effect":"Allow"}]}'
    When bucket policy is deleted
    Then the response status is 204

  # ── Policy Failure ──

  Scenario: Get policy for nonexistent bucket
    When bucket policy is requested for "ghost-bucket"
    Then the response status is 404

  Scenario: Get policy when no policy exists
    When bucket policy is requested
    Then the response status is 404

  Scenario: Put policy for nonexistent bucket
    When bucket policy is set for "ghost-bucket" to '{"Effect":"Allow"}'
    Then the response status is 404

  Scenario: Delete policy for nonexistent bucket
    When bucket policy is deleted for "ghost-bucket"
    Then the response status is 404

  # ── Encryption Success ──

  Scenario: Put bucket encryption configuration
    When bucket encryption is configured with algorithm "AES256"
    Then the response status is 200

  Scenario: Get bucket encryption configuration
    Given bucket encryption is preset with algorithm "AES256"
    When bucket encryption configuration is requested
    Then the response status is 200
    And the metadata response contains "Algorithm"

  Scenario: Delete bucket encryption configuration
    Given bucket encryption is preset with algorithm "AES256"
    When bucket encryption configuration is deleted
    Then the response status is 204

  # ── Encryption Failure ──

  Scenario: Get encryption for nonexistent bucket
    When bucket encryption configuration is requested for "ghost-bucket"
    Then the response status is 404

  Scenario: Get encryption when no configuration exists
    When bucket encryption configuration is requested
    Then the response status is 404

  Scenario: Put encryption for nonexistent bucket
    When bucket encryption is configured for "ghost-bucket" with algorithm "AES256"
    Then the response status is 404

  Scenario: Delete encryption for nonexistent bucket
    When bucket encryption configuration is deleted for "ghost-bucket"
    Then the response status is 404

  # ── Logging Success ──

  Scenario: Put bucket logging configuration
    When bucket logging is configured with target bucket "log-bucket" and prefix "test/"
    Then the response status is 200

  Scenario: Get bucket logging configuration
    Given bucket logging is preset with target bucket "log-bucket" and prefix "test/"
    When bucket logging configuration is requested
    Then the response status is 200
    And the metadata response contains "TargetBucket"

  Scenario: Delete bucket logging configuration
    Given bucket logging is preset with target bucket "log-bucket" and prefix "test/"
    When bucket logging configuration is deleted
    Then the response status is 204

  # ── Logging Failure ──

  Scenario: Get logging for nonexistent bucket
    When bucket logging configuration is requested for "ghost-bucket"
    Then the response status is 404

  Scenario: Get logging when no configuration exists
    When bucket logging configuration is requested
    Then the response status is 404

  Scenario: Put logging for nonexistent bucket
    When bucket logging is configured for "ghost-bucket" with target bucket "log-bucket" and prefix "test/"
    Then the response status is 404

  Scenario: Delete logging for nonexistent bucket
    When bucket logging configuration is deleted for "ghost-bucket"
    Then the response status is 404

  # ── Website Success ──

  Scenario: Put bucket website configuration
    When bucket website is configured with index "index.html"
    Then the response status is 200

  Scenario: Get bucket website configuration
    Given bucket website is preset with index "index.html"
    When bucket website configuration is requested
    Then the response status is 200
    And the metadata response contains "IndexDocument"

  Scenario: Delete bucket website configuration
    Given bucket website is preset with index "index.html"
    When bucket website configuration is deleted
    Then the response status is 204

  # ── Website Failure ──

  Scenario: Get website for nonexistent bucket
    When bucket website configuration is requested for "ghost-bucket"
    Then the response status is 404

  Scenario: Get website when no configuration exists
    When bucket website configuration is requested
    Then the response status is 404

  Scenario: Put website for nonexistent bucket
    When bucket website is configured for "ghost-bucket" with index "index.html"
    Then the response status is 404

  Scenario: Delete website for nonexistent bucket
    When bucket website configuration is deleted for "ghost-bucket"
    Then the response status is 404

  # ── Notification Success ──

  Scenario: Put bucket notification configuration
    When bucket notification is configured with event "s3:ObjectCreated:*"
    Then the response status is 200

  Scenario: Get bucket notification configuration
    Given bucket notification is preset with event "s3:ObjectCreated:*"
    When bucket notification configuration is requested
    Then the response status is 200
    And the metadata response contains "EventConfiguration"

  Scenario: Delete bucket notification configuration
    Given bucket notification is preset with event "s3:ObjectCreated:*"
    When bucket notification configuration is deleted
    Then the response status is 204

  # ── Notification Failure ──

  Scenario: Get notification for nonexistent bucket
    When bucket notification configuration is requested for "ghost-bucket"
    Then the response status is 404

  Scenario: Get notification when no configuration exists
    When bucket notification configuration is requested
    Then the response status is 404

  Scenario: Put notification for nonexistent bucket
    When bucket notification is configured for "ghost-bucket" with event "s3:ObjectCreated:*"
    Then the response status is 404

  Scenario: Delete notification for nonexistent bucket
    When bucket notification configuration is deleted for "ghost-bucket"
    Then the response status is 404

  # ── Replication Success ──

  Scenario: Put bucket replication configuration
    When bucket replication is configured with role "arn:aws:iam::123:role/s3-replication"
    Then the response status is 200

  Scenario: Get bucket replication configuration
    Given bucket replication is preset with role "arn:aws:iam::123:role/s3-replication"
    When bucket replication configuration is requested
    Then the response status is 200
    And the metadata response contains "Role"

  Scenario: Delete bucket replication configuration
    Given bucket replication is preset with role "arn:aws:iam::123:role/s3-replication"
    When bucket replication configuration is deleted
    Then the response status is 204

  # ── Replication Failure ──

  Scenario: Get replication for nonexistent bucket
    When bucket replication configuration is requested for "ghost-bucket"
    Then the response status is 404

  Scenario: Get replication when no configuration exists
    When bucket replication configuration is requested
    Then the response status is 404

  Scenario: Put replication for nonexistent bucket
    When bucket replication is configured for "ghost-bucket" with role "arn:aws:iam::123:role/s3-replication"
    Then the response status is 404

  Scenario: Delete replication for nonexistent bucket
    When bucket replication configuration is deleted for "ghost-bucket"
    Then the response status is 404

  # ── Request Payment Success ──

  Scenario: Put bucket request payment configuration
    When bucket request payment is configured with payer "Requester"
    Then the response status is 200

  Scenario: Get bucket request payment configuration
    Given bucket request payment is preset with payer "Requester"
    When bucket request payment configuration is requested
    Then the response status is 200
    And the metadata response contains "Payer"

  Scenario: Delete bucket request payment configuration
    Given bucket request payment is preset with payer "Requester"
    When bucket request payment configuration is deleted
    Then the response status is 204

  # ── Request Payment Failure ──

  Scenario: Get request payment for nonexistent bucket
    When bucket request payment configuration is requested for "ghost-bucket"
    Then the response status is 404

  Scenario: Get request payment when no configuration exists
    When bucket request payment configuration is requested
    Then the response status is 404

  Scenario: Put request payment for nonexistent bucket
    When bucket request payment is configured for "ghost-bucket" with payer "Requester"
    Then the response status is 404

  Scenario: Delete request payment for nonexistent bucket
    When bucket request payment configuration is deleted for "ghost-bucket"
    Then the response status is 404

  # ── Ownership Controls Success ──

  Scenario: Put bucket ownership controls
    When bucket ownership controls are configured with ownership "BucketOwnerPreferred"
    Then the response status is 200

  Scenario: Get bucket ownership controls
    Given bucket ownership controls are preset with ownership "BucketOwnerPreferred"
    When bucket ownership controls are requested
    Then the response status is 200
    And the metadata response contains "Ownership"

  Scenario: Delete bucket ownership controls
    Given bucket ownership controls are preset with ownership "BucketOwnerPreferred"
    When bucket ownership controls are deleted
    Then the response status is 204

  # ── Ownership Controls Failure ──

  Scenario: Get ownership controls for nonexistent bucket
    When bucket ownership controls are requested for "ghost-bucket"
    Then the response status is 404

  Scenario: Get ownership controls when no configuration exists
    When bucket ownership controls are requested
    Then the response status is 404

  Scenario: Put ownership controls for nonexistent bucket
    When bucket ownership controls are configured for "ghost-bucket" with ownership "BucketOwnerPreferred"
    Then the response status is 404

  Scenario: Delete ownership controls for nonexistent bucket
    When bucket ownership controls are deleted for "ghost-bucket"
    Then the response status is 404

  # ── Public Access Block Success ──

  Scenario: Put bucket public access block configuration
    When bucket public access block is configured with blockPublicAcls "true"
    Then the response status is 200

  Scenario: Get bucket public access block configuration
    Given bucket public access block is preset with blockPublicAcls "true"
    When bucket public access block configuration is requested
    Then the response status is 200
    And the metadata response contains "BlockPublicAcls"

  Scenario: Delete bucket public access block configuration
    Given bucket public access block is preset with blockPublicAcls "true"
    When bucket public access block configuration is deleted
    Then the response status is 204

  # ── Public Access Block Failure ──

  Scenario: Get public access block for nonexistent bucket
    When bucket public access block configuration is requested for "ghost-bucket"
    Then the response status is 404

  Scenario: Get public access block when no configuration exists
    When bucket public access block configuration is requested
    Then the response status is 404

  Scenario: Put public access block for nonexistent bucket
    When bucket public access block is configured for "ghost-bucket" with blockPublicAcls "true"
    Then the response status is 404

  Scenario: Delete public access block for nonexistent bucket
    When bucket public access block configuration is deleted for "ghost-bucket"
    Then the response status is 404

  # ── Accelerate Success ──

  Scenario: Put bucket accelerate configuration
    When bucket accelerate is configured with status "Enabled"
    Then the response status is 200

  Scenario: Get bucket accelerate configuration
    Given bucket accelerate is preset with status "Enabled"
    When bucket accelerate configuration is requested
    Then the response status is 200
    And the metadata response contains "Status"

  Scenario: Delete bucket accelerate configuration
    Given bucket accelerate is preset with status "Enabled"
    When bucket accelerate configuration is deleted
    Then the response status is 204

  # ── Accelerate Failure ──

  Scenario: Get accelerate for nonexistent bucket
    When bucket accelerate configuration is requested for "ghost-bucket"
    Then the response status is 404

  Scenario: Get accelerate when no configuration exists
    When bucket accelerate configuration is requested
    Then the response status is 404

  Scenario: Put accelerate for nonexistent bucket
    When bucket accelerate is configured for "ghost-bucket" with status "Enabled"
    Then the response status is 404

  Scenario: Delete accelerate for nonexistent bucket
    When bucket accelerate configuration is deleted for "ghost-bucket"
    Then the response status is 404
