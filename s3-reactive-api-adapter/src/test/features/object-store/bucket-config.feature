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

  # ── Analytics Success ──

  Scenario: Put bucket analytics configuration
    When bucket analytics is configured with id "my-analytics" and filter "documents/"
    Then the response status is 200

  Scenario: Get bucket analytics configuration
    Given bucket analytics is preset with id "my-analytics" and filter "documents/"
    When bucket analytics configuration is requested for id "my-analytics"
    Then the response status is 200
    And the metadata response contains "Id"

  Scenario: Delete bucket analytics configuration
    Given bucket analytics is preset with id "my-analytics" and filter "documents/"
    When bucket analytics configuration is deleted for id "my-analytics"
    Then the response status is 204

  Scenario: List bucket analytics configurations
    Given bucket analytics is preset with id "my-analytics" and filter "documents/"
    When bucket analytics configurations are listed
    Then the response status is 200

  # ── Analytics Failure ──

  Scenario: Get analytics for nonexistent bucket
    When bucket analytics configuration is requested for "ghost-bucket" with id "x"
    Then the response status is 404

  Scenario: Get analytics when no configuration exists
    When bucket analytics configuration is requested for id "missing-id"
    Then the response status is 404

  Scenario: Put analytics for nonexistent bucket
    When bucket analytics is configured for "ghost-bucket" with id "x" and filter ""
    Then the response status is 404

  Scenario: Delete analytics for nonexistent bucket
    When bucket analytics configuration is deleted for "ghost-bucket" with id "x"
    Then the response status is 404

  # ── Inventory Success ──

  Scenario: Put bucket inventory configuration
    When bucket inventory is configured with id "my-inventory" and format "CSV"
    Then the response status is 200

  Scenario: Get bucket inventory configuration
    Given bucket inventory is preset with id "my-inventory" and format "CSV"
    When bucket inventory configuration is requested for id "my-inventory"
    Then the response status is 200
    And the metadata response contains "Id"

  Scenario: Delete bucket inventory configuration
    Given bucket inventory is preset with id "my-inventory" and format "CSV"
    When bucket inventory configuration is deleted for id "my-inventory"
    Then the response status is 204

  Scenario: List bucket inventory configurations
    Given bucket inventory is preset with id "my-inventory" and format "CSV"
    When bucket inventory configurations are listed
    Then the response status is 200

  # ── Inventory Failure ──

  Scenario: Get inventory for nonexistent bucket
    When bucket inventory configuration is requested for "ghost-bucket" with id "x"
    Then the response status is 404

  Scenario: Get inventory when no configuration exists
    When bucket inventory configuration is requested for id "missing-id"
    Then the response status is 404

  Scenario: Put inventory for nonexistent bucket
    When bucket inventory is configured for "ghost-bucket" with id "x" and format "CSV"
    Then the response status is 404

  Scenario: Delete inventory for nonexistent bucket
    When bucket inventory configuration is deleted for "ghost-bucket" with id "x"
    Then the response status is 404

  # ── Metrics Success ──

  Scenario: Put bucket metrics configuration
    When bucket metrics is configured with id "my-metrics"
    Then the response status is 200

  Scenario: Get bucket metrics configuration
    Given bucket metrics is preset with id "my-metrics"
    When bucket metrics configuration is requested
    Then the response status is 200
    And the metadata response contains "Id"

  Scenario: Delete bucket metrics configuration
    Given bucket metrics is preset with id "my-metrics"
    When bucket metrics configuration is deleted
    Then the response status is 204

  # ── Metrics Failure ──

  Scenario: Get metrics for nonexistent bucket
    When bucket metrics configuration is requested for "ghost-bucket"
    Then the response status is 404

  Scenario: Get metrics when no configuration exists
    When bucket metrics configuration is requested
    Then the response status is 404

  Scenario: Put metrics for nonexistent bucket
    When bucket metrics is configured for "ghost-bucket" with id "x"
    Then the response status is 404

  Scenario: Delete metrics for nonexistent bucket
    When bucket metrics configuration is deleted for "ghost-bucket"
    Then the response status is 404

  # ── Intelligent-Tiering Success ──

  Scenario: Put bucket intelligent-tiering configuration
    When bucket intelligent-tiering is configured with id "my-tiering" and status "ACTIVE"
    Then the response status is 200

  Scenario: Get bucket intelligent-tiering configuration
    Given bucket intelligent-tiering is preset with id "my-tiering" and status "ACTIVE"
    When bucket intelligent-tiering configuration is requested
    Then the response status is 200
    And the metadata response contains "Id"

  Scenario: Delete bucket intelligent-tiering configuration
    Given bucket intelligent-tiering is preset with id "my-tiering" and status "ACTIVE"
    When bucket intelligent-tiering configuration is deleted
    Then the response status is 204

  # ── Intelligent-Tiering Failure ──

  Scenario: Get intelligent-tiering for nonexistent bucket
    When bucket intelligent-tiering configuration is requested for "ghost-bucket"
    Then the response status is 404

  Scenario: Get intelligent-tiering when no configuration exists
    When bucket intelligent-tiering configuration is requested
    Then the response status is 404

  Scenario: Put intelligent-tiering for nonexistent bucket
    When bucket intelligent-tiering is configured for "ghost-bucket" with id "x" and status "ACTIVE"
    Then the response status is 404

  Scenario: Delete intelligent-tiering for nonexistent bucket
    When bucket intelligent-tiering configuration is deleted for "ghost-bucket"
    Then the response status is 404

  # ── ABAC Success (Batch 2) ──

  Scenario: Put bucket ABAC configuration
    When bucket ABAC is configured with rule id "abac-1" and principal "arn:aws:iam::123:user/admin"
    Then the response status is 200

  Scenario: Get bucket ABAC configuration
    Given bucket ABAC is preset with rule id "abac-1" and principal "arn:aws:iam::123:user/admin"
    When bucket ABAC configuration is requested
    Then the response status is 200
    And the metadata response contains "AbacRule"

  # ── ABAC Failure ──

  Scenario: Get ABAC for nonexistent bucket
    When bucket ABAC configuration is requested for "ghost-bucket"
    Then the response status is 404

  Scenario: Get ABAC when no configuration exists
    When bucket ABAC configuration is requested
    Then the response status is 404

  Scenario: Put ABAC for nonexistent bucket
    When bucket ABAC is configured for "ghost-bucket" with rule id "x" and principal "*"
    Then the response status is 404

  # ── Object Lock Configuration Success (Batch 5) ──

  Scenario: Put bucket object lock configuration
    When bucket object lock is configured with mode "GOVERNANCE" and days 5
    Then the response status is 200

  Scenario: Get bucket object lock configuration
    Given bucket object lock is preset with mode "GOVERNANCE" and days 5
    When bucket object lock configuration is requested
    Then the response status is 200
    And the metadata response contains "DefaultRetention"

  # ── Object Lock Failure ──

  Scenario: Get object lock for nonexistent bucket
    When bucket object lock configuration is requested for "ghost-bucket"
    Then the response status is 404

  Scenario: Get object lock when no configuration exists
    When bucket object lock configuration is requested
    Then the response status is 404

  Scenario: Put object lock for nonexistent bucket
    When bucket object lock is configured for "ghost-bucket" with mode "GOVERNANCE" and days 5
    Then the response status is 404

  # ── Metadata Configuration Success (Batch 2) ──

  Scenario: Put bucket metadata configuration
    When bucket metadata is configured with rule id "meta-1" and status "Enabled"
    Then the response status is 200

  Scenario: Get bucket metadata configuration
    Given bucket metadata is preset with rule id "meta-1" and status "Enabled"
    When bucket metadata configuration is requested
    Then the response status is 200
    And the metadata response contains "MetadataResourceType"

  Scenario: Delete bucket metadata configuration
    Given bucket metadata is preset with rule id "meta-1" and status "Enabled"
    When bucket metadata configuration is deleted
    Then the response status is 204

  # ── Metadata Configuration Failure ──

  Scenario: Get metadata config for nonexistent bucket
    When bucket metadata configuration is requested for "ghost-bucket"
    Then the response status is 404

  Scenario: Get metadata config when no configuration exists
    When bucket metadata configuration is requested
    Then the response status is 404

  Scenario: Put metadata config for nonexistent bucket
    When bucket metadata is configured for "ghost-bucket" with rule id "x" and status "Enabled"
    Then the response status is 404

  Scenario: Delete metadata config for nonexistent bucket
    When bucket metadata configuration is deleted for "ghost-bucket"
    Then the response status is 404

  # ── Metadata Table Configuration Success (Batch 2) ──

  Scenario: Put bucket metadata table configuration
    When bucket metadata table is configured with rule id "meta-table-1" and table name "my-table"
    Then the response status is 200

  Scenario: Get bucket metadata table configuration
    Given bucket metadata table is preset with rule id "meta-table-1" and table name "my-table"
    When bucket metadata table configuration is requested
    Then the response status is 200
    And the metadata response contains "MetadataTableName"

  Scenario: Delete bucket metadata table configuration
    Given bucket metadata table is preset with rule id "meta-table-1" and table name "my-table"
    When bucket metadata table configuration is deleted
    Then the response status is 204

  # ── Metadata Table Configuration Failure ──

  Scenario: Get metadata table config for nonexistent bucket
    When bucket metadata table configuration is requested for "ghost-bucket"
    Then the response status is 404

  Scenario: Get metadata table config when no configuration exists
    When bucket metadata table configuration is requested
    Then the response status is 404

  Scenario: Put metadata table config for nonexistent bucket
    When bucket metadata table is configured for "ghost-bucket" with rule id "x" and table name "x"
    Then the response status is 404

  Scenario: Delete metadata table config for nonexistent bucket
    When bucket metadata table configuration is deleted for "ghost-bucket"
    Then the response status is 404

  # ── Inventory Table Configuration Success (Batch 2) ──

  Scenario: Put bucket inventory table configuration
    When bucket inventory table is configured with id "inv-table-1" and format "CSV"
    Then the response status is 200

  Scenario: Get bucket inventory table configuration
    Given bucket inventory table is preset with id "inv-table-1" and format "CSV"
    When bucket inventory table configuration is requested
    Then the response status is 200
    And the metadata response contains "DestinationFormat"

  # ── Inventory Table Configuration Failure ──

  Scenario: Get inventory table config for nonexistent bucket
    When bucket inventory table configuration is requested for "ghost-bucket"
    Then the response status is 404

  Scenario: Get inventory table config when no configuration exists
    When bucket inventory table configuration is requested
    Then the response status is 404

  Scenario: Put inventory table config for nonexistent bucket
    When bucket inventory table is configured for "ghost-bucket" with id "x" and format "CSV"
    Then the response status is 404

  # ── Journal Table Configuration Success (Batch 2) ──

  Scenario: Put bucket journal table configuration
    When bucket journal table is configured with id "journal-table-1" and format "JSON"
    Then the response status is 200

  Scenario: Get bucket journal table configuration
    Given bucket journal table is preset with id "journal-table-1" and format "JSON"
    When bucket journal table configuration is requested
    Then the response status is 200
    And the metadata response contains "DestinationFormat"

  # ── Journal Table Configuration Failure ──

  Scenario: Get journal table config for nonexistent bucket
    When bucket journal table configuration is requested for "ghost-bucket"
    Then the response status is 404

  Scenario: Get journal table config when no configuration exists
    When bucket journal table configuration is requested
    Then the response status is 404

  Scenario: Put journal table config for nonexistent bucket
    When bucket journal table is configured for "ghost-bucket" with id "x" and format "JSON"
    Then the response status is 404
