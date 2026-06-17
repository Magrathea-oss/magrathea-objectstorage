@webclient
Feature: S3-compatible Bucket Configuration APIs (CORS, Policy, Encryption, etc.)

  Background:
    Given bucket "test-bucket" exists

  # ── CORS Success ──

  @webclient
  Scenario: Put bucket CORS configuration
    When bucket CORS is configured with origin "http://example.com" and methods "GET,PUT"
    Then the response status is 200

  @webclient
  Scenario: Get bucket CORS configuration
    Given bucket CORS is preset with origin "http://example.com" and methods "GET,PUT"
    When bucket CORS configuration is requested
    Then the response status is 200
    And the metadata response contains "AllowedOrigin"

  @webclient
  Scenario: Delete bucket CORS configuration
    Given bucket CORS is preset with origin "http://example.com" and methods "GET,PUT"
    When bucket CORS configuration is deleted
    Then the response status is 204

  # ── CORS Failure ──

  @webclient
  Scenario: Get CORS for nonexistent bucket
    When bucket CORS configuration is requested for "ghost-bucket"
    Then the response status is 404

  @webclient
  Scenario: Get CORS when no configuration exists
    When bucket CORS configuration is requested
    Then the response status is 404

  @webclient
  Scenario: Put CORS for nonexistent bucket
    When bucket CORS is configured for "ghost-bucket" with origin "*" and methods "GET"
    Then the response status is 404

  @webclient
  Scenario: Delete CORS for nonexistent bucket
    When bucket CORS configuration is deleted for "ghost-bucket"
    Then the response status is 404

  # ── Lifecycle Success ──

  @webclient
  Scenario: Put bucket lifecycle configuration
    When bucket lifecycle is configured with rule "expire-30" and status "Enabled"
    Then the response status is 200

  @webclient
  Scenario: Get bucket lifecycle configuration
    Given bucket lifecycle is preset with rule "expire-30" and status "Enabled"
    When bucket lifecycle configuration is requested
    Then the response status is 200
    And the metadata response contains "Status"

  @webclient
  Scenario: Delete bucket lifecycle configuration
    Given bucket lifecycle is preset with rule "expire-30" and status "Enabled"
    When bucket lifecycle configuration is deleted
    Then the response status is 204

  # ── Lifecycle Failure ──

  @webclient
  Scenario: Get lifecycle for nonexistent bucket
    When bucket lifecycle configuration is requested for "ghost-bucket"
    Then the response status is 404

  @webclient
  Scenario: Get lifecycle when no configuration exists
    When bucket lifecycle configuration is requested
    Then the response status is 404

  @webclient
  Scenario: Put lifecycle for nonexistent bucket
    When bucket lifecycle is configured for "ghost-bucket" with rule "expire-30" and status "Enabled"
    Then the response status is 404

  @webclient
  Scenario: Delete lifecycle for nonexistent bucket
    When bucket lifecycle configuration is deleted for "ghost-bucket"
    Then the response status is 404

  # ── Policy Success ──

  @webclient
  Scenario: Put bucket policy
    When bucket policy is set to '{"Version":"2012-10-17","Statement":[{"Effect":"Allow","Principal":"*","Action":"s3:GetObject"}]}'
    Then the response status is 200

  @webclient
  Scenario: Get bucket policy
    Given bucket policy is preset with '{"Version":"2012-10-17","Statement":[{"Effect":"Allow","Principal":"*","Action":"s3:GetObject"}]}'
    When bucket policy is requested
    Then the response status is 200
    And the metadata response contains "s3:GetObject"

  @webclient
  Scenario: Delete bucket policy
    Given bucket policy is preset with '{"Version":"2012-10-17","Statement":[{"Effect":"Allow"}]}'
    When bucket policy is deleted
    Then the response status is 204

  # ── Policy Failure ──

  @webclient
  Scenario: Get policy for nonexistent bucket
    When bucket policy is requested for "ghost-bucket"
    Then the response status is 404

  @webclient
  Scenario: Get policy when no policy exists
    When bucket policy is requested
    Then the response status is 404

  @webclient
  Scenario: Put policy for nonexistent bucket
    When bucket policy is set for "ghost-bucket" to '{"Effect":"Allow"}'
    Then the response status is 404

  @webclient
  Scenario: Delete policy for nonexistent bucket
    When bucket policy is deleted for "ghost-bucket"
    Then the response status is 404

  # ── Encryption Success ──

  @webclient
  Scenario: Put bucket encryption configuration
    When bucket encryption is configured with algorithm "AES256"
    Then the response status is 200

  @webclient
  Scenario: Get bucket encryption configuration
    Given bucket encryption is preset with algorithm "AES256"
    When bucket encryption configuration is requested
    Then the response status is 200
    And the metadata response contains "Algorithm"

  @webclient
  Scenario: Delete bucket encryption configuration
    Given bucket encryption is preset with algorithm "AES256"
    When bucket encryption configuration is deleted
    Then the response status is 204

  # ── Encryption Failure ──

  @webclient
  Scenario: Get encryption for nonexistent bucket
    When bucket encryption configuration is requested for "ghost-bucket"
    Then the response status is 404

  @webclient
  Scenario: Get encryption when no configuration exists
    When bucket encryption configuration is requested
    Then the response status is 404

  @webclient
  Scenario: Put encryption for nonexistent bucket
    When bucket encryption is configured for "ghost-bucket" with algorithm "AES256"
    Then the response status is 404

  @webclient
  Scenario: Delete encryption for nonexistent bucket
    When bucket encryption configuration is deleted for "ghost-bucket"
    Then the response status is 404

  # ── Logging Success ──

  @webclient
  Scenario: Put bucket logging configuration
    When bucket logging is configured with target bucket "log-bucket" and prefix "test/"
    Then the response status is 200

  @webclient
  Scenario: Get bucket logging configuration
    Given bucket logging is preset with target bucket "log-bucket" and prefix "test/"
    When bucket logging configuration is requested
    Then the response status is 200
    And the metadata response contains "TargetBucket"

  @webclient
  Scenario: Delete bucket logging configuration
    Given bucket logging is preset with target bucket "log-bucket" and prefix "test/"
    When bucket logging configuration is deleted
    Then the response status is 204

  # ── Logging Failure ──

  @webclient
  Scenario: Get logging for nonexistent bucket
    When bucket logging configuration is requested for "ghost-bucket"
    Then the response status is 404

  @webclient
  Scenario: Get logging when no configuration exists
    When bucket logging configuration is requested
    Then the response status is 404

  @webclient
  Scenario: Put logging for nonexistent bucket
    When bucket logging is configured for "ghost-bucket" with target bucket "log-bucket" and prefix "test/"
    Then the response status is 404

  @webclient
  Scenario: Delete logging for nonexistent bucket
    When bucket logging configuration is deleted for "ghost-bucket"
    Then the response status is 404

  # ── Website Success ──

  @webclient
  Scenario: Put bucket website configuration
    When bucket website is configured with index "index.html"
    Then the response status is 200

  @webclient
  Scenario: Get bucket website configuration
    Given bucket website is preset with index "index.html"
    When bucket website configuration is requested
    Then the response status is 200
    And the metadata response contains "IndexDocument"

  @webclient
  Scenario: Delete bucket website configuration
    Given bucket website is preset with index "index.html"
    When bucket website configuration is deleted
    Then the response status is 204

  # ── Website Failure ──

  @webclient
  Scenario: Get website for nonexistent bucket
    When bucket website configuration is requested for "ghost-bucket"
    Then the response status is 404

  @webclient
  Scenario: Get website when no configuration exists
    When bucket website configuration is requested
    Then the response status is 404

  @webclient
  Scenario: Put website for nonexistent bucket
    When bucket website is configured for "ghost-bucket" with index "index.html"
    Then the response status is 404

  @webclient
  Scenario: Delete website for nonexistent bucket
    When bucket website configuration is deleted for "ghost-bucket"
    Then the response status is 404

  # ── Notification Success ──

  @webclient
  Scenario: Put bucket notification configuration
    When bucket notification is configured with event "s3:ObjectCreated:*"
    Then the response status is 200

  @webclient
  Scenario: Get bucket notification configuration
    Given bucket notification is preset with event "s3:ObjectCreated:*"
    When bucket notification configuration is requested
    Then the response status is 200
    And the metadata response contains "EventConfiguration"

  @webclient
  Scenario: Delete bucket notification configuration
    Given bucket notification is preset with event "s3:ObjectCreated:*"
    When bucket notification configuration is deleted
    Then the response status is 204

  # ── Notification Failure ──

  @webclient
  Scenario: Get notification for nonexistent bucket
    When bucket notification configuration is requested for "ghost-bucket"
    Then the response status is 404

  @webclient
  Scenario: Get notification when no configuration exists
    When bucket notification configuration is requested
    Then the response status is 404

  @webclient
  Scenario: Put notification for nonexistent bucket
    When bucket notification is configured for "ghost-bucket" with event "s3:ObjectCreated:*"
    Then the response status is 404

  @webclient
  Scenario: Delete notification for nonexistent bucket
    When bucket notification configuration is deleted for "ghost-bucket"
    Then the response status is 404

  # ── Replication Success ──

  @webclient
  Scenario: Put bucket replication configuration
    When bucket replication is configured with role "arn:aws:iam::123:role/s3-replication"
    Then the response status is 200

  @webclient
  Scenario: Get bucket replication configuration
    Given bucket replication is preset with role "arn:aws:iam::123:role/s3-replication"
    When bucket replication configuration is requested
    Then the response status is 200
    And the metadata response contains "Role"

  @webclient
  Scenario: Delete bucket replication configuration
    Given bucket replication is preset with role "arn:aws:iam::123:role/s3-replication"
    When bucket replication configuration is deleted
    Then the response status is 204

  # ── Replication Failure ──

  @webclient
  Scenario: Get replication for nonexistent bucket
    When bucket replication configuration is requested for "ghost-bucket"
    Then the response status is 404

  @webclient
  Scenario: Get replication when no configuration exists
    When bucket replication configuration is requested
    Then the response status is 404

  @webclient
  Scenario: Put replication for nonexistent bucket
    When bucket replication is configured for "ghost-bucket" with role "arn:aws:iam::123:role/s3-replication"
    Then the response status is 404

  @webclient
  Scenario: Delete replication for nonexistent bucket
    When bucket replication configuration is deleted for "ghost-bucket"
    Then the response status is 404

  # ── Request Payment Success ──

  @webclient
  Scenario: Put bucket request payment configuration
    When bucket request payment is configured with payer "Requester"
    Then the response status is 200

  @webclient
  Scenario: Get bucket request payment configuration
    Given bucket request payment is preset with payer "Requester"
    When bucket request payment configuration is requested
    Then the response status is 200
    And the metadata response contains "Payer"

  @webclient
  Scenario: Delete bucket request payment configuration
    Given bucket request payment is preset with payer "Requester"
    When bucket request payment configuration is deleted
    Then the response status is 204

  # ── Request Payment Failure ──

  @webclient
  Scenario: Get request payment for nonexistent bucket
    When bucket request payment configuration is requested for "ghost-bucket"
    Then the response status is 404

  @webclient
  Scenario: Get request payment when no configuration exists
    When bucket request payment configuration is requested
    Then the response status is 404

  @webclient
  Scenario: Put request payment for nonexistent bucket
    When bucket request payment is configured for "ghost-bucket" with payer "Requester"
    Then the response status is 404

  @webclient
  Scenario: Delete request payment for nonexistent bucket
    When bucket request payment configuration is deleted for "ghost-bucket"
    Then the response status is 404

  # ── Ownership Controls Success ──

  @webclient
  Scenario: Put bucket ownership controls
    When bucket ownership controls are configured with ownership "BucketOwnerPreferred"
    Then the response status is 200

  @webclient
  Scenario: Get bucket ownership controls
    Given bucket ownership controls are preset with ownership "BucketOwnerPreferred"
    When bucket ownership controls are requested
    Then the response status is 200
    And the metadata response contains "Ownership"

  @webclient
  Scenario: Delete bucket ownership controls
    Given bucket ownership controls are preset with ownership "BucketOwnerPreferred"
    When bucket ownership controls are deleted
    Then the response status is 204

  # ── Ownership Controls Failure ──

  @webclient
  Scenario: Get ownership controls for nonexistent bucket
    When bucket ownership controls are requested for "ghost-bucket"
    Then the response status is 404

  @webclient
  Scenario: Get ownership controls when no configuration exists
    When bucket ownership controls are requested
    Then the response status is 404

  @webclient
  Scenario: Put ownership controls for nonexistent bucket
    When bucket ownership controls are configured for "ghost-bucket" with ownership "BucketOwnerPreferred"
    Then the response status is 404

  @webclient
  Scenario: Delete ownership controls for nonexistent bucket
    When bucket ownership controls are deleted for "ghost-bucket"
    Then the response status is 404

  # ── Public Access Block Success ──

  @webclient
  Scenario: Put bucket public access block configuration
    When bucket public access block is configured with blockPublicAcls "true"
    Then the response status is 200

  @webclient
  Scenario: Get bucket public access block configuration
    Given bucket public access block is preset with blockPublicAcls "true"
    When bucket public access block configuration is requested
    Then the response status is 200
    And the metadata response contains "BlockPublicAcls"

  @webclient
  Scenario: Delete bucket public access block configuration
    Given bucket public access block is preset with blockPublicAcls "true"
    When bucket public access block configuration is deleted
    Then the response status is 204

  # ── Public Access Block Failure ──

  @webclient
  Scenario: Get public access block for nonexistent bucket
    When bucket public access block configuration is requested for "ghost-bucket"
    Then the response status is 404

  @webclient
  Scenario: Get public access block when no configuration exists
    When bucket public access block configuration is requested
    Then the response status is 404

  @webclient
  Scenario: Put public access block for nonexistent bucket
    When bucket public access block is configured for "ghost-bucket" with blockPublicAcls "true"
    Then the response status is 404

  @webclient
  Scenario: Delete public access block for nonexistent bucket
    When bucket public access block configuration is deleted for "ghost-bucket"
    Then the response status is 404

  # ── Accelerate Success ──

  @webclient
  Scenario: Put bucket accelerate configuration
    When bucket accelerate is configured with status "Enabled"
    Then the response status is 200

  @webclient
  Scenario: Get bucket accelerate configuration
    Given bucket accelerate is preset with status "Enabled"
    When bucket accelerate configuration is requested
    Then the response status is 200
    And the metadata response contains "Status"

  @webclient
  Scenario: Delete bucket accelerate configuration
    Given bucket accelerate is preset with status "Enabled"
    When bucket accelerate configuration is deleted
    Then the response status is 204

  # ── Accelerate Failure ──

  @webclient
  Scenario: Get accelerate for nonexistent bucket
    When bucket accelerate configuration is requested for "ghost-bucket"
    Then the response status is 404

  @webclient
  Scenario: Get accelerate when no configuration exists
    When bucket accelerate configuration is requested
    Then the response status is 404

  @webclient
  Scenario: Put accelerate for nonexistent bucket
    When bucket accelerate is configured for "ghost-bucket" with status "Enabled"
    Then the response status is 404

  @webclient
  Scenario: Delete accelerate for nonexistent bucket
    When bucket accelerate configuration is deleted for "ghost-bucket"
    Then the response status is 404

  # ── Analytics Success ──

  @webclient
  Scenario: Put bucket analytics configuration
    When bucket analytics is configured with id "my-analytics" and filter "documents/"
    Then the response status is 200

  @webclient
  Scenario: Get bucket analytics configuration
    Given bucket analytics is preset with id "my-analytics" and filter "documents/"
    When bucket analytics configuration is requested for id "my-analytics"
    Then the response status is 200
    And the metadata response contains "Id"

  @webclient
  Scenario: Delete bucket analytics configuration
    Given bucket analytics is preset with id "my-analytics" and filter "documents/"
    When bucket analytics configuration is deleted for id "my-analytics"
    Then the response status is 204

  @webclient
  Scenario: List bucket analytics configurations
    Given bucket analytics is preset with id "my-analytics" and filter "documents/"
    When bucket analytics configurations are listed
    Then the response status is 200

  # ── Analytics Failure ──

  @webclient
  Scenario: Get analytics for nonexistent bucket
    When bucket analytics configuration is requested for "ghost-bucket" with id "x"
    Then the response status is 404

  @webclient
  Scenario: Get analytics when no configuration exists
    When bucket analytics configuration is requested for id "missing-id"
    Then the response status is 404

  @webclient
  Scenario: Put analytics for nonexistent bucket
    When bucket analytics is configured for "ghost-bucket" with id "x" and filter ""
    Then the response status is 404

  @webclient
  Scenario: Delete analytics for nonexistent bucket
    When bucket analytics configuration is deleted for "ghost-bucket" with id "x"
    Then the response status is 404

  # ── Inventory Success ──

  @webclient
  Scenario: Put bucket inventory configuration
    When bucket inventory is configured with id "my-inventory" and format "CSV"
    Then the response status is 200

  @webclient
  Scenario: Get bucket inventory configuration
    Given bucket inventory is preset with id "my-inventory" and format "CSV"
    When bucket inventory configuration is requested for id "my-inventory"
    Then the response status is 200
    And the metadata response contains "Id"

  @webclient
  Scenario: Delete bucket inventory configuration
    Given bucket inventory is preset with id "my-inventory" and format "CSV"
    When bucket inventory configuration is deleted for id "my-inventory"
    Then the response status is 204

  @webclient
  Scenario: List bucket inventory configurations
    Given bucket inventory is preset with id "my-inventory" and format "CSV"
    When bucket inventory configurations are listed
    Then the response status is 200

  # ── Inventory Failure ──

  @webclient
  Scenario: Get inventory for nonexistent bucket
    When bucket inventory configuration is requested for "ghost-bucket" with id "x"
    Then the response status is 404

  @webclient
  Scenario: Get inventory when no configuration exists
    When bucket inventory configuration is requested for id "missing-id"
    Then the response status is 404

  @webclient
  Scenario: Put inventory for nonexistent bucket
    When bucket inventory is configured for "ghost-bucket" with id "x" and format "CSV"
    Then the response status is 404

  @webclient
  Scenario: Delete inventory for nonexistent bucket
    When bucket inventory configuration is deleted for "ghost-bucket" with id "x"
    Then the response status is 404

  # ── Metrics Success ──

  @webclient
  Scenario: Put bucket metrics configuration
    When bucket metrics is configured with id "my-metrics"
    Then the response status is 200

  @webclient
  Scenario: Get bucket metrics configuration
    Given bucket metrics is preset with id "my-metrics"
    When bucket metrics configuration is requested
    Then the response status is 200
    And the metadata response contains "Id"

  @webclient
  Scenario: Delete bucket metrics configuration
    Given bucket metrics is preset with id "my-metrics"
    When bucket metrics configuration is deleted
    Then the response status is 204

  # ── Metrics Failure ──

  @webclient
  Scenario: Get metrics for nonexistent bucket
    When bucket metrics configuration is requested for "ghost-bucket"
    Then the response status is 404

  @webclient
  Scenario: Get metrics when no configuration exists
    When bucket metrics configuration is requested
    Then the response status is 404

  @webclient
  Scenario: Put metrics for nonexistent bucket
    When bucket metrics is configured for "ghost-bucket" with id "x"
    Then the response status is 404

  @webclient
  Scenario: Delete metrics for nonexistent bucket
    When bucket metrics configuration is deleted for "ghost-bucket"
    Then the response status is 404

  # ── Intelligent-Tiering Success ──

  @webclient
  Scenario: Put bucket intelligent-tiering configuration
    When bucket intelligent-tiering is configured with id "my-tiering" and status "ACTIVE"
    Then the response status is 200

  @webclient
  Scenario: Get bucket intelligent-tiering configuration
    Given bucket intelligent-tiering is preset with id "my-tiering" and status "ACTIVE"
    When bucket intelligent-tiering configuration is requested
    Then the response status is 200
    And the metadata response contains "Id"

  @webclient
  Scenario: Delete bucket intelligent-tiering configuration
    Given bucket intelligent-tiering is preset with id "my-tiering" and status "ACTIVE"
    When bucket intelligent-tiering configuration is deleted
    Then the response status is 204

  # ── Intelligent-Tiering Failure ──

  @webclient
  Scenario: Get intelligent-tiering for nonexistent bucket
    When bucket intelligent-tiering configuration is requested for "ghost-bucket"
    Then the response status is 404

  @webclient
  Scenario: Get intelligent-tiering when no configuration exists
    When bucket intelligent-tiering configuration is requested
    Then the response status is 404

  @webclient
  Scenario: Put intelligent-tiering for nonexistent bucket
    When bucket intelligent-tiering is configured for "ghost-bucket" with id "x" and status "ACTIVE"
    Then the response status is 404

  @webclient
  Scenario: Delete intelligent-tiering for nonexistent bucket
    When bucket intelligent-tiering configuration is deleted for "ghost-bucket"
    Then the response status is 404

  # ── ABAC Success (Batch 2) ──

  @webclient
  Scenario: Put bucket ABAC configuration
    When bucket ABAC is configured with rule id "abac-1" and principal "arn:aws:iam::123:user/admin"
    Then the response status is 200

  @webclient
  Scenario: Get bucket ABAC configuration
    Given bucket ABAC is preset with rule id "abac-1" and principal "arn:aws:iam::123:user/admin"
    When bucket ABAC configuration is requested
    Then the response status is 200
    And the metadata response contains "AbacRule"

  # ── ABAC Failure ──

  @webclient
  Scenario: Get ABAC for nonexistent bucket
    When bucket ABAC configuration is requested for "ghost-bucket"
    Then the response status is 404

  @webclient
  Scenario: Get ABAC when no configuration exists
    When bucket ABAC configuration is requested
    Then the response status is 404

  @webclient
  Scenario: Put ABAC for nonexistent bucket
    When bucket ABAC is configured for "ghost-bucket" with rule id "x" and principal "*"
    Then the response status is 404

  # ── Object Lock Configuration Success (Batch 5) ──

  @webclient
  Scenario: Put bucket object lock configuration
    When bucket object lock is configured with mode "GOVERNANCE" and days 5
    Then the response status is 200

  @webclient
  Scenario: Get bucket object lock configuration
    Given bucket object lock is preset with mode "GOVERNANCE" and days 5
    When bucket object lock configuration is requested
    Then the response status is 200
    And the metadata response contains "DefaultRetention"

  # ── Object Lock Failure ──

  @webclient
  Scenario: Get object lock for nonexistent bucket
    When bucket object lock configuration is requested for "ghost-bucket"
    Then the response status is 404

  @webclient
  Scenario: Get object lock when no configuration exists
    When bucket object lock configuration is requested
    Then the response status is 200
    And the metadata response contains "ObjectLockEnabled"

  @webclient
  Scenario: Put object lock for nonexistent bucket
    When bucket object lock is configured for "ghost-bucket" with mode "GOVERNANCE" and days 5
    Then the response status is 404

  # ── Metadata Configuration Success (Batch 2) ──

  @webclient
  Scenario: Put bucket metadata configuration
    When bucket metadata is configured with rule id "meta-1" and status "Enabled"
    Then the response status is 200

  @webclient
  Scenario: Get bucket metadata configuration
    Given bucket metadata is preset with rule id "meta-1" and status "Enabled"
    When bucket metadata configuration is requested
    Then the response status is 200
    And the metadata response contains "MetadataResourceType"

  @webclient
  Scenario: Delete bucket metadata configuration
    Given bucket metadata is preset with rule id "meta-1" and status "Enabled"
    When bucket metadata configuration is deleted
    Then the response status is 204

  # ── Metadata Configuration Failure ──

  @webclient
  Scenario: Get metadata config for nonexistent bucket
    When bucket metadata configuration is requested for "ghost-bucket"
    Then the response status is 404

  @webclient
  Scenario: Get metadata config when no configuration exists
    When bucket metadata configuration is requested
    Then the response status is 404

  @webclient
  Scenario: Put metadata config for nonexistent bucket
    When bucket metadata is configured for "ghost-bucket" with rule id "x" and status "Enabled"
    Then the response status is 404

  @webclient
  Scenario: Delete metadata config for nonexistent bucket
    When bucket metadata configuration is deleted for "ghost-bucket"
    Then the response status is 404

  # ── Metadata Table Configuration Success (Batch 2) ──

  @webclient
  Scenario: Put bucket metadata table configuration
    When bucket metadata table is configured with rule id "meta-table-1" and table name "my-table"
    Then the response status is 200

  @webclient
  Scenario: Get bucket metadata table configuration
    Given bucket metadata table is preset with rule id "meta-table-1" and table name "my-table"
    When bucket metadata table configuration is requested
    Then the response status is 200
    And the metadata response contains "MetadataTableName"

  @webclient
  Scenario: Delete bucket metadata table configuration
    Given bucket metadata table is preset with rule id "meta-table-1" and table name "my-table"
    When bucket metadata table configuration is deleted
    Then the response status is 204

  # ── Metadata Table Configuration Failure ──

  @webclient
  Scenario: Get metadata table config for nonexistent bucket
    When bucket metadata table configuration is requested for "ghost-bucket"
    Then the response status is 404

  @webclient
  Scenario: Get metadata table config when no configuration exists
    When bucket metadata table configuration is requested
    Then the response status is 404

  @webclient
  Scenario: Put metadata table config for nonexistent bucket
    When bucket metadata table is configured for "ghost-bucket" with rule id "x" and table name "x"
    Then the response status is 404

  @webclient
  Scenario: Delete metadata table config for nonexistent bucket
    When bucket metadata table configuration is deleted for "ghost-bucket"
    Then the response status is 404

  # ── Inventory Table Configuration Success (Batch 2) ──

  @webclient
  Scenario: Put bucket inventory table configuration
    When bucket inventory table is configured with id "inv-table-1" and format "CSV"
    Then the response status is 200

  @webclient
  Scenario: Get bucket inventory table configuration
    Given bucket inventory table is preset with id "inv-table-1" and format "CSV"
    When bucket inventory table configuration is requested
    Then the response status is 200
    And the metadata response contains "DestinationFormat"

  # ── Inventory Table Configuration Failure ──

  @webclient
  Scenario: Get inventory table config for nonexistent bucket
    When bucket inventory table configuration is requested for "ghost-bucket"
    Then the response status is 404

  @webclient
  Scenario: Get inventory table config when no configuration exists
    When bucket inventory table configuration is requested
    Then the response status is 404

  @webclient
  Scenario: Put inventory table config for nonexistent bucket
    When bucket inventory table is configured for "ghost-bucket" with id "x" and format "CSV"
    Then the response status is 404

  # ── Journal Table Configuration Success (Batch 2) ──

  @webclient
  Scenario: Put bucket journal table configuration
    When bucket journal table is configured with id "journal-table-1" and format "JSON"
    Then the response status is 200

  @webclient
  Scenario: Get bucket journal table configuration
    Given bucket journal table is preset with id "journal-table-1" and format "JSON"
    When bucket journal table configuration is requested
    Then the response status is 200
    And the metadata response contains "DestinationFormat"

  # ── Journal Table Configuration Failure ──

  @webclient
  Scenario: Get journal table config for nonexistent bucket
    When bucket journal table configuration is requested for "ghost-bucket"
    Then the response status is 404

  @webclient
  Scenario: Get journal table config when no configuration exists
    When bucket journal table configuration is requested
    Then the response status is 404

  @webclient
  Scenario: Put journal table config for nonexistent bucket
    When bucket journal table is configured for "ghost-bucket" with id "x" and format "JSON"
    Then the response status is 404
