Feature: S3 runtime bucket configuration effects

  Scenario: Allowed CORS origin receives runtime CORS response headers
    Given bucket "cors-allowed-bucket" exists
    When bucket CORS is configured for "cors-allowed-bucket" with origin "https://app.example" and methods "GET"
    Then the response status is 200
    Given object "index.txt" exists in bucket "cors-allowed-bucket" with content "hello"
    When object "index.txt" in bucket "cors-allowed-bucket" is retrieved with Origin "https://app.example"
    Then the response status is 200
    And the response header "Access-Control-Allow-Origin" is "https://app.example"
    And the response header "Access-Control-Allow-Methods" is "GET"

  Scenario: Disallowed CORS origin is rejected before object retrieval
    Given bucket "cors-denied-bucket" exists
    When bucket CORS is configured for "cors-denied-bucket" with origin "https://app.example" and methods "GET"
    Then the response status is 200
    Given object "index.txt" exists in bucket "cors-denied-bucket" with content "hello"
    When object "index.txt" in bucket "cors-denied-bucket" is retrieved with Origin "https://evil.example"
    Then the response status is 403
    And the response body contains "Origin not allowed"

  Scenario: Origin header is rejected when CORS is not configured
    Given bucket "cors-missing-bucket" exists
    Given object "index.txt" exists in bucket "cors-missing-bucket" with content "hello"
    When object "index.txt" in bucket "cors-missing-bucket" is retrieved with Origin "https://app.example"
    Then the response status is 403
    And the response body contains "CORS not configured"

  Scenario: Website index document redirects bucket root
    Given bucket "website-index-bucket" exists
    When bucket website is configured for "website-index-bucket" with index "index.html"
    Then the response status is 200
    When bucket root "website-index-bucket" is requested
    Then the response status is 302
    And the response Location header is "/website-index-bucket/index.html"

  Scenario: Website error document is served for missing keys
    Given bucket "website-error-bucket" exists
    When bucket website is configured for "website-error-bucket" with index "index.html" and error document "error.html"
    Then the response status is 200
    When missing object "missing.html" in bucket "website-error-bucket" is requested
    Then the response status is 404
    And the response body contains "error.html"

  Scenario: BucketOwnerEnforced ownership controls cannot be reverted
    Given bucket "ownership-runtime-bucket" exists
    When bucket ownership controls are set directly for "ownership-runtime-bucket" to "BucketOwnerEnforced"
    Then the response status is 200
    When bucket ownership controls are set directly for "ownership-runtime-bucket" to "ObjectWriter"
    Then the response status is 409

  Scenario: PublicAccessBlock blocks public object ACL mutations
    Given bucket "pab-acl-bucket" exists
    Given object "public.txt" exists in bucket "pab-acl-bucket" with content "public"
    When bucket public access block is configured for "pab-acl-bucket" with blockPublicAcls "true", ignorePublicAcls "false", blockPublicPolicy "false", restrictPublicBuckets "false"
    Then the response status is 200
    When object ACL "public-read" is applied to "public.txt" in bucket "pab-acl-bucket"
    Then the response status is 403

  Scenario: PublicAccessBlock blocks public bucket policies
    Given bucket "pab-policy-bucket" exists
    When bucket public access block is configured for "pab-policy-bucket" with blockPublicAcls "false", ignorePublicAcls "false", blockPublicPolicy "true", restrictPublicBuckets "false"
    Then the response status is 200
    When bucket policy is set for "pab-policy-bucket" to a public read policy
    Then the response status is 403
    And the response body contains "Public bucket policies are blocked"

  Scenario: PublicAccessBlock restricts anonymous access to public buckets
    Given bucket "pab-restrict-bucket" exists
    When bucket policy is set for "pab-restrict-bucket" to a public read policy
    Then the response status is 200
    When bucket public access block is configured for "pab-restrict-bucket" with blockPublicAcls "false", ignorePublicAcls "false", blockPublicPolicy "false", restrictPublicBuckets "true"
    Then the response status is 200
    When bucket "pab-restrict-bucket" is listed without authorization
    Then the response status is 403
    And the response body contains "Public bucket access is restricted"

  Scenario: Accelerate configuration adds runtime transfer acceleration headers
    Given bucket "accelerate-runtime-bucket" exists
    When bucket accelerate is configured for "accelerate-runtime-bucket" with status "Enabled"
    Then the response status is 200
    When bucket "accelerate-runtime-bucket" is listed without authorization
    Then the response status is 200
    And the response header "x-amz-transfer-acceleration" is "Enabled"

  Scenario: RequestPayment enforces requester pays access
    Given bucket "requester-pays-bucket" exists
    When bucket request payment is configured for "requester-pays-bucket" with payer "Requester"
    Then the response status is 200
    When bucket "requester-pays-bucket" is listed without authorization
    Then the response status is 403
    When bucket "requester-pays-bucket" is listed with requester pays header
    Then the response status is 200
