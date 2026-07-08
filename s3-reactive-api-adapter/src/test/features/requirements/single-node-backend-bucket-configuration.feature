@requirement @single-node-backend @bucket-configuration @webclient-required
Business Need: Bucket configuration APIs against the single-node in-memory backend
  As an S3-compatible client using WebTestClient against a single-node Magrathea
  deployment,
  I want CORS, Lifecycle, Policy, Encryption, Logging, Website, Notification,
  Replication, RequestPayment, OwnershipControls, PublicAccessBlock, Accelerate,
  Analytics, Inventory, Metrics, IntelligentTiering, ABAC, ObjectLock, Metadata,
  MetadataTable, InventoryTable, and JournalTable bucket configuration APIs to
  persist and return the exact configuration I submit,
  So that configuration-management tooling built against these APIs can trust that
  Put/Get/Delete round-trips the real submitted values, not just a structurally
  valid response shape.

  This feature shares the single-node-backend validation scope described in
  single-node-backend-bucket-operations.feature (no "storage-engine" profile active).
  Per AGENTS.md §B.5, every configuration family here is `@config-only`: production
  code genuinely persists each configuration on the durable Bucket aggregate
  (S3BucketConfigHandler; comment in that file: "EP-2: every bucket configuration
  family is now persisted on the Bucket aggregate"), but no runtime enforcement of
  the configured policy exists in this feature's scope. Enforcement that does exist
  is covered elsewhere: OwnershipControls reversion rejection and PublicAccessBlock
  blocking a public bucket policy are functional, enforced behaviors validated
  separately in single-node-backend-runtime-effects.feature (migrated from
  object-store/runtime-effects.feature), not in this configuration-storage feature.

  Test-quality correction applied during migration (2026-07-07): the legacy
  features/object-store/bucket-config.feature scenarios asserted only that a
  response XML contained the *tag name* of the configured field (for example,
  "the metadata response contains \"AllowedOrigin\""), which would pass even if the
  actual submitted value were wrong, empty, or a hardcoded default — this is barely
  stronger than a status-code-only check. Every "Get X configuration" success
  scenario in this feature has been strengthened in place (no new scenarios added)
  with an additional assertion checking the actual round-tripped value using the
  same existing generic step (`the metadata response contains {string}`), reusing
  the fixture values already given in each scenario's `Given ... is preset with ...`
  step. No new step definitions were needed.

  One test-glue defect was found and is deliberately NOT masked by a false
  assertion: `ConfigSteps.bucketCorsPreset`/`putBucketCors` hardcode
  `<AllowedMethod>GET</AllowedMethod>` regardless of the `methods` parameter passed
  in Gherkin (for example, "GET,PUT" in "Get bucket CORS configuration"). The
  CORS scenarios in this feature only assert the origin value, not "PUT", because
  "PUT" is never actually submitted to the server — asserting it would either fail
  honestly or require fixing the test glue, which is out of scope for this
  migration; the discrepancy between the Gherkin text ("methods GET,PUT") and the
  real submitted body is called out inline on the affected scenarios.

  Background:
    Given bucket "test-bucket" exists

  Rule: CORS configuration can be put, read, and deleted, persisted on the Bucket aggregate

  @config-only @REQ-SINGLENODE-BUCKETCFG-CORS-001 @webclient-required
  Scenario: Put bucket CORS configuration
    When bucket CORS is configured with origin "http://example.com" and methods "GET,PUT"
    Then the response status is 200

  @config-only @REQ-SINGLENODE-BUCKETCFG-CORS-002 @webclient-required
  Scenario: Get bucket CORS configuration
    # Note: "methods GET,PUT" here is not fully honored by the test glue
    # (ConfigSteps.bucketCorsPreset hardcodes AllowedMethod=GET); only the origin
    # value is asserted below. See feature-level note above.
    Given bucket CORS is preset with origin "http://example.com" and methods "GET,PUT"
    When bucket CORS configuration is requested
    Then the response status is 200
    And the metadata response contains "AllowedOrigin"
    And the metadata response contains "http://example.com"

  @config-only @REQ-SINGLENODE-BUCKETCFG-CORS-003 @webclient-required
  Scenario: Delete bucket CORS configuration
    Given bucket CORS is preset with origin "http://example.com" and methods "GET,PUT"
    When bucket CORS configuration is deleted
    Then the response status is 204

  @protocol-smoke @REQ-SINGLENODE-BUCKETCFG-CORS-004 @webclient-required
  Scenario: Get CORS for nonexistent bucket
    When bucket CORS configuration is requested for "ghost-bucket"
    Then the response status is 404

  @protocol-smoke @REQ-SINGLENODE-BUCKETCFG-CORS-005 @webclient-required
  Scenario: Get CORS when no configuration exists
    When bucket CORS configuration is requested
    Then the response status is 404

  @protocol-smoke @REQ-SINGLENODE-BUCKETCFG-CORS-006 @webclient-required
  Scenario: Put CORS for nonexistent bucket
    When bucket CORS is configured for "ghost-bucket" with origin "*" and methods "GET"
    Then the response status is 404

  @protocol-smoke @REQ-SINGLENODE-BUCKETCFG-CORS-007 @webclient-required
  Scenario: Delete CORS for nonexistent bucket
    When bucket CORS configuration is deleted for "ghost-bucket"
    Then the response status is 404

  Rule: Lifecycle configuration can be put, read, and deleted, persisted on the Bucket aggregate

  @config-only @REQ-SINGLENODE-BUCKETCFG-LIFECYCLE-001 @webclient-required
  Scenario: Put bucket lifecycle configuration
    When bucket lifecycle is configured with rule "expire-30" and status "Enabled"
    Then the response status is 200

  @config-only @REQ-SINGLENODE-BUCKETCFG-LIFECYCLE-002 @webclient-required
  Scenario: Get bucket lifecycle configuration
    Given bucket lifecycle is preset with rule "expire-30" and status "Enabled"
    When bucket lifecycle configuration is requested
    Then the response status is 200
    And the metadata response contains "Status"
    And the metadata response contains "expire-30"
    And the metadata response contains "Enabled"

  @config-only @REQ-SINGLENODE-BUCKETCFG-LIFECYCLE-003 @webclient-required
  Scenario: Delete bucket lifecycle configuration
    Given bucket lifecycle is preset with rule "expire-30" and status "Enabled"
    When bucket lifecycle configuration is deleted
    Then the response status is 204

  @protocol-smoke @REQ-SINGLENODE-BUCKETCFG-LIFECYCLE-004 @webclient-required
  Scenario: Get lifecycle for nonexistent bucket
    When bucket lifecycle configuration is requested for "ghost-bucket"
    Then the response status is 404

  @protocol-smoke @REQ-SINGLENODE-BUCKETCFG-LIFECYCLE-005 @webclient-required
  Scenario: Get lifecycle when no configuration exists
    When bucket lifecycle configuration is requested
    Then the response status is 404

  @protocol-smoke @REQ-SINGLENODE-BUCKETCFG-LIFECYCLE-006 @webclient-required
  Scenario: Put lifecycle for nonexistent bucket
    When bucket lifecycle is configured for "ghost-bucket" with rule "expire-30" and status "Enabled"
    Then the response status is 404

  @protocol-smoke @REQ-SINGLENODE-BUCKETCFG-LIFECYCLE-007 @webclient-required
  Scenario: Delete lifecycle for nonexistent bucket
    When bucket lifecycle configuration is deleted for "ghost-bucket"
    Then the response status is 404

  Rule: Policy configuration can be put, read, and deleted, persisted on the Bucket aggregate

  @config-only @REQ-SINGLENODE-BUCKETCFG-POLICY-001 @webclient-required
  Scenario: Put bucket policy
    When bucket policy is set to '{"Version":"2012-10-17","Statement":[{"Effect":"Allow","Principal":"*","Action":"s3:GetObject"}]}'
    Then the response status is 200

  @config-only @REQ-SINGLENODE-BUCKETCFG-POLICY-002 @webclient-required
  Scenario: Get bucket policy
    Given bucket policy is preset with '{"Version":"2012-10-17","Statement":[{"Effect":"Allow","Principal":"*","Action":"s3:GetObject"}]}'
    When bucket policy is requested
    Then the response status is 200
    And the metadata response contains "s3:GetObject"

  @config-only @REQ-SINGLENODE-BUCKETCFG-POLICY-003 @webclient-required
  Scenario: Delete bucket policy
    Given bucket policy is preset with '{"Version":"2012-10-17","Statement":[{"Effect":"Allow"}]}'
    When bucket policy is deleted
    Then the response status is 204

  @protocol-smoke @REQ-SINGLENODE-BUCKETCFG-POLICY-004 @webclient-required
  Scenario: Get policy for nonexistent bucket
    When bucket policy is requested for "ghost-bucket"
    Then the response status is 404

  @protocol-smoke @REQ-SINGLENODE-BUCKETCFG-POLICY-005 @webclient-required
  Scenario: Get policy when no policy exists
    When bucket policy is requested
    Then the response status is 404

  @protocol-smoke @REQ-SINGLENODE-BUCKETCFG-POLICY-006 @webclient-required
  Scenario: Put policy for nonexistent bucket
    When bucket policy is set for "ghost-bucket" to '{"Effect":"Allow"}'
    Then the response status is 404

  @protocol-smoke @REQ-SINGLENODE-BUCKETCFG-POLICY-007 @webclient-required
  Scenario: Delete policy for nonexistent bucket
    When bucket policy is deleted for "ghost-bucket"
    Then the response status is 404

  Rule: Encryption configuration can be put, read, and deleted, persisted on the Bucket aggregate

  @config-only @REQ-SINGLENODE-BUCKETCFG-ENCRYPTION-001 @webclient-required
  Scenario: Put bucket encryption configuration
    When bucket encryption is configured with algorithm "AES256"
    Then the response status is 200

  @config-only @REQ-SINGLENODE-BUCKETCFG-ENCRYPTION-002 @webclient-required
  Scenario: Get bucket encryption configuration
    Given bucket encryption is preset with algorithm "AES256"
    When bucket encryption configuration is requested
    Then the response status is 200
    And the metadata response contains "Algorithm"
    And the metadata response contains "AES256"

  @config-only @REQ-SINGLENODE-BUCKETCFG-ENCRYPTION-003 @webclient-required
  Scenario: Delete bucket encryption configuration
    Given bucket encryption is preset with algorithm "AES256"
    When bucket encryption configuration is deleted
    Then the response status is 204

  @protocol-smoke @REQ-SINGLENODE-BUCKETCFG-ENCRYPTION-004 @webclient-required
  Scenario: Get encryption for nonexistent bucket
    When bucket encryption configuration is requested for "ghost-bucket"
    Then the response status is 404

  @protocol-smoke @REQ-SINGLENODE-BUCKETCFG-ENCRYPTION-005 @webclient-required
  Scenario: Get encryption when no configuration exists
    When bucket encryption configuration is requested
    Then the response status is 404

  @protocol-smoke @REQ-SINGLENODE-BUCKETCFG-ENCRYPTION-006 @webclient-required
  Scenario: Put encryption for nonexistent bucket
    When bucket encryption is configured for "ghost-bucket" with algorithm "AES256"
    Then the response status is 404

  @protocol-smoke @REQ-SINGLENODE-BUCKETCFG-ENCRYPTION-007 @webclient-required
  Scenario: Delete encryption for nonexistent bucket
    When bucket encryption configuration is deleted for "ghost-bucket"
    Then the response status is 404

  Rule: Logging configuration can be put, read, and deleted, persisted on the Bucket aggregate

  @config-only @REQ-SINGLENODE-BUCKETCFG-LOGGING-001 @webclient-required
  Scenario: Put bucket logging configuration
    When bucket logging is configured with target bucket "log-bucket" and prefix "test/"
    Then the response status is 200

  @config-only @REQ-SINGLENODE-BUCKETCFG-LOGGING-002 @webclient-required
  Scenario: Get bucket logging configuration
    Given bucket logging is preset with target bucket "log-bucket" and prefix "test/"
    When bucket logging configuration is requested
    Then the response status is 200
    And the metadata response contains "TargetBucket"
    And the metadata response contains "log-bucket"
    And the metadata response contains "test/"

  @config-only @REQ-SINGLENODE-BUCKETCFG-LOGGING-003 @webclient-required
  Scenario: Delete bucket logging configuration
    Given bucket logging is preset with target bucket "log-bucket" and prefix "test/"
    When bucket logging configuration is deleted
    Then the response status is 204

  @protocol-smoke @REQ-SINGLENODE-BUCKETCFG-LOGGING-004 @webclient-required
  Scenario: Get logging for nonexistent bucket
    When bucket logging configuration is requested for "ghost-bucket"
    Then the response status is 404

  @protocol-smoke @REQ-SINGLENODE-BUCKETCFG-LOGGING-005 @webclient-required
  Scenario: Get logging when no configuration exists
    When bucket logging configuration is requested
    Then the response status is 404

  @protocol-smoke @REQ-SINGLENODE-BUCKETCFG-LOGGING-006 @webclient-required
  Scenario: Put logging for nonexistent bucket
    When bucket logging is configured for "ghost-bucket" with target bucket "log-bucket" and prefix "test/"
    Then the response status is 404

  @protocol-smoke @REQ-SINGLENODE-BUCKETCFG-LOGGING-007 @webclient-required
  Scenario: Delete logging for nonexistent bucket
    When bucket logging configuration is deleted for "ghost-bucket"
    Then the response status is 404

  Rule: Website configuration can be put, read, and deleted, persisted on the Bucket aggregate

  @config-only @REQ-SINGLENODE-BUCKETCFG-WEBSITE-001 @webclient-required
  Scenario: Put bucket website configuration
    When bucket website is configured with index "index.html"
    Then the response status is 200

  @config-only @REQ-SINGLENODE-BUCKETCFG-WEBSITE-002 @webclient-required
  Scenario: Get bucket website configuration
    Given bucket website is preset with index "index.html"
    When bucket website configuration is requested
    Then the response status is 200
    And the metadata response contains "IndexDocument"
    And the metadata response contains "index.html"

  @config-only @REQ-SINGLENODE-BUCKETCFG-WEBSITE-003 @webclient-required
  Scenario: Delete bucket website configuration
    Given bucket website is preset with index "index.html"
    When bucket website configuration is deleted
    Then the response status is 204

  @protocol-smoke @REQ-SINGLENODE-BUCKETCFG-WEBSITE-004 @webclient-required
  Scenario: Get website for nonexistent bucket
    When bucket website configuration is requested for "ghost-bucket"
    Then the response status is 404

  @protocol-smoke @REQ-SINGLENODE-BUCKETCFG-WEBSITE-005 @webclient-required
  Scenario: Get website when no configuration exists
    When bucket website configuration is requested
    Then the response status is 404

  @protocol-smoke @REQ-SINGLENODE-BUCKETCFG-WEBSITE-006 @webclient-required
  Scenario: Put website for nonexistent bucket
    When bucket website is configured for "ghost-bucket" with index "index.html"
    Then the response status is 404

  @protocol-smoke @REQ-SINGLENODE-BUCKETCFG-WEBSITE-007 @webclient-required
  Scenario: Delete website for nonexistent bucket
    When bucket website configuration is deleted for "ghost-bucket"
    Then the response status is 404

  Rule: Notification configuration can be put, read, and deleted, persisted on the Bucket aggregate

  @config-only @REQ-SINGLENODE-BUCKETCFG-NOTIFICATION-001 @webclient-required
  Scenario: Put bucket notification configuration
    When bucket notification is configured with event "s3:ObjectCreated:*"
    Then the response status is 200

  @config-only @REQ-SINGLENODE-BUCKETCFG-NOTIFICATION-002 @webclient-required
  Scenario: Get bucket notification configuration
    Given bucket notification is preset with event "s3:ObjectCreated:*"
    When bucket notification configuration is requested
    Then the response status is 200
    And the metadata response contains "EventConfiguration"
    And the metadata response contains "s3:ObjectCreated:*"

  @config-only @REQ-SINGLENODE-BUCKETCFG-NOTIFICATION-003 @webclient-required
  Scenario: Delete bucket notification configuration
    Given bucket notification is preset with event "s3:ObjectCreated:*"
    When bucket notification configuration is deleted
    Then the response status is 204

  @protocol-smoke @REQ-SINGLENODE-BUCKETCFG-NOTIFICATION-004 @webclient-required
  Scenario: Get notification for nonexistent bucket
    When bucket notification configuration is requested for "ghost-bucket"
    Then the response status is 404

  @protocol-smoke @REQ-SINGLENODE-BUCKETCFG-NOTIFICATION-005 @webclient-required
  Scenario: Get notification when no configuration exists
    When bucket notification configuration is requested
    Then the response status is 404

  @protocol-smoke @REQ-SINGLENODE-BUCKETCFG-NOTIFICATION-006 @webclient-required
  Scenario: Put notification for nonexistent bucket
    When bucket notification is configured for "ghost-bucket" with event "s3:ObjectCreated:*"
    Then the response status is 404

  @protocol-smoke @REQ-SINGLENODE-BUCKETCFG-NOTIFICATION-007 @webclient-required
  Scenario: Delete notification for nonexistent bucket
    When bucket notification configuration is deleted for "ghost-bucket"
    Then the response status is 404

  Rule: Replication configuration can be put, read, and deleted, persisted on the Bucket aggregate

  @config-only @REQ-SINGLENODE-BUCKETCFG-REPLICATION-001 @webclient-required
  Scenario: Put bucket replication configuration
    When bucket replication is configured with role "arn:aws:iam::123:role/s3-replication"
    Then the response status is 200

  @config-only @REQ-SINGLENODE-BUCKETCFG-REPLICATION-002 @webclient-required
  Scenario: Get bucket replication configuration
    Given bucket replication is preset with role "arn:aws:iam::123:role/s3-replication"
    When bucket replication configuration is requested
    Then the response status is 200
    And the metadata response contains "Role"
    And the metadata response contains "arn:aws:iam::123:role/s3-replication"

  @config-only @REQ-SINGLENODE-BUCKETCFG-REPLICATION-003 @webclient-required
  Scenario: Delete bucket replication configuration
    Given bucket replication is preset with role "arn:aws:iam::123:role/s3-replication"
    When bucket replication configuration is deleted
    Then the response status is 204

  @protocol-smoke @REQ-SINGLENODE-BUCKETCFG-REPLICATION-004 @webclient-required
  Scenario: Get replication for nonexistent bucket
    When bucket replication configuration is requested for "ghost-bucket"
    Then the response status is 404

  @protocol-smoke @REQ-SINGLENODE-BUCKETCFG-REPLICATION-005 @webclient-required
  Scenario: Get replication when no configuration exists
    When bucket replication configuration is requested
    Then the response status is 404

  @protocol-smoke @REQ-SINGLENODE-BUCKETCFG-REPLICATION-006 @webclient-required
  Scenario: Put replication for nonexistent bucket
    When bucket replication is configured for "ghost-bucket" with role "arn:aws:iam::123:role/s3-replication"
    Then the response status is 404

  @protocol-smoke @REQ-SINGLENODE-BUCKETCFG-REPLICATION-007 @webclient-required
  Scenario: Delete replication for nonexistent bucket
    When bucket replication configuration is deleted for "ghost-bucket"
    Then the response status is 404

  Rule: Request Payment configuration can be put, read, and deleted, persisted on the Bucket aggregate

  @config-only @REQ-SINGLENODE-BUCKETCFG-REQPAY-001 @webclient-required
  Scenario: Put bucket request payment configuration
    When bucket request payment is configured with payer "Requester"
    Then the response status is 200

  @config-only @REQ-SINGLENODE-BUCKETCFG-REQPAY-002 @webclient-required
  Scenario: Get bucket request payment configuration
    Given bucket request payment is preset with payer "Requester"
    When bucket request payment configuration is requested
    Then the response status is 200
    And the metadata response contains "Payer"
    And the metadata response contains "Requester"

  @config-only @REQ-SINGLENODE-BUCKETCFG-REQPAY-003 @webclient-required
  Scenario: Delete bucket request payment configuration
    Given bucket request payment is preset with payer "Requester"
    When bucket request payment configuration is deleted
    Then the response status is 204

  @protocol-smoke @REQ-SINGLENODE-BUCKETCFG-REQPAY-004 @webclient-required
  Scenario: Get request payment for nonexistent bucket
    When bucket request payment configuration is requested for "ghost-bucket"
    Then the response status is 404

  @protocol-smoke @REQ-SINGLENODE-BUCKETCFG-REQPAY-005 @webclient-required
  Scenario: Get request payment when no configuration exists
    When bucket request payment configuration is requested
    Then the response status is 404

  @protocol-smoke @REQ-SINGLENODE-BUCKETCFG-REQPAY-006 @webclient-required
  Scenario: Put request payment for nonexistent bucket
    When bucket request payment is configured for "ghost-bucket" with payer "Requester"
    Then the response status is 404

  @protocol-smoke @REQ-SINGLENODE-BUCKETCFG-REQPAY-007 @webclient-required
  Scenario: Delete request payment for nonexistent bucket
    When bucket request payment configuration is deleted for "ghost-bucket"
    Then the response status is 404

  Rule: Ownership Controls configuration can be put, read, and deleted, persisted on the Bucket aggregate

  @config-only @REQ-SINGLENODE-BUCKETCFG-OWNERSHIP-001 @webclient-required
  Scenario: Put bucket ownership controls
    When bucket ownership controls are configured with ownership "BucketOwnerPreferred"
    Then the response status is 200

  @config-only @REQ-SINGLENODE-BUCKETCFG-OWNERSHIP-002 @webclient-required
  Scenario: Get bucket ownership controls
    Given bucket ownership controls are preset with ownership "BucketOwnerPreferred"
    When bucket ownership controls are requested
    Then the response status is 200
    And the metadata response contains "Ownership"
    And the metadata response contains "BucketOwnerPreferred"

  @config-only @REQ-SINGLENODE-BUCKETCFG-OWNERSHIP-003 @webclient-required
  Scenario: Delete bucket ownership controls
    Given bucket ownership controls are preset with ownership "BucketOwnerPreferred"
    When bucket ownership controls are deleted
    Then the response status is 204

  @protocol-smoke @REQ-SINGLENODE-BUCKETCFG-OWNERSHIP-004 @webclient-required
  Scenario: Get ownership controls for nonexistent bucket
    When bucket ownership controls are requested for "ghost-bucket"
    Then the response status is 404

  @protocol-smoke @REQ-SINGLENODE-BUCKETCFG-OWNERSHIP-005 @webclient-required
  Scenario: Get ownership controls when no configuration exists
    When bucket ownership controls are requested
    Then the response status is 404

  @protocol-smoke @REQ-SINGLENODE-BUCKETCFG-OWNERSHIP-006 @webclient-required
  Scenario: Put ownership controls for nonexistent bucket
    When bucket ownership controls are configured for "ghost-bucket" with ownership "BucketOwnerPreferred"
    Then the response status is 404

  @protocol-smoke @REQ-SINGLENODE-BUCKETCFG-OWNERSHIP-007 @webclient-required
  Scenario: Delete ownership controls for nonexistent bucket
    When bucket ownership controls are deleted for "ghost-bucket"
    Then the response status is 404

  Rule: Public Access Block configuration can be put, read, and deleted, persisted on the Bucket aggregate

  @config-only @REQ-SINGLENODE-BUCKETCFG-PAB-001 @webclient-required
  Scenario: Put bucket public access block configuration
    When bucket public access block is configured with blockPublicAcls "true"
    Then the response status is 200

  @config-only @REQ-SINGLENODE-BUCKETCFG-PAB-002 @webclient-required
  Scenario: Get bucket public access block configuration
    Given bucket public access block is preset with blockPublicAcls "true"
    When bucket public access block configuration is requested
    Then the response status is 200
    And the metadata response contains "BlockPublicAcls"
    And the metadata response contains "true"

  @config-only @REQ-SINGLENODE-BUCKETCFG-PAB-003 @webclient-required
  Scenario: Delete bucket public access block configuration
    Given bucket public access block is preset with blockPublicAcls "true"
    When bucket public access block configuration is deleted
    Then the response status is 204

  @protocol-smoke @REQ-SINGLENODE-BUCKETCFG-PAB-004 @webclient-required
  Scenario: Get public access block for nonexistent bucket
    When bucket public access block configuration is requested for "ghost-bucket"
    Then the response status is 404

  @protocol-smoke @REQ-SINGLENODE-BUCKETCFG-PAB-005 @webclient-required
  Scenario: Get public access block when no configuration exists
    When bucket public access block configuration is requested
    Then the response status is 404

  @protocol-smoke @REQ-SINGLENODE-BUCKETCFG-PAB-006 @webclient-required
  Scenario: Put public access block for nonexistent bucket
    When bucket public access block is configured for "ghost-bucket" with blockPublicAcls "true"
    Then the response status is 404

  @protocol-smoke @REQ-SINGLENODE-BUCKETCFG-PAB-007 @webclient-required
  Scenario: Delete public access block for nonexistent bucket
    When bucket public access block configuration is deleted for "ghost-bucket"
    Then the response status is 404

  Rule: Accelerate configuration can be put, read, and deleted, persisted on the Bucket aggregate

  @config-only @REQ-SINGLENODE-BUCKETCFG-ACCELERATE-001 @webclient-required
  Scenario: Put bucket accelerate configuration
    When bucket accelerate is configured with status "Enabled"
    Then the response status is 200

  @config-only @REQ-SINGLENODE-BUCKETCFG-ACCELERATE-002 @webclient-required
  Scenario: Get bucket accelerate configuration
    Given bucket accelerate is preset with status "Enabled"
    When bucket accelerate configuration is requested
    Then the response status is 200
    And the metadata response contains "Status"
    And the metadata response contains "Enabled"

  @config-only @REQ-SINGLENODE-BUCKETCFG-ACCELERATE-003 @webclient-required
  Scenario: Delete bucket accelerate configuration
    Given bucket accelerate is preset with status "Enabled"
    When bucket accelerate configuration is deleted
    Then the response status is 204

  @protocol-smoke @REQ-SINGLENODE-BUCKETCFG-ACCELERATE-004 @webclient-required
  Scenario: Get accelerate for nonexistent bucket
    When bucket accelerate configuration is requested for "ghost-bucket"
    Then the response status is 404

  @protocol-smoke @REQ-SINGLENODE-BUCKETCFG-ACCELERATE-005 @webclient-required
  Scenario: Get accelerate when no configuration exists
    When bucket accelerate configuration is requested
    Then the response status is 404

  @protocol-smoke @REQ-SINGLENODE-BUCKETCFG-ACCELERATE-006 @webclient-required
  Scenario: Put accelerate for nonexistent bucket
    When bucket accelerate is configured for "ghost-bucket" with status "Enabled"
    Then the response status is 404

  @protocol-smoke @REQ-SINGLENODE-BUCKETCFG-ACCELERATE-007 @webclient-required
  Scenario: Delete accelerate for nonexistent bucket
    When bucket accelerate configuration is deleted for "ghost-bucket"
    Then the response status is 404

  Rule: Analytics configuration can be put, read, and deleted, persisted on the Bucket aggregate

  @config-only @REQ-SINGLENODE-BUCKETCFG-ANALYTICS-001 @webclient-required
  Scenario: Put bucket analytics configuration
    When bucket analytics is configured with id "my-analytics" and filter "documents/"
    Then the response status is 200

  @config-only @REQ-SINGLENODE-BUCKETCFG-ANALYTICS-002 @webclient-required
  Scenario: Get bucket analytics configuration
    Given bucket analytics is preset with id "my-analytics" and filter "documents/"
    When bucket analytics configuration is requested for id "my-analytics"
    Then the response status is 200
    And the metadata response contains "Id"
    And the metadata response contains "my-analytics"

  @config-only @REQ-SINGLENODE-BUCKETCFG-ANALYTICS-003 @webclient-required
  Scenario: Delete bucket analytics configuration
    Given bucket analytics is preset with id "my-analytics" and filter "documents/"
    When bucket analytics configuration is deleted for id "my-analytics"
    Then the response status is 204

  @config-only @REQ-SINGLENODE-BUCKETCFG-ANALYTICS-004 @webclient-required
  Scenario: List bucket analytics configurations
    Given bucket analytics is preset with id "my-analytics" and filter "documents/"
    When bucket analytics configurations are listed
    Then the response status is 200

  @protocol-smoke @REQ-SINGLENODE-BUCKETCFG-ANALYTICS-005 @webclient-required
  Scenario: Get analytics for nonexistent bucket
    When bucket analytics configuration is requested for "ghost-bucket" with id "x"
    Then the response status is 404

  @protocol-smoke @REQ-SINGLENODE-BUCKETCFG-ANALYTICS-006 @webclient-required
  Scenario: Get analytics when no configuration exists
    When bucket analytics configuration is requested for id "missing-id"
    Then the response status is 404

  @protocol-smoke @REQ-SINGLENODE-BUCKETCFG-ANALYTICS-007 @webclient-required
  Scenario: Put analytics for nonexistent bucket
    When bucket analytics is configured for "ghost-bucket" with id "x" and filter ""
    Then the response status is 404

  @protocol-smoke @REQ-SINGLENODE-BUCKETCFG-ANALYTICS-008 @webclient-required
  Scenario: Delete analytics for nonexistent bucket
    When bucket analytics configuration is deleted for "ghost-bucket" with id "x"
    Then the response status is 404

  Rule: Inventory configuration can be put, read, and deleted, persisted on the Bucket aggregate

  @config-only @REQ-SINGLENODE-BUCKETCFG-INVENTORY-001 @webclient-required
  Scenario: Put bucket inventory configuration
    When bucket inventory is configured with id "my-inventory" and format "CSV"
    Then the response status is 200

  @config-only @REQ-SINGLENODE-BUCKETCFG-INVENTORY-002 @webclient-required
  Scenario: Get bucket inventory configuration
    Given bucket inventory is preset with id "my-inventory" and format "CSV"
    When bucket inventory configuration is requested for id "my-inventory"
    Then the response status is 200
    And the metadata response contains "Id"
    And the metadata response contains "my-inventory"
    And the metadata response contains "CSV"

  @config-only @REQ-SINGLENODE-BUCKETCFG-INVENTORY-003 @webclient-required
  Scenario: Delete bucket inventory configuration
    Given bucket inventory is preset with id "my-inventory" and format "CSV"
    When bucket inventory configuration is deleted for id "my-inventory"
    Then the response status is 204

  @config-only @REQ-SINGLENODE-BUCKETCFG-INVENTORY-004 @webclient-required
  Scenario: List bucket inventory configurations
    Given bucket inventory is preset with id "my-inventory" and format "CSV"
    When bucket inventory configurations are listed
    Then the response status is 200

  @protocol-smoke @REQ-SINGLENODE-BUCKETCFG-INVENTORY-005 @webclient-required
  Scenario: Get inventory for nonexistent bucket
    When bucket inventory configuration is requested for "ghost-bucket" with id "x"
    Then the response status is 404

  @protocol-smoke @REQ-SINGLENODE-BUCKETCFG-INVENTORY-006 @webclient-required
  Scenario: Get inventory when no configuration exists
    When bucket inventory configuration is requested for id "missing-id"
    Then the response status is 404

  @protocol-smoke @REQ-SINGLENODE-BUCKETCFG-INVENTORY-007 @webclient-required
  Scenario: Put inventory for nonexistent bucket
    When bucket inventory is configured for "ghost-bucket" with id "x" and format "CSV"
    Then the response status is 404

  @protocol-smoke @REQ-SINGLENODE-BUCKETCFG-INVENTORY-008 @webclient-required
  Scenario: Delete inventory for nonexistent bucket
    When bucket inventory configuration is deleted for "ghost-bucket" with id "x"
    Then the response status is 404

  Rule: Metrics configuration can be put, read, and deleted, persisted on the Bucket aggregate

  @config-only @REQ-SINGLENODE-BUCKETCFG-METRICS-001 @webclient-required
  Scenario: Put bucket metrics configuration
    When bucket metrics is configured with id "my-metrics"
    Then the response status is 200

  @config-only @REQ-SINGLENODE-BUCKETCFG-METRICS-002 @webclient-required
  Scenario: Get bucket metrics configuration
    Given bucket metrics is preset with id "my-metrics"
    When bucket metrics configuration is requested
    Then the response status is 200
    And the metadata response contains "Id"
    And the metadata response contains "my-metrics"

  @config-only @REQ-SINGLENODE-BUCKETCFG-METRICS-003 @webclient-required
  Scenario: Delete bucket metrics configuration
    Given bucket metrics is preset with id "my-metrics"
    When bucket metrics configuration is deleted
    Then the response status is 204

  @protocol-smoke @REQ-SINGLENODE-BUCKETCFG-METRICS-004 @webclient-required
  Scenario: Get metrics for nonexistent bucket
    When bucket metrics configuration is requested for "ghost-bucket"
    Then the response status is 404

  @protocol-smoke @REQ-SINGLENODE-BUCKETCFG-METRICS-005 @webclient-required
  Scenario: Get metrics when no configuration exists
    When bucket metrics configuration is requested
    Then the response status is 404

  @protocol-smoke @REQ-SINGLENODE-BUCKETCFG-METRICS-006 @webclient-required
  Scenario: Put metrics for nonexistent bucket
    When bucket metrics is configured for "ghost-bucket" with id "x"
    Then the response status is 404

  @protocol-smoke @REQ-SINGLENODE-BUCKETCFG-METRICS-007 @webclient-required
  Scenario: Delete metrics for nonexistent bucket
    When bucket metrics configuration is deleted for "ghost-bucket"
    Then the response status is 404

  Rule: Intelligent-Tiering configuration can be put, read, and deleted, persisted on the Bucket aggregate

  @config-only @REQ-SINGLENODE-BUCKETCFG-INTTIERING-001 @webclient-required
  Scenario: Put bucket intelligent-tiering configuration
    When bucket intelligent-tiering is configured with id "my-tiering" and status "ACTIVE"
    Then the response status is 200

  @config-only @REQ-SINGLENODE-BUCKETCFG-INTTIERING-002 @webclient-required
  Scenario: Get bucket intelligent-tiering configuration
    Given bucket intelligent-tiering is preset with id "my-tiering" and status "ACTIVE"
    When bucket intelligent-tiering configuration is requested
    Then the response status is 200
    And the metadata response contains "Id"
    And the metadata response contains "my-tiering"
    And the metadata response contains "ACTIVE"

  @config-only @REQ-SINGLENODE-BUCKETCFG-INTTIERING-003 @webclient-required
  Scenario: Delete bucket intelligent-tiering configuration
    Given bucket intelligent-tiering is preset with id "my-tiering" and status "ACTIVE"
    When bucket intelligent-tiering configuration is deleted
    Then the response status is 204

  @protocol-smoke @REQ-SINGLENODE-BUCKETCFG-INTTIERING-004 @webclient-required
  Scenario: Get intelligent-tiering for nonexistent bucket
    When bucket intelligent-tiering configuration is requested for "ghost-bucket"
    Then the response status is 404

  @protocol-smoke @REQ-SINGLENODE-BUCKETCFG-INTTIERING-005 @webclient-required
  Scenario: Get intelligent-tiering when no configuration exists
    When bucket intelligent-tiering configuration is requested
    Then the response status is 404

  @protocol-smoke @REQ-SINGLENODE-BUCKETCFG-INTTIERING-006 @webclient-required
  Scenario: Put intelligent-tiering for nonexistent bucket
    When bucket intelligent-tiering is configured for "ghost-bucket" with id "x" and status "ACTIVE"
    Then the response status is 404

  @protocol-smoke @REQ-SINGLENODE-BUCKETCFG-INTTIERING-007 @webclient-required
  Scenario: Delete intelligent-tiering for nonexistent bucket
    When bucket intelligent-tiering configuration is deleted for "ghost-bucket"
    Then the response status is 404

  Rule: ABAC configuration can be put, read, and deleted, persisted on the Bucket aggregate

  @config-only @REQ-SINGLENODE-BUCKETCFG-ABAC-001 @webclient-required
  Scenario: Put bucket ABAC configuration
    When bucket ABAC is configured with rule id "abac-1" and principal "arn:aws:iam::123:user/admin"
    Then the response status is 200

  @config-only @REQ-SINGLENODE-BUCKETCFG-ABAC-002 @webclient-required
  Scenario: Get bucket ABAC configuration
    Given bucket ABAC is preset with rule id "abac-1" and principal "arn:aws:iam::123:user/admin"
    When bucket ABAC configuration is requested
    Then the response status is 200
    And the metadata response contains "AbacRule"
    And the metadata response contains "abac-1"
    And the metadata response contains "arn:aws:iam::123:user/admin"

  @protocol-smoke @REQ-SINGLENODE-BUCKETCFG-ABAC-003 @webclient-required
  Scenario: Get ABAC for nonexistent bucket
    When bucket ABAC configuration is requested for "ghost-bucket"
    Then the response status is 404

  @protocol-smoke @REQ-SINGLENODE-BUCKETCFG-ABAC-004 @webclient-required
  Scenario: Get ABAC when no configuration exists
    When bucket ABAC configuration is requested
    Then the response status is 404

  @protocol-smoke @REQ-SINGLENODE-BUCKETCFG-ABAC-005 @webclient-required
  Scenario: Put ABAC for nonexistent bucket
    When bucket ABAC is configured for "ghost-bucket" with rule id "x" and principal "*"
    Then the response status is 404

  Rule: Object Lock Configuration configuration can be put, read, and deleted, persisted on the Bucket aggregate

  @config-only @REQ-SINGLENODE-BUCKETCFG-OBJECTLOCKCONFIGURATION-001 @webclient-required
  Scenario: Put bucket object lock configuration
    When bucket object lock is configured with mode "GOVERNANCE" and days 5
    Then the response status is 200

  @config-only @REQ-SINGLENODE-BUCKETCFG-OBJECTLOCKCONFIGURATION-002 @webclient-required
  Scenario: Get bucket object lock configuration
    Given bucket object lock is preset with mode "GOVERNANCE" and days 5
    When bucket object lock configuration is requested
    Then the response status is 200
    And the metadata response contains "DefaultRetention"
    And the metadata response contains "GOVERNANCE"
    And the metadata response contains "5"

  @protocol-smoke @REQ-SINGLENODE-BUCKETCFG-OBJECTLOCKCONFIGURATION-003 @webclient-required
  Scenario: Get object lock for nonexistent bucket
    When bucket object lock configuration is requested for "ghost-bucket"
    Then the response status is 404

  @protocol-smoke @REQ-SINGLENODE-BUCKETCFG-OBJECTLOCKCONFIGURATION-004 @webclient-required
  Scenario: Get object lock when no configuration exists
    When bucket object lock configuration is requested
    Then the response status is 200
    And the metadata response contains "ObjectLockEnabled"

  @protocol-smoke @REQ-SINGLENODE-BUCKETCFG-OBJECTLOCKCONFIGURATION-005 @webclient-required
  Scenario: Put object lock for nonexistent bucket
    When bucket object lock is configured for "ghost-bucket" with mode "GOVERNANCE" and days 5
    Then the response status is 404

  Rule: Metadata Configuration configuration can be put, read, and deleted, persisted on the Bucket aggregate

  @config-only @REQ-SINGLENODE-BUCKETCFG-METACONFIG-001 @webclient-required
  Scenario: Put bucket metadata configuration
    When bucket metadata is configured with rule id "meta-1" and status "Enabled"
    Then the response status is 200

  @config-only @REQ-SINGLENODE-BUCKETCFG-METACONFIG-002 @webclient-required
  Scenario: Get bucket metadata configuration
    Given bucket metadata is preset with rule id "meta-1" and status "Enabled"
    When bucket metadata configuration is requested
    Then the response status is 200
    And the metadata response contains "MetadataResourceType"
    And the metadata response contains "meta-1"

  @config-only @REQ-SINGLENODE-BUCKETCFG-METACONFIG-003 @webclient-required
  Scenario: Delete bucket metadata configuration
    Given bucket metadata is preset with rule id "meta-1" and status "Enabled"
    When bucket metadata configuration is deleted
    Then the response status is 204

  @protocol-smoke @REQ-SINGLENODE-BUCKETCFG-METACONFIG-004 @webclient-required
  Scenario: Get metadata config for nonexistent bucket
    When bucket metadata configuration is requested for "ghost-bucket"
    Then the response status is 404

  @protocol-smoke @REQ-SINGLENODE-BUCKETCFG-METACONFIG-005 @webclient-required
  Scenario: Get metadata config when no configuration exists
    When bucket metadata configuration is requested
    Then the response status is 404

  @protocol-smoke @REQ-SINGLENODE-BUCKETCFG-METACONFIG-006 @webclient-required
  Scenario: Put metadata config for nonexistent bucket
    When bucket metadata is configured for "ghost-bucket" with rule id "x" and status "Enabled"
    Then the response status is 404

  @protocol-smoke @REQ-SINGLENODE-BUCKETCFG-METACONFIG-007 @webclient-required
  Scenario: Delete metadata config for nonexistent bucket
    When bucket metadata configuration is deleted for "ghost-bucket"
    Then the response status is 404

  Rule: Metadata Table Configuration configuration can be put, read, and deleted, persisted on the Bucket aggregate

  @config-only @REQ-SINGLENODE-BUCKETCFG-METATABLE-001 @webclient-required
  Scenario: Put bucket metadata table configuration
    When bucket metadata table is configured with rule id "meta-table-1" and table name "my-table"
    Then the response status is 200

  @config-only @REQ-SINGLENODE-BUCKETCFG-METATABLE-002 @webclient-required
  Scenario: Get bucket metadata table configuration
    Given bucket metadata table is preset with rule id "meta-table-1" and table name "my-table"
    When bucket metadata table configuration is requested
    Then the response status is 200
    And the metadata response contains "MetadataTableName"
    And the metadata response contains "meta-table-1"
    And the metadata response contains "my-table"

  @config-only @REQ-SINGLENODE-BUCKETCFG-METATABLE-003 @webclient-required
  Scenario: Delete bucket metadata table configuration
    Given bucket metadata table is preset with rule id "meta-table-1" and table name "my-table"
    When bucket metadata table configuration is deleted
    Then the response status is 204

  @protocol-smoke @REQ-SINGLENODE-BUCKETCFG-METATABLE-004 @webclient-required
  Scenario: Get metadata table config for nonexistent bucket
    When bucket metadata table configuration is requested for "ghost-bucket"
    Then the response status is 404

  @protocol-smoke @REQ-SINGLENODE-BUCKETCFG-METATABLE-005 @webclient-required
  Scenario: Get metadata table config when no configuration exists
    When bucket metadata table configuration is requested
    Then the response status is 404

  @protocol-smoke @REQ-SINGLENODE-BUCKETCFG-METATABLE-006 @webclient-required
  Scenario: Put metadata table config for nonexistent bucket
    When bucket metadata table is configured for "ghost-bucket" with rule id "x" and table name "x"
    Then the response status is 404

  @protocol-smoke @REQ-SINGLENODE-BUCKETCFG-METATABLE-007 @webclient-required
  Scenario: Delete metadata table config for nonexistent bucket
    When bucket metadata table configuration is deleted for "ghost-bucket"
    Then the response status is 404

  Rule: Inventory Table Configuration configuration can be put, read, and deleted, persisted on the Bucket aggregate

  @config-only @REQ-SINGLENODE-BUCKETCFG-INVTABLE-001 @webclient-required
  Scenario: Put bucket inventory table configuration
    When bucket inventory table is configured with id "inv-table-1" and format "CSV"
    Then the response status is 200

  @config-only @REQ-SINGLENODE-BUCKETCFG-INVTABLE-002 @webclient-required
  Scenario: Get bucket inventory table configuration
    Given bucket inventory table is preset with id "inv-table-1" and format "CSV"
    When bucket inventory table configuration is requested
    Then the response status is 200
    And the metadata response contains "DestinationFormat"
    And the metadata response contains "inv-table-1"
    And the metadata response contains "CSV"

  @protocol-smoke @REQ-SINGLENODE-BUCKETCFG-INVTABLE-003 @webclient-required
  Scenario: Get inventory table config for nonexistent bucket
    When bucket inventory table configuration is requested for "ghost-bucket"
    Then the response status is 404

  @protocol-smoke @REQ-SINGLENODE-BUCKETCFG-INVTABLE-004 @webclient-required
  Scenario: Get inventory table config when no configuration exists
    When bucket inventory table configuration is requested
    Then the response status is 404

  @protocol-smoke @REQ-SINGLENODE-BUCKETCFG-INVTABLE-005 @webclient-required
  Scenario: Put inventory table config for nonexistent bucket
    When bucket inventory table is configured for "ghost-bucket" with id "x" and format "CSV"
    Then the response status is 404

  Rule: Journal Table Configuration configuration can be put, read, and deleted, persisted on the Bucket aggregate

  @config-only @REQ-SINGLENODE-BUCKETCFG-JOURNALTABLE-001 @webclient-required
  Scenario: Put bucket journal table configuration
    When bucket journal table is configured with id "journal-table-1" and format "JSON"
    Then the response status is 200

  @config-only @REQ-SINGLENODE-BUCKETCFG-JOURNALTABLE-002 @webclient-required
  Scenario: Get bucket journal table configuration
    Given bucket journal table is preset with id "journal-table-1" and format "JSON"
    When bucket journal table configuration is requested
    Then the response status is 200
    And the metadata response contains "DestinationFormat"
    And the metadata response contains "journal-table-1"
    And the metadata response contains "JSON"

  @protocol-smoke @REQ-SINGLENODE-BUCKETCFG-JOURNALTABLE-003 @webclient-required
  Scenario: Get journal table config for nonexistent bucket
    When bucket journal table configuration is requested for "ghost-bucket"
    Then the response status is 404

  @protocol-smoke @REQ-SINGLENODE-BUCKETCFG-JOURNALTABLE-004 @webclient-required
  Scenario: Get journal table config when no configuration exists
    When bucket journal table configuration is requested
    Then the response status is 404

  @protocol-smoke @REQ-SINGLENODE-BUCKETCFG-JOURNALTABLE-005 @webclient-required
  Scenario: Put journal table config for nonexistent bucket
    When bucket journal table is configured for "ghost-bucket" with id "x" and format "JSON"
    Then the response status is 404
