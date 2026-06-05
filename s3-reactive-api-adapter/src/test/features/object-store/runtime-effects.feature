Feature: S3 runtime bucket configuration effects

  Scenario: BucketOwnerEnforced ownership controls cannot be reverted
    Given bucket "ownership-runtime-bucket" exists
    When bucket ownership controls are set directly for "ownership-runtime-bucket" to "BucketOwnerEnforced"
    Then the response status is 200
    When bucket ownership controls are set directly for "ownership-runtime-bucket" to "ObjectWriter"
    Then the response status is 409

  Scenario: PublicAccessBlock blocks public bucket policies
    Given bucket "pab-policy-bucket" exists
    When bucket public access block is configured for "pab-policy-bucket" with blockPublicAcls "false", ignorePublicAcls "false", blockPublicPolicy "true", restrictPublicBuckets "false"
    Then the response status is 200
    When bucket policy is set for "pab-policy-bucket" to a public read policy
    Then the response status is 403
    And the response body contains "Public bucket policies are blocked"
