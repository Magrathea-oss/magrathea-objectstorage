@requirement @single-node-backend @bucket-configuration @enforcement @webclient-required
Business Need: Bucket configuration enforcement effects against the single-node in-memory backend
  As an S3-compatible client using WebTestClient against a single-node Magrathea
  deployment,
  I want BucketOwnerEnforced ownership controls to reject reversion to a weaker
  ownership mode, and PublicAccessBlock to actually reject a public bucket policy
  rather than silently accepting it,
  So that these two configuration families are known to be genuinely enforced at
  runtime, not merely stored, unlike the config-only families documented in
  single-node-backend-bucket-configuration.feature.

  This feature shares the single-node-backend validation scope described in
  single-node-backend-bucket-operations.feature (no "storage-engine" profile
  active). It was extracted from the legacy features/object-store/runtime-effects.feature.

  Both scenarios here are classified `Business Need` rather than `Ability`: they
  describe cross-cutting enforcement behavior observable through the external
  S3-compatible API (PutBucketOwnershipControls, PutBucketPolicy interacting with
  PutPublicAccessBlock), not an internal mechanism with no S3 API equivalent, so
  AGENTS.md §B.2/§B.3 place them in requirements/ rather than specs/.

  Rule: Ownership controls set to BucketOwnerEnforced must reject reversion to a weaker ownership mode

    @REQ-SINGLENODE-BUCKETCFG-OWNERSHIP-ENFORCE-001 @functional-requirement @webclient-required
    Scenario: BucketOwnerEnforced ownership controls cannot be reverted
      Given bucket "ownership-runtime-bucket" exists
      When bucket ownership controls are set directly for "ownership-runtime-bucket" to "BucketOwnerEnforced"
      Then the response status is 200
      When bucket ownership controls are set directly for "ownership-runtime-bucket" to "ObjectWriter"
      Then the response status is 409

  Rule: PublicAccessBlock with blockPublicPolicy enabled must reject a subsequent public bucket policy

    @REQ-SINGLENODE-BUCKETCFG-PAB-ENFORCE-001 @functional-requirement @webclient-required
    Scenario: PublicAccessBlock blocks public bucket policies
      Given bucket "pab-policy-bucket" exists
      When bucket public access block is configured for "pab-policy-bucket" with blockPublicAcls "false", ignorePublicAcls "false", blockPublicPolicy "true", restrictPublicBuckets "false"
      Then the response status is 200
      When bucket policy is set for "pab-policy-bucket" to a public read policy
      Then the response status is 403
      And the response body contains "Public bucket policies are blocked"
